package dev.yuan.yuanguo.effect;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * One status effect a food applies when it is eaten.
 *
 * @param id        the namespaced id as written in {@code effects.yml}, for example
 *                  {@code minecraft:speed} or {@code yuanguo:digesting}
 * @param type      the vanilla effect backing a {@code minecraft:} entry, or {@code null} when the
 *                  entry is one of this plugin's own effects
 * @param custom    the custom effect a {@code yuanguo:} entry maps to, or {@code null}
 * @param duration  how long the effect lasts, in ticks
 * @param amplifier the effect level, zero based
 * @param chance    the probability the effect is applied, between 0 and 1
 */
public record FoodEffect(String id,
                         @Nullable PotionEffectType type,
                         @Nullable CustomEffect custom,
                         int duration,
                         int amplifier,
                         double chance) {

    /** Whether this effect can be handed to Bukkit directly. */
    public boolean isVanilla() {
        return this.type != null;
    }
}