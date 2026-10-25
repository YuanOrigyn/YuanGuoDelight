package dev.yuan.yuanguo.cauldron;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fruit table the cauldron chain needs, mirrored from the mod's {@code FruitType}.
 *
 * <p>Kept as a copy rather than read from the content pack because the chain is implemented in Java
 * and has to know the mixing cost and colour before any pack file is parsed.</p>
 */
public final class CauldronFruits {

    /** name, jellyCost, colour(0xRRGGBB) */
    public static final String[][] FRUITS = {
            { "apple",      "2", "C3833D" },
            { "blueberry",  "4", "5A32BF" },
            { "glowberry",  "4", "FAAB16" },
            { "hamimelon",  "4", "FFC14E" },
            { "melon",      "4", "C83627" },
            { "hawberry",   "4", "BF1D1D" },
            { "lychee",     "4", "FFF2FC" },
            { "mango",      "2", "FFB229" },
            { "orange",     "2", "F7970F" },
            { "peach",      "2", "E78264" },
            { "pear",       "2", "BECB32" },
            { "persimmon",  "2", "E15F1E" },
            { "pineapple",  "4", "FDDC28" },
            { "lemon",      "2", "FFE47A" },
            { "cranberry",  "4", "CF2626" },
            { "mangosteen", "2", "FBECD6" },
            { "sweetberry", "4", "C41A4F" },
            { "chorus",     "2", "D0A9D7" },
            { "bayberry",   "4", "541135" },
            { "kiwi",       "2", "89D90D" },
            { "fig",        "2", "E08517" },
            { "durian",     "2", "EDD955" },
    };

    /** The colour the mod paints a lemonade cauldron: 30% lemon over white. */
    public static final int LEMONADE_COLOUR = mix(0xFFFFFF, 0xFFE47A, 0.30F);

    private static final Map<String, Integer> INDEX = index();

    private CauldronFruits() {
    }

    private static Map<String, Integer> index() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < FRUITS.length; i++) {
            map.put(FRUITS[i][0], i);
        }
        return map;
    }

    public static boolean isFruit(String name) {
        return INDEX.containsKey(name);
    }

    public static int indexOf(String name) {
        return INDEX.getOrDefault(name, -1);
    }

    public static String name(int index) {
        return FRUITS[index][0];
    }

    public static int jellyCost(int index) {
        return Integer.parseInt(FRUITS[index][1]);
    }

    public static int colour(int index) {
        return Integer.parseInt(FRUITS[index][2], 16);
    }

    /** How much one fruit raises the cauldron level: {@code 4 / jellyCost}. */
    public static int advance(int index) {
        return 4 / jellyCost(index);
    }

    /** How many fruits a full lemonade cauldron gives up: {@code 12 / advance}. */
    public static int fruitPerLemonade(int index) {
        return 12 / advance(index);
    }

    /** The fruit item, which is a vanilla item for the fruits the mod does not add one for. */
    public static String fruitItem(String name) {
        return switch (name) {
            case "apple" -> "minecraft:apple";
            case "melon" -> "minecraft:melon_slice";
            case "sweetberry" -> "minecraft:sweet_berries";
            case "glowberry" -> "minecraft:glow_berries";
            case "chorus" -> "minecraft:chorus_fruit";
            default -> "yuanguo:" + name;
        };
    }

    /** The fruit a held item is, or `null` when the item is not one of the fruit items. */
    @Nullable
    public static String fruitForItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (String[] fruit : FRUITS) {
            if (fruitItem(fruit[0]).equals(itemId)) {
                return fruit[0];
            }
        }
        return null;
    }

    /** The mod's channel lerp, used for the lemonade colour. */
    public static int mix(int from, int to, float factor) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr * (1 - factor) + tr * factor);
        int g = Math.round(fg * (1 - factor) + tg * factor);
        int b = Math.round(fb * (1 - factor) + tb * factor);
        return (r << 16) | (g << 8) | b;
    }
}