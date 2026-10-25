package dev.yuan.yuanguo.config;

import dev.yuan.yuanguo.YuanGuoDelight;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
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
            try {
                configuration.save(new java.io.File(this.plugin.getDataFolder(), "config.yml"));
            } catch (IOException ignored) {
            }
        } catch (Exception ignored) {
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