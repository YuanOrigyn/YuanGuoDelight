package dev.yuan.yuanguo.effect;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the remaining time of the plugin's own effects on a boss bar.
 *
 * <p>Minecraft has no way to add a potion type, so a custom effect has no icon and no built in
 * timer. The bar restores that feedback: one bar per effect, with the same name, level and colour
 * the mod uses, counting down exactly like a potion would.</p>
 */
final class EffectTimerBar {

    private final EffectRegistry registry;
    private final boolean enabled;
    private final Map<UUID, Map<CustomEffect, BossBar>> bars = new HashMap<>();

    EffectTimerBar(EffectRegistry registry, boolean enabled) {
        this.registry = registry;
        this.enabled = enabled;
    }

    /** Adds, refreshes or removes the bar of every effect the player carries. */
    void update(Player player, Map<CustomEffect, EffectState.Active> active) {
        Map<CustomEffect, BossBar> owned = this.bars.computeIfAbsent(player.getUniqueId(),
                key -> new EnumMap<>(CustomEffect.class));
        for (CustomEffect effect : CustomEffect.values()) {
            EffectState.Active value = active.get(effect);
            EffectRegistry.Presentation presentation = this.registry.presentation(effect);
            BossBar bar = owned.get(effect);
            if (value == null || !presentation.bar() || !this.enabled) {
                if (bar != null) {
                    player.hideBossBar(bar);
                    owned.remove(effect);
                }
                continue;
            }
            if (bar == null) {
                bar = BossBar.bossBar(title(effect, value), progress(value),
                        presentation.color(), presentation.style());
                owned.put(effect, bar);
                player.showBossBar(bar);
                continue;
            }
            bar.name(title(effect, value));
            bar.progress(progress(value));
        }
        if (owned.isEmpty()) {
            this.bars.remove(player.getUniqueId());
        }
    }

    void remove(Player player, CustomEffect effect) {
        Map<CustomEffect, BossBar> owned = this.bars.get(player.getUniqueId());
        if (owned == null) {
            return;
        }
        BossBar bar = owned.remove(effect);
        if (bar != null) {
            player.hideBossBar(bar);
        }
        if (owned.isEmpty()) {
            this.bars.remove(player.getUniqueId());
        }
    }

    void removeAll(Player player) {
        Map<CustomEffect, BossBar> owned = this.bars.remove(player.getUniqueId());
        if (owned == null) {
            return;
        }
        for (BossBar bar : owned.values()) {
            player.hideBossBar(bar);
        }
    }

    private static float progress(EffectState.Active active) {
        int total = Math.max(1, active.totalTicks());
        double ratio = (double) active.remainingTicks() / total;
        return (float) Math.max(0.0D, Math.min(1.0D, ratio));
    }

    private static Component title(CustomEffect effect, EffectState.Active active) {
        Component title = Component.translatable(effect.nameKey());
        int amplifier = active.amplifier();
        if (amplifier > 0) {
            String key = amplifier <= 5
                    ? "potion.potency." + amplifier
                    : "enchantment.level." + (amplifier + 1);
            title = title.append(Component.text(" ")).append(Component.translatable(key));
        }
        return title.append(Component.text(" " + format(active.remainingTicks())));
    }

    private static String format(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int rest = seconds % 60;
        return (seconds / 60) + ":" + (rest < 10 ? "0" : "") + rest;
    }
}