import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Emits {@code effects.yml}, the table of status effects every food applies when eaten, and the
 * matching item lore.
 *
 * <p>The mod grants effects from two places. The food itself carries the effect level of its
 * {@code FoodType}, and the fruit behind it carries the actual effects. The effective duration is
 * {@code fruitFactor * foodType.effectLevel} ticks, which is why a meal lasts four times as long as
 * a snack made from the same fruit.</p>
 *
 * <p>Only items built through {@code FDFoodItem} grant effects. The raw fruit items are plain
 * {@code Item}s in the mod, so an apple or a lychee eaten straight off the tree is just food.</p>
 */
public final class Effects {

    private Effects() {
    }

    /**
     * The effects a fruit grants, as {@code fruit -> [effectId, durationFactor, amplifier]...}.
     *
     * <p>The factor is multiplied by the food's effect level; the amplifier is fixed.</p>
     */
    private static final Map<String, String[][]> FRUIT_EFFECTS = new LinkedHashMap<>();

    static {
        add("apple", "minecraft:absorption", "20", "0");
        add("blueberry", "minecraft:night_vision", "20", "0", "yuanguo:brightening", "20", "0");
        add("glowberry", "minecraft:glowing", "80", "0");
        add("hamimelon", "minecraft:health_boost", "20", "0");
        add("melon", "minecraft:regeneration", "40", "0");
        add("hawberry", "yuanguo:appetizing", "20", "0");
        add("lychee", "minecraft:strength", "10", "2");
        add("mango", "minecraft:strength", "20", "2", "yuanguo:rage_aura", "20", "0");
        add("orange", "minecraft:regeneration", "20", "0", "yuanguo:recovering", "20", "0");
        add("peach", "yuanguo:heal_aura", "10", "0");
        add("pear", "yuanguo:lozenge", "20", "0");
        add("pineapple", "minecraft:speed", "20", "0", "yuanguo:sweetening", "20", "0");
        add("lemon", "minecraft:haste", "20", "0", "yuanguo:refreshing", "20", "0");
        add("cranberry", "yuanguo:shrinking", "80", "0");
        // sweetberry has no effects of its own.
        add("chorus", "yuanguo:chorus", "1", "0");
        add("kiwi", "yuanguo:cycling", "4", "0");
        add("fig", "yuanguo:digesting", "80", "0");
        add("durian", "yuanguo:alienating", "40", "0", "yuanguo:suspicious_smell", "80", "0");
    }

