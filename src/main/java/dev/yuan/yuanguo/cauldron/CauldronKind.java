package dev.yuan.yuanguo.cauldron;

/**
 * What a cauldron holds. The mod has four shapes of cauldron and they share one behavior class.
 */
public enum CauldronKind {

    /** Water plus a lemon slice. Always full, no level property. */
    LEMONADE,
    /** Fruit juice, level 1..12. */
    FRUIT,
    /** Fruit jam, level 1..3. */
    JELLY,
    /** Fruit jello, level 1..3. */
    JELLO;

    /**
     * Reads the kind from the cauldron's {@code kind} block property.
     *
     * <p>Every fruit shares one block, so the block id no longer carries the shape. The property is
     * {@code juice}, {@code jelly} or {@code jello}; lemonade has no property and is recognised by
     * its own block id instead.</p>
     */
    public static CauldronKind fromState(String kind) {
        return switch (kind) {
            case "jelly" -> JELLY;
            case "jello" -> JELLO;
            default -> FRUIT;
        };
    }

    /** The {@code kind} property value for this shape. */
    public String propertyValue() {
        return switch (this) {
            case LEMONADE, FRUIT -> "juice";
            case JELLY -> "jelly";
            case JELLO -> "jello";
        };
    }
}
