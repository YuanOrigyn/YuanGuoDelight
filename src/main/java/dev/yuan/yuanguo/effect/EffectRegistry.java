package dev.yuan.yuanguo.effect;

import dev.yuan.yuanguo.YuanGuoDelight;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * The table of effects a food applies, read from the bundled {@code effects.yml}.
 *
 * <p>The file is generated from the mod alongside the CraftEngine pack, so the plugin and the pack
 * cannot drift apart: one build writes both. The same file carries the presentation of the plugin's
 * own effects, so an administrator can retune a colour or turn a single effect off without touching
 * code.</p>
 */
public final class EffectRegistry {

    /** How one of the plugin's own effects is presented. */
    public record Presentation(boolean enabled, boolean bar, BossBar.Color color, BossBar.Overlay style) {
    }

    private final Map<String, List<FoodEffect>> byItem = new LinkedHashMap<>();
    private final Map<CustomEffect, Presentation> presentations = new EnumMap<>(CustomEffect.class);
    private final Map<CustomEffect, Integer> ranges = new EnumMap<>(CustomEffect.class);

    /** @return the effects for a {@code yuanguo:} item id, or an empty list. */
    public List<FoodEffect> effectsOf(String itemId) {
        return this.byItem.getOrDefault(itemId, List.of());
    }

    public Map<String, List<FoodEffect>> all() {
        return Collections.unmodifiableMap(this.byItem);
    }

    public Presentation presentation(CustomEffect effect) {
        return this.presentations.getOrDefault(effect,
                new Presentation(true, true, color(effect.barColor(), BossBar.Color.WHITE),
                        BossBar.Overlay.NOTCHED_20));
    }

    /** Whether the effect is active at all. A disabled effect is never granted. */
    public boolean enabled(CustomEffect effect) {
        return presentation(effect).enabled();
    }

    public int range(CustomEffect effect, int fallback) {
        return this.ranges.getOrDefault(effect, fallback);
    }

    /** Reads {@code effects.yml} from the plugin jar. */
    public static EffectRegistry load(YuanGuoDelight plugin) {
        EffectRegistry registry = new EffectRegistry();
        registry.defaults();
        try (InputStream stream = plugin.getResource("effects.yml")) {
            if (stream == null) {
                plugin.getLogger().warning("effects.yml is missing from the jar; no food will grant effects.");
                return registry;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            registry.read(yaml, plugin);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not read effects.yml", exception);
        }
        registry.applyConfig(plugin);
        return registry;
    }

    /**
     * Applies the {@code effects.disabled} and {@code effects.bar-disabled} lists from
     * {@code config.yml}, so an administrator can switch a single effect off without rebuilding the
     * content pack.
     */
    private void applyConfig(YuanGuoDelight plugin) {
        for (String name : plugin.getConfig().getStringList("effects.disabled")) {
            CustomEffect effect = CustomEffect.byId(name.trim());
            if (effect == null) {
                plugin.getLogger().warning("Unknown effect '" + name + "' in effects.disabled");
                continue;
            }
            Presentation current = presentation(effect);
            this.presentations.put(effect, new Presentation(false, current.bar(), current.color(), current.style()));
        }
        for (String name : plugin.getConfig().getStringList("effects.bar-disabled")) {
            CustomEffect effect = CustomEffect.byId(name.trim());
            if (effect == null) {
                plugin.getLogger().warning("Unknown effect '" + name + "' in effects.bar-disabled");
                continue;
            }
            Presentation current = presentation(effect);
            this.presentations.put(effect, new Presentation(current.enabled(), false, current.color(), current.style()));
        }
    }

    private void defaults() {
        for (CustomEffect effect : CustomEffect.values()) {
            this.presentations.put(effect,
                    new Presentation(true, true, color(effect.barColor(), BossBar.Color.WHITE),
                            BossBar.Overlay.NOTCHED_20));
        }
        this.ranges.put(CustomEffect.RAGE_AURA, 24);
        this.ranges.put(CustomEffect.HEAL_AURA, 6);
        this.ranges.put(CustomEffect.ALIENATING, 12);
    }

    private void read(YamlConfiguration yaml, YuanGuoDelight plugin) {
        ConfigurationSection ranges = yaml.getConfigurationSection("aura-ranges");
        if (ranges != null) {
            for (String key : ranges.getKeys(false)) {
                CustomEffect effect = CustomEffect.byId(key);
                if (effect != null) {
                    this.ranges.put(effect, Math.max(1, ranges.getInt(key, this.ranges.getOrDefault(effect, 8))));
                }
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("effects");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            List<FoodEffect> effects = new ArrayList<>();
            for (Map<?, ?> entry : entriesOf(section.get(key))) {
                FoodEffect effect = parse(entry, plugin);
                if (effect != null) {
                    effects.add(effect);
                }
            }
            if (!effects.isEmpty()) {
                // Lookups go through CraftEngine's Key#value(), which drops the namespace, so the
                // table is keyed the same way. Without this every food silently grants nothing.
                int colon = key.indexOf(':');
                String value = colon < 0 ? key : key.substring(colon + 1);
                this.byItem.put(value, List.copyOf(effects));
            }
        }
    }

    private static BossBar.Color color(@Nullable String name, BossBar.Color fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static BossBar.Overlay style(@Nullable String name, BossBar.Overlay fallback) {
        if (name == null) {
            return fallback;
        }
        String normalised = name.toUpperCase(Locale.ROOT);
        if (normalised.equals("SOLID") || normalised.equals("PROGRESS")) {
            return BossBar.Overlay.PROGRESS;
        }
        if (normalised.equals("SEGMENTED_6")) {
            normalised = "NOTCHED_6";
        } else if (normalised.equals("SEGMENTED_10")) {
            normalised = "NOTCHED_10";
        } else if (normalised.equals("SEGMENTED_12")) {
            normalised = "NOTCHED_12";
        } else if (normalised.equals("SEGMENTED_20")) {
            normalised = "NOTCHED_20";
        }
        try {
            return BossBar.Overlay.valueOf(normalised);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    /** The item key holds a plain sequence of effect maps. */
    private static List<Map<?, ?>> entriesOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<?, ?>> entries = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> map) {
                entries.add(map);
            }
        }
        return entries;
    }

    @Nullable
    private static FoodEffect parse(Map<?, ?> entry, YuanGuoDelight plugin) {
        Object idValue = entry.get("effect");
        if (idValue == null) {
            return null;
        }
        String id = String.valueOf(idValue);
        int duration = number(entry.get("duration"), 0);
        int amplifier = number(entry.get("amplifier"), 0);
        double chance = number(entry.get("chance"), 1.0D);
        if (duration <= 0) {
            return null;
        }
        chance = Math.max(0.0D, Math.min(1.0D, chance));

        if (id.startsWith("yuanguo:")) {
            CustomEffect custom = CustomEffect.byId(id);
            if (custom == null) {
                plugin.getLogger().warning("Unknown custom effect '" + id + "' in effects.yml");
                return null;
            }
            return new FoodEffect(id, null, custom, duration, amplifier, chance);
        }
        PotionEffectType type = resolve(id);
        if (type == null) {
            plugin.getLogger().warning("Unknown potion effect '" + id + "' in effects.yml");
            return null;
        }
        return new FoodEffect(id, type, null, duration, amplifier, chance);
    }

    @Nullable
    private static PotionEffectType resolve(String id) {
        NamespacedKey key = NamespacedKey.fromString(id);
        if (key != null) {
            PotionEffectType byKey = PotionEffectType.getByKey(key);
            if (byKey != null) {
                return byKey;
            }
        }
        return PotionEffectType.getByName(id);
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}