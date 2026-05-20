package com.culltag;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class CullTagConfig {

    public static volatile boolean enabled            = true;
    public static volatile int maxDistance        = 32;
    public static volatile int checkIntervalTicks = 10;
    public static volatile boolean crouchHidesNametag = true;

    private static final Path CONFIG_PATH = Path.of("config", "culltag.properties");

    private CullTagConfig() {}

    public static void load(Logger logger) {
        Properties props = new Properties();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                logger.error("[CullTag] Failed to read config, using defaults", e);
            }
        }

        enabled            = parseBoolean(props, "enabled",              true,       logger);
        maxDistance        = parseInt(props,    "max_distance",         32, 1, 512, logger);
        checkIntervalTicks = parseInt(props,    "check_interval_ticks", 10, 1,  40, logger);
        crouchHidesNametag = parseBoolean(props, "crouch_hides_nametag", true,       logger);

        save(logger);
        logger.info("[CullTag] Config loaded: enabled={}, max_distance={}, check_interval_ticks={}, crouch_hides_nametag={}",
                enabled, maxDistance, checkIntervalTicks, crouchHidesNametag);
    }

    public static void reload(Logger logger) {
        load(logger);
    }

    private static boolean parseBoolean(Properties props, String key, boolean def, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        String s = raw.trim().toLowerCase();
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        logger.warn("[CullTag] '{}' is not a valid boolean ('{}'), using default {}", key, raw, def);
        return def;
    }

    private static int parseInt(Properties props, String key, int def, int min, int max, Logger logger) {
        String raw = props.getProperty(key);
        if (raw == null) return def;
        try {
            int val = Integer.parseInt(raw.trim());
            if (val < min || val > max) {
                logger.warn("[CullTag] '{}' value {} out of range [{}, {}], clamping", key, val, min, max);
                return Math.max(min, Math.min(max, val));
            }
            return val;
        } catch (NumberFormatException e) {
            logger.warn("[CullTag] '{}' is not a valid integer ('{}'), using default {}", key, raw, def);
            return def;
        }
    }

    public static void save(Logger logger) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, toPropertiesString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("[CullTag] Failed to save config", e);
        }
    }

    private static String toPropertiesString() {
        return """
                # CullTag configuration
                # Changes take effect after /culltag reload or server restart.

                # Enable or disable nametag culling entirely.
                enabled=%b

                # Maximum distance in blocks at which line-of-sight checks are performed.
                # Players beyond this distance are treated as visible (vanilla behaviour).
                max_distance=%d

                # How many server ticks between LOS sweeps.
                # Lower values are more accurate but use more CPU.
                # 10 = ~2 checks per second, 4 = ~5 checks per second.
                check_interval_ticks=%d

                # When true, a crouching player's nametag is hidden from everyone
                # entirely, regardless of line of sight.
                crouch_hides_nametag=%b
                """.formatted(enabled, maxDistance, checkIntervalTicks, crouchHidesNametag);
    }
}
