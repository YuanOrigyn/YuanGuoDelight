package dev.yuan.yuanguo.behavior.util;

import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Small helpers for reading optional behavior options from a CraftEngine section. */
public final class BehaviorConfig {

    /** Values {@link dev.yuan.yuanguo.season.Season#parse(String)} understands. */
    private static final Set<String> KNOWN_SEASONS = Set.of(
            "SPRING", "SUMMER", "FALL", "AUTUMN", "WINTER", "ALL", "NONE", "ANY", "DISABLED");
    private static final Set<String> WARNED_SEASONS = ConcurrentHashMap.newKeySet();

    private BehaviorConfig() {
    }

    public static String season(ConfigSection section) {
        String value = firstString(section, "season", "seasons", "grow_season");
        if (value == null) {
            return "ALL";
        }
        String trimmed = value.trim();
        String normalised = trimmed.toUpperCase(Locale.ROOT);
        // A typo used to turn into "grow all year round" silently, which is very hard to notice in
        // game. Warn once per distinct value instead.
        if (!KNOWN_SEASONS.contains(normalised) && WARNED_SEASONS.add(normalised)) {
            try {
                CraftEngine.instance().logger().warn("Unknown season '" + trimmed
                        + "' on a block behavior; it will grow all year round."
                        + " Valid values: SPRING, SUMMER, FALL, WINTER, ALL");
            } catch (Throwable ignored) {
                // CraftEngine is not ready; the fallback below still applies.
            }
        }
        return trimmed;
    }

    public static double optionalDouble(ConfigSection section, double fallback, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                return section.getDouble(key, fallback);
            }
        }
        return fallback;
    }

    public static int intValue(ConfigSection section, int fallback, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                return section.getInt(key, fallback);
            }
        }
        return fallback;
    }

    public static float floatValue(ConfigSection section, float fallback, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                return section.getFloat(key, fallback);
            }
        }
        return fallback;
    }

    public static double doubleValue(ConfigSection section, double fallback, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                return section.getDouble(key, fallback);
            }
        }
        return fallback;
    }

    public static boolean bool(ConfigSection section, boolean fallback, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                return section.getBoolean(key, fallback);
            }
        }
        return fallback;
    }

    public static String string(ConfigSection section, String fallback, String... keys) {
        String value = firstString(section, keys);
        return value == null ? fallback : value;
    }

    private static String firstString(ConfigSection section, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
    public static net.momirealms.craftengine.core.util.Key optionalKey(ConfigSection section, String... keys) {
        for (String key : keys) {
            if (section.containsKey(key)) {
                net.momirealms.craftengine.core.util.Key value = section.getKey(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }
}