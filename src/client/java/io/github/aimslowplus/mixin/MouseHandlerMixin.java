package io.github.aimslowplus.mixin;

import io.github.aimslowplus.logic.SlowdownController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales the raw accumulated mouse movement just before vanilla converts it into camera rotation.
 * Nothing else is changed: no yaw/pitch is ever written by this mod, and the sensitivity option
 * itself is never modified (so there is nothing to "restore" if something goes wrong).
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void aimslowplus$applySlowdown(double timeDelta, CallbackInfo ci) {
        double multiplier = SlowdownController.update(Minecraft.getInstance());
        if (multiplier < 1.0) {
            this.accumulatedDX *= multiplier;
            this.accumulatedDY *= multiplier;
        }
    }
}
