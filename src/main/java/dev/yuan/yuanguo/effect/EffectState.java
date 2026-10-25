package dev.yuan.yuanguo.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The custom effects a player is currently carrying.
 *
 * <p>Vanilla potion effects expire on their own; these do not, so the remaining ticks are counted by
 * the tick loop and the values are written into the player's persistent data. That way a restart or
 * a relog keeps the effect, which is what the mod does with its synced effect data.</p>
 */
public final class EffectState {

    private static final String KEY_NAMESPACE = "yuanguo";
    private static final String KEY_PREFIX = "effect_";

    /** A single active custom effect. */
    public static final class Active {
        private int remainingTicks;
        private final int totalTicks;
        private final int amplifier;

        Active(int remainingTicks, int totalTicks, int amplifier) {
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
            this.amplifier = amplifier;
        }

        public int remainingTicks() {
            return this.remainingTicks;
        }

        /** The duration the effect was granted with, for the timer bar progress. */
        public int totalTicks() {
            return this.totalTicks;
        }

        public int amplifier() {
            return this.amplifier;
        }
    }

    private final Plugin plugin;
    private final Map<UUID, Map<CustomEffect, Active>> active = new LinkedHashMap<>();

    public EffectState(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Grants (or refreshes) a custom effect. */
    public void apply(Player player, CustomEffect effect, int durationTicks, int amplifier) {
        Map<CustomEffect, Active> effects =
                this.active.computeIfAbsent(player.getUniqueId(), key -> new EnumMap<>(CustomEffect.class));
        Active current = effects.get(effect);
        // The longer duration wins and the higher level wins, so eating a second fruit never
        // shortens what the first one granted.
        int remaining = current == null ? durationTicks : Math.max(current.remainingTicks, durationTicks);
        int total = current == null ? durationTicks : Math.max(current.totalTicks, durationTicks);
        int level = current == null ? amplifier : Math.max(current.amplifier, amplifier);
        effects.put(effect, new Active(remaining, total, level));
    }

    public boolean has(Player player, CustomEffect effect) {
        Map<CustomEffect, Active> effects = this.active.get(player.getUniqueId());
        return effects != null && effects.containsKey(effect);
    }

    @Nullable
    public Active get(Player player, CustomEffect effect) {
        Map<CustomEffect, Active> effects = this.active.get(player.getUniqueId());
        return effects == null ? null : effects.get(effect);
    }

    public Map<CustomEffect, Active> of(Player player) {
        return this.active.getOrDefault(player.getUniqueId(), Map.of());
    }

    /** Drops every custom effect, for example on death. */
    public void clear(Player player) {
        this.active.remove(player.getUniqueId());
        PersistentDataContainer container = player.getPersistentDataContainer();
        for (CustomEffect effect : CustomEffect.values()) {
            container.remove(key(effect));
        }
    }

    public void forget(UUID playerId) {
        this.active.remove(playerId);
    }

    /**
     * Advances every effect by {@code ticks} and drops the ones that ran out.
     *
     * @return the effects that just expired
     */
    public Map<CustomEffect, Active> tick(Player player, int ticks) {
        Map<CustomEffect, Active> effects = this.active.get(player.getUniqueId());
        if (effects == null || effects.isEmpty()) {
            return Map.of();
        }
        Map<CustomEffect, Active> expired = new EnumMap<>(CustomEffect.class);
        var iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<CustomEffect, Active> entry = iterator.next();
            Active value = entry.getValue();
            value.remainingTicks -= ticks;
            if (value.remainingTicks <= 0) {
                expired.put(entry.getKey(), value);
                iterator.remove();
            }
        }
        if (effects.isEmpty()) {
            this.active.remove(player.getUniqueId());
        }
        return expired;
    }

    /** Writes the current effects into the player's persistent data. */
    public void persist(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        Map<CustomEffect, Active> effects = this.active.get(player.getUniqueId());
        for (CustomEffect effect : CustomEffect.values()) {
            Active value = effects == null ? null : effects.get(effect);
            if (value == null) {
                container.remove(key(effect));
            } else {
                container.set(key(effect), PersistentDataType.STRING,
                        value.remainingTicks + ":" + value.amplifier);
            }
        }
    }

    /** Reads back what {@link #persist(Player)} wrote. */
    public void restore(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        Map<CustomEffect, Active> effects = new EnumMap<>(CustomEffect.class);
        for (CustomEffect effect : CustomEffect.values()) {
            String raw = container.get(key(effect), PersistentDataType.STRING);
            if (raw == null) {
                continue;
            }
            int colon = raw.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                int remaining = Integer.parseInt(raw.substring(0, colon));
                int amplifier = Integer.parseInt(raw.substring(colon + 1));
                if (remaining > 0) {
                    effects.put(effect, new Active(remaining, remaining, amplifier));
                }
            } catch (NumberFormatException ignored) {
                // A corrupt entry is simply dropped.
            }
        }
        if (effects.isEmpty()) {
            this.active.remove(player.getUniqueId());
        } else {
            this.active.put(player.getUniqueId(), effects);
        }
    }

    private NamespacedKey key(CustomEffect effect) {
        return new NamespacedKey(KEY_NAMESPACE, KEY_PREFIX + effect.id());
    }

    /** The plugin the keys belong to, used by {@code /yuanguo} to report state. */
    public Plugin plugin() {
        return this.plugin;
    }
}