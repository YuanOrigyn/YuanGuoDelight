import java.util.List;

/** Static data mirrored from the Fruits Delight mod (FruitType / FoodType / FDTreeType ...). */
public final class FruitData {

    private FruitData() {}

    /** name, jellyCost, colour(0xRRGGBB), season */
    public static final String[][] FRUITS = {
            { "apple",      "2", "C3833D", "FALL"   },
            { "blueberry",  "4", "5A32BF", "SPRING" },
            { "glowberry",  "4", "FAAB16", "ALL"    },
            { "hamimelon",  "4", "FFC14E", "SUMMER" },
            { "melon",      "4", "C83627", "ALL"    },
            { "hawberry",   "4", "BF1D1D", "WINTER" },
            { "lychee",     "4", "FFF2FC", "SUMMER" },
            { "mango",      "2", "FFB229", "SUMMER" },
            { "orange",     "2", "F7970F", "FALL"   },
            { "peach",      "2", "E78264", "SPRING" },
            { "pear",       "2", "BECB32", "SPRING" },
            { "persimmon",  "2", "E15F1E", "WINTER" },
            { "pineapple",  "4", "FDDC28", "SUMMER" },
            { "lemon",      "2", "FFE47A", "SUMMER" },
            { "cranberry",  "4", "CF2626", "FALL"   },
            { "mangosteen", "2", "FBECD6", "SUMMER" },
            { "sweetberry", "4", "C41A4F", "ALL"    },
            { "chorus",     "2", "D0A9D7", "ALL"    },
            { "bayberry",   "4", "541135", "SUMMER" },
            { "kiwi",       "2", "89D90D", "WINTER" },
            { "fig",        "2", "E08517", "FALL"   },
            { "durian",     "2", "EDD955", "SUMMER" },
    };

    public static String fruitName(int i)     { return FRUITS[i][0]; }
    public static int    jellyCost(int i)     { return Integer.parseInt(FRUITS[i][1]); }
    public static String colour(int i)        { return FRUITS[i][2]; }
    public static String season(int i)        { return FRUITS[i][3]; }
    public static int    indexOf(String name) {
        for (int i = 0; i < FRUITS.length; i++) if (FRUITS[i][0].equals(name)) return i;
        return -1;
    }

    /** Fruits that exist as a real item in the pack (everything except apple / melon / sweetberry / glowberry / chorus). */
    public static boolean hasFruitItem(String name) {
        return switch (name) {
            case "apple", "melon", "sweetberry", "glowberry", "chorus" -> false;
            default -> true;
        };
    }

    /** The vanilla item used for a fruit that has no custom fruit item. */
    public static String vanillaFruitItem(String name) {
        return switch (name) {
            case "apple"      -> "minecraft:apple";
            case "melon"      -> "minecraft:melon_slice";
            case "sweetberry" -> "minecraft:sweet_berries";
            case "glowberry"  -> "minecraft:glow_berries";
            case "chorus"     -> "minecraft:chorus_fruit";
            default -> null;
        };
    }

    /** name, log, treeType, fruitCount, food, fast, wildWeight */
    public static final String[][] TREES = {
            { "pear",       "birch", "TALL",   "3", "false", "15"  },
            { "hawberry",   "spruce","TALL",   "2", "true",  "20"  },
            { "lychee",     "jungle","TALL",   "2", "true",  "4"   },
            { "mango",      "jungle","NORMAL", "3", "false", "4"   },
            { "persimmon",  "spruce","FANCY",  "3", "false", "50"  },
            { "peach",      "jungle","PEACH",  "3", "false", "8"   },
            { "orange",     "oak",   "NORMAL", "3", "false", "20"  },
            { "apple",      "oak",   "NORMAL", "-1","false", "20"  },
            { "mangosteen", "oak",   "FANCY",  "3", "false", "40"  },
            { "bayberry",   "spruce","TALL",   "2", "true",  "15"  },
            { "kiwi",       "jungle","NORMAL", "3", "true",  "20"  },
            { "fig",        "oak",   "NORMAL", "3", "false", "20"  },
            { "durian",     "jungle","DURIAN", "-1","false", "200" },
    };

    /** name, log, bushType(FDBushType), food, fast */
    public static final String[][] BUSHES = {
            { "blueberry", "oak",    "BLOCK", "2", "true"  },
            { "lemon",     "jungle", "TALL",  "4", "false" },
            { "cranberry", "oak",    "CROSS", "2", "true"  },
    };

    public static String[] tree(String name) {
        for (String[] t : TREES) if (t[0].equals(name)) return t;
        return null;
    }

    public static String[] bush(String name) {
        for (String[] b : BUSHES) if (b[0].equals(name)) return b;
        return null;
    }

