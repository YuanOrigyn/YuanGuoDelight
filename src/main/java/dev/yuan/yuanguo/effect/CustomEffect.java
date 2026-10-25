package dev.yuan.yuanguo.effect;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The status effects the mod adds that vanilla Minecraft has no equivalent for.
 *
 * <p>CraftEngine cannot register new potion types, so these live entirely in this plugin: the player
 * carries them as plugin state rather than as a real potion effect. The names still come from the
 * bundled lang files, so the action bar and the command read the same as the mod.</p>
 *
 * <p>Each entry declares how it behaves. {@code harmful} effects are red on the timer bar, and
 * {@code barColor} mirrors the colour the mod gives the effect icon.</p>
 */
public enum CustomEffect {

    /** Sneak to fit through a one block gap. */
    SHRINKING("shrinking", false, "RED"),
    /** Eat and drink faster. */
    LOZENGE("lozenge", false, "GREEN"),
    /** Eat even when the hunger bar is full. */
    APPETIZING("appetizing", false, "RED"),
    /** Nearby mobs turn on the player. */
    RAGE_AURA("rage_aura", false, "YELLOW"),
    /** Heals the player and everything nearby. */
    HEAL_AURA("heal_aura", false, "PINK"),
    /** Immune to mining fatigue. */
    REFRESHING("refreshing", false, "YELLOW"),
    /** Immune to slowness. */
    SWEETENING("sweetening", false, "YELLOW"),
    /** Immune to blindness and darkness. */
    BRIGHTENING("brightening", false, "PURPLE"),
    /** Immune to poison and wither. */
    RECOVERING("recovering", false, "YELLOW"),
    /** Teleports the player at random when it lands. */
    CHORUS("chorus", false, "PURPLE"),
    /** Overflowing nutrition becomes saturation. */
    DIGESTING("digesting", false, "YELLOW"),
    /** Drops a level of experience every second. */
    CYCLING("cycling", false, "GREEN"),
    /** Animals will not breed. */
    DISGUSTED("disgusted", true, "RED"),
    /** Weakens everything nearby that does not share the effect. */
    ALIENATING("alienating", false, "YELLOW"),
    /** Reveals suspicious stew and suspicious sand. */
    SUSPICIOUS_SMELL("suspicious_smell", false, "YELLOW");

    private static final Map<String, CustomEffect> BY_ID = new LinkedHashMap<>();

    static {
        for (CustomEffect effect : values()) {
            BY_ID.put("yuanguo:" + effect.id, effect);
            BY_ID.put(effect.id, effect);
        }
    }

    private final String id;
    private final boolean harmful;
    private final String barColor;

    CustomEffect(String id, boolean harmful, String barColor) {
        this.id = id;
        this.harmful = harmful;
        this.barColor = barColor;
    }

    /** The path part of the id, without a namespace. */
    public String id() {
        return this.id;
    }

    /** The translation key the bundled lang files use. */
    public String nameKey() {
        return "effect.yuanguo." + this.id;
    }

    /** The translation key of the one line description shown under the name. */
    public String descriptionKey() {
        return "effect.yuanguo." + this.id + ".description";
    }

    public boolean harmful() {
        return this.harmful;
    }

    /** The boss bar colour name, resolved to an adventure colour by the registry. */
    public String barColor() {
        return this.barColor;
    }

    /** @return the effect for {@code yuanguo:<id>} or a bare id, or {@code null} when unknown. */
    @Nullable
    public static CustomEffect byId(String id) {
        return BY_ID.get(id);
    }
}
