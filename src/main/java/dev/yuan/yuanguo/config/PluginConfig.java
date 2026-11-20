package dev.yuan.yuanguo.config;

import dev.yuan.yuanguo.YuanGuoDelight;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads {@code config.yml}, creating it from the bundled defaults on first run.
 */
public final class PluginConfig {

    private final YuanGuoDelight plugin;
    private volatile PluginSettings settings;

    public PluginConfig(YuanGuoDelight plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        FileConfiguration configuration = this.plugin.getConfig();
        applyBundledDefaults(configuration);
        this.settings = PluginSettings.read(configuration);
    }

    /**
     * Bukkit's {@code saveDefaultConfig} keeps the previously generated file, so new options added by an
     * update would silently stay missing. Re-applying the packaged defaults fills those gaps in memory
     * without overwriting values the administrator already edited.
     *
     * <p>The file is deliberately not written back: Bukkit's YAML writer does not preserve comments,
     * so saving here would strip every explanatory comment out of the administrator's config.yml on
     * the next reload.</p>
     */
    private void applyBundledDefaults(FileConfiguration configuration) {
        try (InputStream stream = this.plugin.getResource("config.yml")) {
            if (stream == null) {
                return;
            }
            FileConfiguration defaults = new org.bukkit.configuration.file.YamlConfiguration();
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
        } catch (Exception exception) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not merge the bundled config.yml defaults; missing options fall back to their code defaults",
                    exception);
        }
    }

    public PluginSettings settings() {
        PluginSettings current = this.settings;
        if (current == null) {
            throw new IllegalStateException("Configuration has not been loaded yet");
        }
        return current;
    }
}