package dev.yuan.yuanguo.behavior.util;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;

/** Small helpers for reading optional behavior options from a CraftEngine section. */
public final class BehaviorConfig {

    private BehaviorConfig() {
    }

    public static String season(ConfigSection section) {
        String value = firstString(section, "season", "seasons", "grow_season");
        return value == null ? "ALL" : value.trim();
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