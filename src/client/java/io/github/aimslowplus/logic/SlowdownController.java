package io.github.aimslowplus.logic;

import io.github.aimslowplus.config.AimSlowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes the mouse-sensitivity multiplier.
 *
 * <p>IMPORTANT: this class only returns a number in [minMultiplier, 1]. It never touches the
 * player's yaw/pitch and never moves the crosshair: it is a slowdown, not an aim assist.
 */
public final class SlowdownController {
    private static final Logger LOGGER = LoggerFactory.getLogger("AimSlowPlus");

    /** Time constant (seconds) used when smoothness is at 100%. */
    private static final double MAX_TIME_CONSTANT = 0.40;
    /** Below this difference the multiplier snaps to its target (prevents an endless tail). */
    private static final double SNAP_EPSILON = 0.002;
    private static final double NONE = Double.MAX_VALUE;

    private static double current = 1.0;
    private static long lastNanos = 0L;
    private static boolean errorLogged = false;

    private SlowdownController() {
    }

    /** Immediately returns to normal sensitivity and forgets all state. */
    public static void reset() {
        current = 1.0;
        lastNanos = 0L;
    }

    /**
     * Called once per frame, right before the accumulated mouse movement is applied to the camera.
     *
     * @return multiplier to apply to the raw mouse movement (1.0 = untouched)
     */
    public static double update(Minecraft mc) {
        AimSlowConfig cfg = AimSlowConfig.get();
        if (!cfg.enabled || mc.player == null || mc.level == null || mc.screen != null) {
            reset();
            return 1.0;
        }

        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.0 : Math.min(Math.max((now - lastNanos) / 1.0e9, 0.0), 0.1);
        lastNanos = now;

        double target;
        try {
            target = computeTarget(mc, cfg);
        } catch (RuntimeException e) {
            // Never let a bug here break the player's mouse.
            if (!errorLogged) {
                errorLogged = true;
                LOGGER.error("Aim Slow Plus failed to compute the slowdown, falling back to normal sensitivity", e);
            }
            target = 1.0;
        }

        // Slowing down uses the activation smoothness, speeding back up uses the release smoothness.
        double smoothness = target < current ? cfg.activationSmoothness : cfg.releaseSmoothness;
        double tau = smoothness * MAX_TIME_CONSTANT;
        if (tau < 1.0e-3) {
            current = target;
        } else {
            // Frame-rate independent exponential smoothing.
            current += (target - current) * (1.0 - Math.exp(-dt / tau));
        }
        if (Math.abs(target - current) < SNAP_EPSILON) {
            current = target;
        }
        current = Math.max(cfg.minSensitivityMultiplier, Math.min(1.0, current));
        return current;
    }

    // --------------------------------------------------------------- targeting

    private static double computeTarget(Minecraft mc, AimSlowConfig cfg) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        Vec3 eye = player.getEyePosition();
        Vec3 look = lookVector(player);
        double bubble = cfg.bubbleAngle;
        double range = cfg.maxRange;
        double reach = range + 4.0; // generous margin for large hitboxes

        Entity best = null;
        double bestAngle = bubble; // only angles strictly below the bubble radius qualify

        for (Entity entity : level.entitiesForRendering()) {
            if (!isEligible(entity, player, cfg)) {
                continue;
            }
            AABB box = entity.getBoundingBox();

            // Cheap distance rejection before doing any trigonometry.
            double dx = (box.minX + box.maxX) * 0.5 - eye.x;
            double dy = (box.minY + box.maxY) * 0.5 - eye.y;
            double dz = (box.minZ + box.maxZ) * 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                continue;
            }

            double angle = angularDistanceDegrees(eye, look, box, range);
            if (angle >= bestAngle) {
                continue; // outside the bubble, or not better than the current best
            }
            if (cfg.requireLineOfSight && !hasLineOfSight(level, player, eye, box)) {
                continue;
            }
            bestAngle = angle;
            best = entity;
        }

        if (best == null) {
            return 1.0;
        }

        // 0 at the bubble edge -> 1 at the crosshair, eased so the transition has no kink.
        double proximity = 1.0 - bestAngle / bubble;
        double eased = proximity * proximity * (3.0 - 2.0 * proximity);
        double target = 1.0 - cfg.slowdownStrength * eased;
        return Math.max(cfg.minSensitivityMultiplier, Math.min(1.0, target));
    }

    private static boolean isEligible(Entity e, LocalPlayer player, AimSlowConfig cfg) {
        if (e == player || e.isRemoved() || !e.isAlive() || e.isSpectator() || e.isInvisible() || !e.isPickable()) {
            return false;
        }
        if (cfg.playersOnly) {
            if (!(e instanceof Player)) {
                return false;
            }
        } else if (!(e instanceof LivingEntity)) {
            return false;
        }
        if (e == player.getVehicle()) {
            return false;
        }
        // Scoreboard teams are synced to the client, so this is reliable whenever teams are used.
        return !(cfg.ignoreTeammates && player.isAlliedTo(e));
    }

    /** Direction the player is looking at, computed from the current yaw/pitch (read-only). */
    private static Vec3 lookVector(LocalPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        double cp = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    /**
     * Angle (degrees) between the look direction and the closest point of the hitbox, or
     * {@link #NONE} if the box is farther than {@code range} or contains the eyes.
     * An angle of 0 means the crosshair is on the hitbox.
     */
    private static double angularDistanceDegrees(Vec3 eye, Vec3 look, AABB box, double range) {
        double cx = (box.minX + box.maxX) * 0.5 - eye.x;
        double cy = (box.minY + box.maxY) * 0.5 - eye.y;
        double cz = (box.minZ + box.maxZ) * 0.5 - eye.z;

        // Point of the view ray at the depth of the box center...
        double t = Math.max(0.0, cx * look.x + cy * look.y + cz * look.z);
        double px = eye.x + look.x * t;
        double py = eye.y + look.y * t;
        double pz = eye.z + look.z * t;

        // ...then the point of the hitbox closest to it.
        double qx = clamp(px, box.minX, box.maxX);
        double qy = clamp(py, box.minY, box.maxY);
        double qz = clamp(pz, box.minZ, box.maxZ);

        double vx = qx - eye.x;
        double vy = qy - eye.y;
        double vz = qz - eye.z;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1.0e-4 || len > range) {
            return NONE;
        }
        double cos = (vx * look.x + vy * look.y + vz * look.z) / len;
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    /** True if at least one of two sample points of the hitbox is not hidden behind a block. */
    private static boolean hasLineOfSight(ClientLevel level, LocalPlayer player, Vec3 eye, AABB box) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        // Upper body (roughly where the head / eyes are) and center.
        Vec3 high = new Vec3(cx, box.minY + (box.maxY - box.minY) * 0.85, cz);
        Vec3 center = new Vec3(cx, cy, cz);
        return isUnobstructed(level, player, eye, high) || isUnobstructed(level, player, eye, center);
    }

    private static boolean isUnobstructed(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }
}
