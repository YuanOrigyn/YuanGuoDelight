package dev.yuan.yuanguo.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable snapshot of the plugin configuration.
 */
public final class PluginSettings {

    private final boolean seasonEnabled;
    private final boolean autoDetect;
    private final boolean restrictGrowth;
    private final boolean notifySeasonChange;
    private final double inSeasonMultiplier;
    private final double outOfSeasonMultiplier;
    private final boolean debug;
    private final boolean autoInstallPack;
    private final boolean packOverwrite;
    private final boolean cauldron;
    private final boolean effectsEnabled;
    private final boolean effectBossBar;

    private PluginSettings(boolean seasonEnabled,
                           boolean autoDetect,
                           boolean restrictGrowth,
                           boolean notifySeasonChange,
                           double inSeasonMultiplier,
                           double outOfSeasonMultiplier,
                           boolean debug,
                           boolean autoInstallPack,
                           boolean packOverwrite,
                           boolean cauldron,
                           boolean effectsEnabled,
                           boolean effectBossBar) {
        this.seasonEnabled = seasonEnabled;
        this.autoDetect = autoDetect;
        this.restrictGrowth = restrictGrowth;
        this.notifySeasonChange = notifySeasonChange;
        this.inSeasonMultiplier = inSeasonMultiplier;
        this.outOfSeasonMultiplier = outOfSeasonMultiplier;
        this.debug = debug;
        this.autoInstallPack = autoInstallPack;
        this.packOverwrite = packOverwrite;
        this.cauldron = cauldron;
        this.effectsEnabled = effectsEnabled;
        this.effectBossBar = effectBossBar;
    }

    public static PluginSettings read(FileConfiguration config) {
        boolean autoDetect = config.getBoolean("season.auto-detect", true);
        boolean enabled = config.getBoolean("season.enabled", true);
        double inSeason = config.getDouble("season.growth.in-season-multiplier", 1.0D);
        double outOfSeason = config.getDouble("season.growth.out-of-season-multiplier", 0.25D);
        return new PluginSettings(
                enabled,
                autoDetect,
                config.getBoolean("season.growth.restrict-growth", true),
                config.getBoolean("season.notify-season-change", false),
                clampMultiplier(inSeason),
                clampMultiplier(outOfSeason),
                config.getBoolean("debug", false),
                config.getBoolean("pack.auto-install", true),
                config.getBoolean("pack.overwrite", false),
                config.getBoolean("pack.cauldron", true),
                config.getBoolean("effects.enabled", true),
                config.getBoolean("effects.boss-bar", true));
    }

    private static double clampMultiplier(double value) {
        if (Double.isNaN(value) || value < 0.0D) {
            return 0.0D;
        }
        return Math.min(value, 64.0D);
    }

    public boolean seasonEnabled() {
        return this.seasonEnabled;
    }

    public boolean autoDetect() {
        return this.autoDetect;
    }

    public boolean restrictGrowth() {
        return this.restrictGrowth;
    }

    public boolean notifySeasonChange() {
        return this.notifySeasonChange;
    }

    public double inSeasonMultiplier() {
        return this.inSeasonMultiplier;
    }

    public double outOfSeasonMultiplier() {
        return this.outOfSeasonMultiplier;
    }

    public boolean debug() {
        return this.debug;
    }

    public boolean autoInstallPack() {
        return this.autoInstallPack;
    }

    public boolean packOverwrite() {
        return this.packOverwrite;
    }

    /** Whether the jelly cauldron chain is active. */
    public boolean cauldron() {
        return this.cauldron;
    }

    /** Whether the food status effects are granted at all. */
    public boolean effectsEnabled() {
        return this.effectsEnabled;
    }

    /** Whether a custom effect also gets a countdown boss bar. */
    public boolean effectBossBar() {
        return this.effectBossBar;
    }
}