    private static void add(String fruit, String... entries) {
        String[][] rows = new String[entries.length / 3][];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new String[] {entries[i * 3], entries[i * 3 + 1], entries[i * 3 + 2]};
        }
        FRUIT_EFFECTS.put(fruit, rows);
    }

    /** {@code FoodType -> effectLevel}; 0 means the food grants no effects at all. */
    private static final Map<String, Integer> EFFECT_LEVEL = Map.ofEntries(
            Map.entry("JUICE", 10),
            Map.entry("COCKTAIL", 20),
            Map.entry("JELLY", 20),
            Map.entry("JELLO", 20),
            Map.entry("FRUIT", 5),
            Map.entry("SLICE", 0),
            Map.entry("SHEET", 10),
            Map.entry("STICK", 10),
            Map.entry("SWEET", 20),
            Map.entry("CREAM", 20),
            Map.entry("BOWL", 30),
            Map.entry("MEAL", 40),
            Map.entry("DESSERT", 20),
            Map.entry("STAPLE", 40),
            Map.entry("ROLL", 10),
            Map.entry("COOKIE", 5),
            Map.entry("MANGOSTEEN_CAKE", 30),
            Map.entry("DURIAN_FLESH", 0));

    /**
     * The meals that also grant Farmer's Delight's Nourishment, in the duration the mod asks for.
     *
     * <p>Nourishment is PapersDelight's effect rather than one of the mod's own, so it is granted
     * from the content pack through the {@code papersdelight:nourishment_effect} function, exactly
     * like the Farmer's Delight pack does for its own meals. It is listed here as well so the item
     * tooltip can show it.</p>
     */
    private static final Map<String, Integer> NOURISHMENT = new LinkedHashMap<>();

    static {
        NOURISHMENT.put("mangosteen_cake", 1200);
        NOURISHMENT.put("pear_with_rock_sugar", 1200);
        NOURISHMENT.put("orange_chicken", 3600);
        NOURISHMENT.put("fig_chicken_stew", 3600);
        NOURISHMENT.put("orange_marinated_pork", 3600);
        NOURISHMENT.put("bowl_of_pineapple_fried_rice", 6000);
        NOURISHMENT.put("pineapple_marinated_pork", 3600);
        NOURISHMENT.put("lychee_chicken", 3600);
    }

    /** How often a fruit effect fires, per effect level, when it is not guaranteed. */
    private static final Map<String, String> CHANCE = Map.of("yuanguo:chorus", "0.05");

    /**
     * Effects whose duration is a fixed number of ticks rather than a multiple of the food level.
     *
     * <p>The mod writes the chorus effect as {@code lv -> 1}: one tick, whatever the food, because
     * the teleport fires on the first tick and the effect then expires.</p>
     */
    private static final Set<String> CONSTANT_DURATION = Set.of("yuanguo:chorus");

    /** {@code yuanguo:<item> -> entries}, built before the items are written so they can carry lore. */
    static final Map<String, Object> BY_ITEM = new LinkedHashMap<>();

    /** Fills {@link #BY_ITEM}. Runs before {@code Items.gen()} so the lore can be attached. */
    static void build() {
        BY_ITEM.clear();
        for (String[] fruit : FruitData.FRUITS) {
            put(fruit[0] + "_jelly", fruit[0], "JELLY");
            put(fruit[0] + "_jello", fruit[0], "JELLO");
            // The mod lets a jelly bread carry any fruit jelly, and the filled bread uses the
            // JELLY effect level. Sweetberry has no effects, so put() skips it.
            put(fruit[0] + "_jelly_bread", fruit[0], "JELLY");
        }
        for (String[] food : FruitData.FOODS) {
            put(food[0], food[1], food[2]);
        }
        for (String[] juice : FruitData.JUICES) {
            put(juice[0], juice[1], "JUICE");
        }
        // Items the food table does not cover.
        put("jelly_bread", "sweetberry", "SWEET");
        put("mangosteen_cake", "mangosteen", "MANGOSTEEN_CAKE");
    }

    static void gen() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("effects", BY_ITEM);
        root.put("aura-ranges", Gen.map(
                "rage", 24,
                "heal", 6,
                "alienating", 12));

        // The table is read by the plugin, not by CraftEngine: CraftEngine only accepts the config
        // sections it knows, so a custom section in the pack would be rejected as unknown.
        Path plugin = Path.of("D:/plugins-by/YuanGuoDelight/src/main/resources/effects.yml");
        if (Files.isDirectory(plugin.getParent())) {
            String body = "# Generated from the Fruits Delight mod by the YuanGuoDelight build script.\n"
                    + Gen.YAML.dump(root);
            Files.writeString(plugin, body, StandardCharsets.UTF_8);
            System.out.println("effects.yml written to the plugin resources");
        } else {
            System.out.println("plugin resources not found; effects.yml was not written");
        }
    }

    private static void put(Map<String, Object> items, String item, String fruit, String foodType) {
        int level = EFFECT_LEVEL.getOrDefault(foodType, 0);
        String[][] table = FRUIT_EFFECTS.get(fruit);
        if (level <= 0 || table == null || table.length == 0) {
            return;
        }
        List<Object> list = new ArrayList<>();
        for (String[] row : table) {
            String effect = row[0];
            int factor = Integer.parseInt(row[1]);
            int duration = CONSTANT_DURATION.contains(effect) ? factor : factor * level;
            if (duration <= 0) {
                continue;
            }
            Map<String, Object> entry = Gen.map(
                    "effect", effect,
                    "duration", duration,
                    "amplifier", Integer.parseInt(row[2]));
            String chance = CHANCE.get(effect);
            if (chance != null) {
                double value = Math.min(1.0D, Double.parseDouble(chance) * level);
                if (value < 1.0D) {
                    entry.put("chance", value);
                }
            }
            list.add(entry);
        }
        if (!list.isEmpty()) {
            items.put(Gen.id(item), list);
        }
    }

    private static void put(String item, String fruit, String foodType) {
        put(BY_ITEM, item, fruit, foodType);
    }

    // ------------------------------------------------------------------ lore

    /**
     * The tooltip lines an item shows for its effects, in the same shape the Farmer's Delight pack
     * uses: a blue line per effect, with the duration and the chance when they apply.
     */
    static List<String> loreFor(String item) {
        List<?> entries = (List<?>) BY_ITEM.get(Gen.id(item));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<String> lore = new ArrayList<>(entries.size());
        Integer nourishment = NOURISHMENT.get(item);
        if (nourishment != null) {
            lore.add(nourishmentLine(nourishment));
        }
        for (Object element : entries) {
            if (!(element instanceof Map<?, ?> entry)) {
                continue;
            }
            String effect = String.valueOf(entry.get("effect"));
            int duration = number(entry.get("duration"), 0);
            int amplifier = number(entry.get("amplifier"), 0);
            Object chance = entry.get("chance");

            String text = "<lang:" + langKey(effect) + ">";
            if (amplifier > 0) {
                text = "<lang:potion.withAmplifier:'" + text + "':'<lang:" + potencyKey(amplifier) + ">'>";
                if (duration > 20) {
                    text += " (" + duration(duration) + ")";
                }
            } else if (duration > 20) {
                text = "<lang:potion.withDuration:'" + text + "':'" + duration(duration) + "'>";
            }
            if (chance != null) {
                int percent = (int) Math.round(number(chance, 1.0D) * 100.0D);
                if (amplifier > 0) {
                    text += " (" + percent + "%)";
                } else {
                    text = "<lang:yuanguo.tooltip.chance:'" + text + "':'" + percent + "'>";
                }
            }
            lore.add("<!i><blue>" + text + "</blue>");
        }
        return lore;
    }

    /** The Nourishment duration of an item, or {@code null} when it grants none. */
    static Integer nourishmentTicks(String item) {
        return NOURISHMENT.get(item);
    }

    /** The Nourishment line, which is not one of the entries in the effect table. */
    private static String nourishmentLine(int ticks) {
        String text = "<lang:effect.farmersdelight.nourishment>";
        if (ticks > 20) {
            text = "<lang:potion.withDuration:'" + text + "':'" + duration(ticks) + "'>";
        }
        return "<!i><blue>" + text + "</blue>";
    }

    private static String langKey(String effect) {
        int colon = effect.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : effect.substring(0, colon);
        String path = colon < 0 ? effect : effect.substring(colon + 1);
        return "effect." + namespace + "." + path;
    }

    private static String potencyKey(int amplifier) {
        return amplifier <= 5 ? "potion.potency." + amplifier : "enchantment.level." + (amplifier + 1);
    }

    private static String duration(int ticks) {
        int seconds = ticks / 20;
        int rest = seconds % 60;
        return (seconds / 60) + ":" + (rest < 10 ? "0" : "") + rest;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
