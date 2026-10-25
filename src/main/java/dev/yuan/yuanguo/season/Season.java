package dev.yuan.yuanguo.season;

import java.util.Locale;

/**
 * The four RealisticSeasons seasons plus a wildcard used by content that grows all year round.
 */
public enum Season {
    SPRING,
    SUMMER,
    FALL,
    WINTER,
    ALL;

    public static Season parse(String raw) {
        if (raw == null) {
            return ALL;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("NONE") || value.equals("ANY") || value.equals("DISABLED")) {
            return ALL;
        }
        if (value.equals("AUTUMN")) {
            return FALL;
        }
        for (Season season : values()) {
            if (season.name().equals(value)) {
                return season;
            }
        }
        return ALL;
    }

    public boolean matches(Season current) {
        return this == ALL || current == null || this == current;
    }
}