    /** name -> nutrition, saturation, canAlwaysEat, fast */
    public static final String[][] FOOD_TYPES = {
            { "JUICE",    "1",  "0.2", "true",  "false" },
            { "COCKTAIL", "1",  "0.2", "true",  "false" },
            { "JELLY",    "4",  "0.3", "false", "false" },
            { "JELLO",    "1",  "0.3", "true",  "false" },
            { "FRUIT",    "4",  "0.3", "false", "false" },
            { "SLICE",    "2",  "0.3", "true",  "false" },
            { "SHEET",    "3",  "0.3", "true",  "false" },
            { "STICK",    "4",  "0.4", "true",  "false" },
            { "SWEET",    "8",  "0.5", "false", "false" },
            { "CREAM",    "6",  "0.5", "false", "false" },
            { "BOWL",     "8",  "0.6", "false", "false" },
            { "MEAL",     "12", "0.8", "false", "false" },
            { "DESSERT",  "5",  "0.4", "false", "false" },
            { "STAPLE",   "14", "0.8", "false", "false" },
            { "ROLL",     "3",  "0.4", "true",  "false" },
            { "COOKIE",   "1",  "0.3", "true",  "false" },
    };

    public static String[] foodType(String name) {
        for (String[] f : FOOD_TYPES) if (f[0].equals(name)) return f;
        return null;
    }

    /** FDFood: id, fruit, foodType, needsJellyTag */
    public static final String[][] FOODS = {
            { "blueberry_custard",          "blueberry",  "CREAM"   },
            { "blueberry_muffin",           "blueberry",  "DESSERT" },
            { "cranberry_muffin",           "cranberry",  "DESSERT" },
            { "fig_pudding_slice",          "fig",        "ROLL"    },
            { "hamimelon_popsicle",         "hamimelon",  "STICK"   },
            { "kiwi_popsicle",              "kiwi",       "STICK"   },
            { "hamimelon_shaved_ice",       "hamimelon",  "JUICE"   },
            { "hawberry_roll",              "hawberry",   "ROLL"    },
            { "hawberry_sheet",             "hawberry",   "SHEET"   },
            { "hawberry_stick",             "hawberry",   "STICK"   },
            { "orange_slice",               "orange",     "SLICE"   },
            { "lemon_slice",                "lemon",      "SLICE"   },
            { "durian_flesh",               "durian",     "SLICE"   },
            { "baked_pear",                 "pear",       "FRUIT"   },
            { "baked_durian",               "durian",     "FRUIT"   },
            { "pineapple_pie",              "pineapple",  "DESSERT" },
            { "durian_pie",                 "durian",     "DESSERT" },
            { "lemon_tart",                 "lemon",      "DESSERT" },
            { "fig_tart",                   "fig",        "DESSERT" },
            { "mango_milkshake",            "mango",      "JUICE"   },
            { "mango_salad",                "mango",      "BOWL"    },
            { "dried_persimmon",            "persimmon",  "FRUIT"   },
            { "persimmon_cookie",           "persimmon",  "COOKIE"  },
            { "lemon_cookie",               "lemon",      "COOKIE"  },
            { "cranberry_cookie",           "cranberry",  "COOKIE"  },
            { "bayberry_cookie",            "bayberry",   "COOKIE"  },
            { "pear_with_rock_sugar",       "pear",       "BOWL"    },
            { "orange_chicken",             "orange",     "MEAL"    },
            { "fig_chicken_stew",           "fig",        "MEAL"    },
            { "orange_marinated_pork",      "orange",     "MEAL"    },
            { "bowl_of_pineapple_fried_rice","pineapple", "STAPLE"  },
            { "pineapple_marinated_pork",   "pineapple",  "MEAL"    },
            { "lychee_chicken",             "lychee",     "MEAL"    },
    };

    /** FDJuice: id, fruit */
    public static final String[][] JUICES = {
            { "hamimelon_juice",    "hamimelon" },
            { "hawberry_tea",       "hawberry"  },
            { "orange_juice",       "orange"    },
            { "lemon_juice",        "lemon"     },
            { "pear_juice",         "pear"      },
            { "mango_tea",          "mango"     },
            { "peach_tea",          "peach"     },
            { "lychee_cherry_tea",  "lychee"    },
            { "mangosteen_tea",     "mangosteen"},
            { "bayberry_soup",      "bayberry"  },
            { "kiwi_juice",         "kiwi"      },
            { "bellini_cocktail",   "peach"     },
    };

    /** FDBlocks special items that are not covered by the fruit tables. */
    public static final List<String> EXTRA_ITEMS = List.of(
            "hamimelon_seeds", "hamimelon_slice", "lemon_seeds", "pineapple_sapling",
            "pineapple_slice", "jelly_bread", "durian_helmet", "durian_shell",
            "durian_sapling", "dummy_cauldron");

    /** Potted saplings generated by the mod. */
    public static final List<String> POTTED_TREES = List.of(
            "apple", "bayberry", "durian", "fig", "hawberry", "kiwi", "lychee",
            "mango", "mangosteen", "orange", "peach", "pear", "persimmon");
}