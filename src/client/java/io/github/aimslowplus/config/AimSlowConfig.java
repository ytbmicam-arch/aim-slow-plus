package io.github.aimslowplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Persistent settings of the mod, stored in {@code config/aimslowplus.json}.
 * Every value read from disk is validated and clamped, so a hand-edited or corrupted
 * file can never crash the game or produce an unusable mouse.
 */
public final class AimSlowConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AimSlowPlus/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "aimslowplus.json";

    // ---- Allowed ranges (also used by the config screen) ----
    public static final double BUBBLE_MIN = 1.0, BUBBLE_MAX = 45.0;
    public static final double MIN_MULT_MIN = 0.05, MIN_MULT_MAX = 1.0;
    public static final double RANGE_MIN = 4.0, RANGE_MAX = 64.0;

    // ---- Defaults ----
    public static final boolean DEF_ENABLED = true;
    public static final double DEF_STRENGTH = 0.75;
    public static final double DEF_BUBBLE = 10.0;
    public static final double DEF_MIN_MULT = 0.20;
    public static final double DEF_RANGE = 24.0;
    public static final double DEF_ACTIVATION = 0.25;
    public static final double DEF_RELEASE = 0.35;
    public static final boolean DEF_PLAYERS_ONLY = false;
    public static final boolean DEF_IGNORE_TEAMMATES = true;
    public static final boolean DEF_REQUIRE_LOS = true;

    // ---- Settings ----
    /** Master toggle. */
    public boolean enabled = DEF_ENABLED;
    /** 0.0 - 1.0: how much sensitivity is removed at the very center of the bubble. */
    public double slowdownStrength = DEF_STRENGTH;
    /** Radius (degrees) around the crosshair in which an entity triggers the slowdown. */
    public double bubbleAngle = DEF_BUBBLE;
    /** Floor for the sensitivity multiplier (the mouse never drops below this). */
    public double minSensitivityMultiplier = DEF_MIN_MULT;
    /** Max distance (blocks) from the eyes to the entity hitbox. */
    public double maxRange = DEF_RANGE;
    /** 0.0 (instant) - 1.0 (slowest): how fast the slowdown engages. */
    public double activationSmoothness = DEF_ACTIVATION;
    /** 0.0 (instant) - 1.0 (slowest): how fast normal sensitivity comes back. */
    public double releaseSmoothness = DEF_RELEASE;
    public boolean playersOnly = DEF_PLAYERS_ONLY;
    public boolean ignoreTeammates = DEF_IGNORE_TEAMMATES;
    public boolean requireLineOfSight = DEF_REQUIRE_LOS;

    private static AimSlowConfig instance = new AimSlowConfig();

    public static AimSlowConfig get() {
        return instance;
    }

    public void resetToDefaults() {
        enabled = DEF_ENABLED;
        slowdownStrength = DEF_STRENGTH;
        bubbleAngle = DEF_BUBBLE;
        minSensitivityMultiplier = DEF_MIN_MULT;
        maxRange = DEF_RANGE;
        activationSmoothness = DEF_ACTIVATION;
        releaseSmoothness = DEF_RELEASE;
        playersOnly = DEF_PLAYERS_ONLY;
        ignoreTeammates = DEF_IGNORE_TEAMMATES;
        requireLineOfSight = DEF_REQUIRE_LOS;
    }

    // ------------------------------------------------------------------ I/O

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** Loads the config from disk (creating it with defaults if missing or unreadable). */
    public static synchronized void load() {
        Path file = path();
        AimSlowConfig loaded = new AimSlowConfig();
        boolean rewrite = false;

        if (Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement root = JsonParser.parseString(text);
                if (root != null && root.isJsonObject()) {
                    loaded.readFrom(root.getAsJsonObject());
                } else {
                    throw new IllegalStateException("root element is not a JSON object");
                }
            } catch (Exception e) {
                LOGGER.warn("Invalid config file, using defaults (a backup is kept next to it)", e);
                backupBrokenFile(file);
                loaded = new AimSlowConfig();
                rewrite = true;
            }
        } else {
            rewrite = true;
        }

        instance = loaded;
        if (rewrite) {
            save();
        }
    }

    /** Saves the current config to disk (atomic write). */
    public static synchronized void save() {
        Path file = path();
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, GSON.toJson(instance.toJson()), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save {}", file, e);
        }
    }

    private static void backupBrokenFile(Path file) {
        try {
            Files.move(file, file.resolveSibling(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Not critical.
        }
    }

    private JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("configVersion", 1);
        o.addProperty("enabled", enabled);
        o.addProperty("slowdownStrength", slowdownStrength);
        o.addProperty("bubbleAngle", bubbleAngle);
        o.addProperty("minSensitivityMultiplier", minSensitivityMultiplier);
        o.addProperty("maxRange", maxRange);
        o.addProperty("activationSmoothness", activationSmoothness);
        o.addProperty("releaseSmoothness", releaseSmoothness);
        o.addProperty("playersOnly", playersOnly);
        o.addProperty("ignoreTeammates", ignoreTeammates);
        o.addProperty("requireLineOfSight", requireLineOfSight);
        return o;
    }

    private void readFrom(JsonObject o) {
        enabled = readBoolean(o, "enabled", DEF_ENABLED);
        slowdownStrength = readDouble(o, "slowdownStrength", DEF_STRENGTH, 0.0, 1.0);
        bubbleAngle = readDouble(o, "bubbleAngle", DEF_BUBBLE, BUBBLE_MIN, BUBBLE_MAX);
        minSensitivityMultiplier = readDouble(o, "minSensitivityMultiplier", DEF_MIN_MULT, MIN_MULT_MIN, MIN_MULT_MAX);
        maxRange = readDouble(o, "maxRange", DEF_RANGE, RANGE_MIN, RANGE_MAX);
        activationSmoothness = readDouble(o, "activationSmoothness", DEF_ACTIVATION, 0.0, 1.0);
        releaseSmoothness = readDouble(o, "releaseSmoothness", DEF_RELEASE, 0.0, 1.0);
        playersOnly = readBoolean(o, "playersOnly", DEF_PLAYERS_ONLY);
        ignoreTeammates = readBoolean(o, "ignoreTeammates", DEF_IGNORE_TEAMMATES);
        requireLineOfSight = readBoolean(o, "requireLineOfSight", DEF_REQUIRE_LOS);
    }

    private static double readDouble(JsonObject o, String key, double def, double min, double max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || e.getAsJsonPrimitive().isBoolean()) {
            return def;
        }
        try {
            double v = e.getAsDouble();
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return def;
            }
            return Math.max(min, Math.min(max, v));
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static boolean readBoolean(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        if (e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        if (e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString().trim();
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
        }
        return def;
    }
}
