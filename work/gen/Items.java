import java.io.IOException;
import java.util.*;

/** Emits configuration/items.yml. */
public final class Items {

    private Items() {
    }

    static final Map<String, Object> ITEMS = new LinkedHashMap<>();

    static void gen() throws IOException {
        fruitItems();
        seeds();
        foods();
        juices();
        jellies();
        blockItems();

        // CraftEngine has no tag registry: a tag exists because an item declares it.
        Tags.applyItemTags(ITEMS);

        Gen.writeYaml("configuration/items.yml", Gen.map("items", ITEMS));
    }

    // ------------------------------------------------------------------ items

    /** The fruit items themselves. Fruits that are a vanilla item are skipped. */
    private static void fruitItems() {
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            if (!FruitData.hasFruitItem(name)) {
                continue;
            }
            String[] tree = FruitData.tree(name);
            String[] bush = FruitData.bush(name);
            boolean fast = (tree != null && "true".equals(tree[4]))
                    || (bush != null && "true".equals(bush[4]));
            int nutrition = 4;
            if (tree != null && Integer.parseInt(tree[3]) > 0) {
                nutrition = Integer.parseInt(tree[3]);
            } else if (bush != null) {
                nutrition = 2;
            }
            Map<String, Object> item = Gen.map(
                    "material", "apple",
                    "custom_model_data", nextCustomModelData(),
                    "data", Gen.map("item_name", nameTag(name),
                            "$$>=1.20.5", Gen.map("food", food(nutrition, 0.3, fast, false))),
                    "model", Gen.id("item/" + name));
            String[] bushType = FruitData.bush(name);
            if (name.equals("durian")) {
                item.put("behavior", Gen.map("type", "block_item", "block", Gen.id("durian")));
            } else if (bushType != null && !"TALL".equals(bushType[2])) {
                // Blueberry and cranberry are planted straight from the fruit item.
                item.put("behavior", Gen.map("type", "block_item", "block", Gen.id(name + "_bush")));
            } else if (name.equals("hamimelon") || name.equals("pineapple")) {
                // The mod ships these two as placeable crops you slice open, not as snacks.
                item.put("behavior", Gen.map("type", "block_item", "block", Gen.id(name)));
            }
            ITEMS.put(Gen.id(name), item);
        }
    }

    /** Seeds and saplings that are planted. */
    private static void seeds() {
        ITEMS.put(Gen.id("hamimelon_seeds"), seed("hamimelon_seeds", "yuanguo:hamimelon_stem"));
        ITEMS.put(Gen.id("lemon_seeds"), seed("lemon_seeds", "yuanguo:lemon_tree"));
        ITEMS.put(Gen.id("pineapple_sapling"), seed("pineapple_sapling", "yuanguo:pineapple"));

        for (String[] tree : FruitData.TREES) {
            String name = tree[0];
            String key = name.equals("durian") ? "durian_sapling" : name + "_sapling";
            Map<String, Object> item = seed(key, "yuanguo:" + name + "_sapling");
            ITEMS.put(Gen.id(key), item);
        }
        // Only the tall lemon bush has a dedicated seed item.
        ITEMS.put(Gen.id("lemon_seeds"), seed("lemon_seeds", "yuanguo:lemon_tree"));
    }

    private static Map<String, Object> seed(String name, String block) {
        return Gen.map(
                "material", "cocoa_beans",
                "custom_model_data", nextCustomModelData(),
                "data", Gen.map("item_name", nameTag(name)),
                "model", Gen.id("item/" + name),
                "behavior", Gen.list(
                        Gen.map("type", "block_item", "block", block),
                        Gen.map("type", "compostable_item", "chance", 0.3)),
                "settings", Gen.map(
                        "compost_probability", 0.3,
                        "tags", Gen.list("minecraft:villager_plantable_seeds")));
    }

    /** Every FDFood entry. */
    private static void foods() {
        for (String[] food : FruitData.FOODS) {
            String name = food[0];
            String[] ft = FruitData.foodType(food[2]);
            ITEMS.put(Gen.id(name), simple(name, Integer.parseInt(ft[1]), Double.parseDouble(ft[2]),
                    "true".equals(ft[3]), "true".equals(ft[4])));
        }
    }

    /** FDJuice entries, all drinks. */
    private static void juices() {
        for (String[] juice : FruitData.JUICES) {
            ITEMS.put(Gen.id(juice[0]), simple(juice[0], 1, 0.2, true, false));
        }
    }

    /** Jelly bottles and jello items. */
    private static void jellies() {
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            ITEMS.put(Gen.id(name + "_jelly"), jelly(name, false));
            ITEMS.put(Gen.id(name + "_jello"), jelly(name, true));
        }
    }

    private static Map<String, Object> jelly(String fruit, boolean jello) {
        String name = fruit + (jello ? "_jello" : "_jelly");
        Map<String, Object> data = Gen.map("item_name", nameTag(name));
        List<String> lore = Effects.loreFor(name);
        if (!lore.isEmpty()) {
            data.put("lore", lore);
        }
        data.put("$$>=1.20.5", Gen.map("food", food(jello ? 1 : 4, 0.3, jello, false)));
        return Gen.map(
                "material", "honey_bottle",
                "custom_model_data", nextCustomModelData(),
                "data", data,
                "model", Gen.id("item/" + name));
    }
    /** Plain items, block items and the remaining special cases. */
    private static void blockItems() {
        // --- plain items
        ITEMS.put(Gen.id("hamimelon_slice"), simple("hamimelon_slice", 2, 0.3, true, false));
        ITEMS.put(Gen.id("pineapple_slice"), simple("pineapple_slice", 2, 0.3, true, false));
        ITEMS.put(Gen.id("jelly_bread"), simple("jelly_bread", 8, 0.5, false, false));
        ITEMS.put(Gen.id("durian_shell"), simple("durian_shell", 0, 0, false, false));
        ITEMS.put(Gen.id("fig_pudding_slice"), simple("fig_pudding_slice", 3, 0.4, true, false));

        // --- durian helmet
        ITEMS.put(Gen.id("durian_helmet"), Gen.map(
                "material", "leather_helmet",
                "custom_model_data", nextCustomModelData(),
                "data", Gen.map("item_name", nameTag("durian_helmet"),
                        "$$>=1.21.2", Gen.map("equippable", Gen.map("slot", "head"))),
                "model", Gen.map(
                        "type", "minecraft:select",
                        "property", "minecraft:display_context",
                        "cases", Gen.list(Gen.map(
                                "when", Gen.list("head"),
                                "model", Gen.map(
                                        "type", "minecraft:model",
                                        "path", Gen.id("item/durian_helmet_base")))),
                        "fallback", Gen.map(
                                "type", "minecraft:model",
                                "path", Gen.id("item/durian_helmet")))));

        // --- serving blocks
        // The pudding is only a placeable dish; the other two are edible as well, exactly like the
        // mod, which builds them from FDFood entries rather than plain block items.
        ITEMS.put(Gen.id("fig_pudding"), blockItem("fig_pudding", "yuanguo:fig_pudding"));
        Map<String, Object> mangosteenCake = Gen.map(
                "material", "apple",
                "custom_model_data", nextCustomModelData(),
                "data", foodData("mangosteen_cake", food(9, 0.6, false, false)),
                "model", Gen.id("item/mangosteen_cake"),
                "behavior", Gen.map("type", "block_item", "block", Gen.id("mangosteen_cake")));
        applyNourishment("mangosteen_cake", mangosteenCake);
        ITEMS.put(Gen.id("mangosteen_cake"), mangosteenCake);

        Map<String, Object> friedRice = Gen.map(
                "material", "apple",
                "custom_model_data", nextCustomModelData(),
                "data", foodData("bowl_of_pineapple_fried_rice", food(14, 0.8, false, false)),
                "model", Gen.id("item/bowl_of_pineapple_fried_rice"),
                "behavior", Gen.map("type", "block_item", "block", Gen.id("pineapple_fried_rice")));
        applyNourishment("bowl_of_pineapple_fried_rice", friedRice);
        ITEMS.put(Gen.id("bowl_of_pineapple_fried_rice"), friedRice);
        // The placed dish has its own item in the mod (a plain BlockItem), separate from the bowl
        // you eat from. Without it the block could not be picked up or placed.
        ITEMS.put(Gen.id("pineapple_fried_rice"),
                blockItem("pineapple_fried_rice", "yuanguo:pineapple_fried_rice"));
        ITEMS.put(Gen.id("wild_pineapple"), blockItem("wild_pineapple", "yuanguo:wild_pineapple"));
        // The blueberry bush drops itself on silk touch, so it needs a placeable item.
        ITEMS.put(Gen.id("blueberry_bush"), blockItem("blueberry_bush", "yuanguo:blueberry_bush"));

        // --- leaves, sapling pots
        for (String[] tree : FruitData.TREES) {
            String name = tree[0];
            ITEMS.put(Gen.id(name + "_leaves"), blockItem(name + "_leaves", "yuanguo:" + name + "_leaves"));
            ITEMS.put(Gen.id("potted_" + name + "_sapling"), Gen.map(
                    "material", "paper",
                    "custom_model_data", nextCustomModelData(),
                    "data", Gen.map("item_name", nameTag("potted_" + name + "_sapling")),
                    "model", Gen.id("block/potted_" + name + "_sapling"),
                    "behavior", Gen.map("type", "block_item", "block",
                            "yuanguo:potted_" + name + "_sapling")));
            ITEMS.put(Gen.id("entity_renderer/potted_" + name), Gen.map(
                    "material", "paper",
                    "data", Gen.map("item_name", nameTag("potted_" + name + "_sapling")),
                    "model", Gen.id("block/potted_" + name + "_plant")));
        }

        // --- crates
        for (String name : crateNames()) {
            ITEMS.put(Gen.id(name + "_crate"), blockItem(name + "_crate", "yuanguo:" + name + "_crate"));
        }

        // --- jellies
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            Map<String, Object> jelly = jelly(name, false);
            jelly.put("behavior", Gen.map("type", "block_item", "block",
                    "yuanguo:" + name + "_jelly"));
            ITEMS.put(Gen.id(name + "_jelly"), jelly);
            ITEMS.put(Gen.id(name + "_jelly_block"),
                    blockItem(name + "_jelly_block", "yuanguo:" + name + "_jelly_block"));
            ITEMS.put(Gen.id(name + "_jello_block"),
                    blockItem(name + "_jello_block", "yuanguo:" + name + "_jello_block"));
        }


        // --- the jelly bottle's item display entity, matching the jug renderer the FD pack uses
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            ITEMS.put(Gen.id("entity_renderer/" + name + "_jelly"), Gen.map(
                    "model", Gen.id("block/" + name + "_jelly")));
        }

        // --- the feast item display entities, exactly how the FD pack names its renderers
        String[] feastModels = {
                "fig_pudding_0", "fig_pudding_1", "fig_pudding_2", "fig_pudding_3",
                "mangosteen_cake_0", "mangosteen_cake_1", "mangosteen_cake_2", "mangosteen_cake_3",
                "pineapple_fried_rice", "pineapple_fried_rice_1", "pineapple_fried_rice_2",
                "pineapple_fried_rice_3", "pineapple_fried_rice_4"};
        for (String name : feastModels) {
            ITEMS.put(Gen.id("entity_renderer/" + name), Gen.map(
                    "model", Gen.id("block/" + name)));
        }
        // --- cauldrons are decorative, so they reuse the vanilla item name
        ITEMS.put(Gen.id("dummy_cauldron"), Gen.map(
                "material", "cauldron",
                "custom_model_data", nextCustomModelData(),
                "data", Gen.map("item_name", "<!i><lang:block.minecraft.water_cauldron>"),
                "model", Gen.id("item/dummy_cauldron")));
        ITEMS.put(Gen.id("lemonade_cauldron"), cauldronItem("lemonade_cauldron"));
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            ITEMS.put(Gen.id(name + "_cauldron"), cauldronItem(name + "_cauldron"));
        }

        // --- the cauldron contents, one display entity per juice level and jam/jello stage
        ITEMS.put(Gen.id("entity_renderer/lemonade_cauldron"), Gen.map(
                "material", "cauldron",
                "data", Gen.map("item_name", liquidTag("lemonade_cauldron")),
                "model", Gen.id("block/lemonade_cauldron_liquid")));
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            for (int level = 1; level <= 12; level++) {
                ITEMS.put(Gen.id("entity_renderer/" + name + "_cauldron_juice_" + level), Gen.map(
                        "material", "cauldron",
                        "data", Gen.map("item_name", liquidTag(name + "_cauldron")),
                        "model", Gen.id("block/" + name + "_cauldron_liquid_" + level)));
            }
            for (String kind : new String[] { "jelly", "jello" }) {
                for (int level = 1; level <= 3; level++) {
                    ITEMS.put(Gen.id("entity_renderer/" + name + "_cauldron_" + kind + "_" + level),
                            Gen.map("material", "cauldron",
                                    "data", Gen.map("item_name", liquidTag(name + "_" + kind + "_cauldron")),
                                    "model", Gen.id("block/" + name + "_cauldron_" + kind
                                            + "_liquid_" + level)));
                }
            }
        }
    }

    private static Map<String, Object> cauldronItem(String name) {
        return Gen.map(
                "material", "cauldron",
                "custom_model_data", nextCustomModelData(),
                "data", Gen.map("item_name", nameTag(name)),
                "model", Gen.id("item/" + name),
                "behavior", Gen.map("type", "block_item", "block", Gen.id(name)));
    }

    static List<String> crateNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String[] tree : FruitData.TREES) {
            // Durian has no crate in the original mod.
            if (!"durian".equals(tree[0])) {
                names.add(tree[0]);
            }
        }
        for (String[] bush : FruitData.BUSHES) {
            names.add(bush[0]);
        }
        names.add("pineapple");
        names.add("apple");
        return new ArrayList<>(names);
    }

    // ----------------------------------------------------------------- helpers

    private static Map<String, Object> simple(String name, int nutrition, double saturation,
                                              boolean alwaysEat, boolean fast) {
        Map<String, Object> data = Gen.map("item_name", nameTag(name));
        List<String> lore = Effects.loreFor(name);
        if (!lore.isEmpty()) {
            data.put("lore", lore);
        }
        data.put("$$>=1.20.5", Gen.map("food", food(nutrition, saturation, alwaysEat, fast)));
        Map<String, Object> item = Gen.map(
                "material", "apple",
                "custom_model_data", nextCustomModelData(),
                "data", data,
                "model", Gen.id("item/" + name));
        applyNourishment(name, item);
        return item;
    }

    /** The item name, effect lore and food component shared by the plain and the edible items. */
    private static Map<String, Object> foodData(String name, Map<String, Object> food) {
        Map<String, Object> data = Gen.map("item_name", nameTag(name));
        List<String> lore = Effects.loreFor(name);
        if (!lore.isEmpty()) {
            data.put("lore", lore);
        }
        data.put("$$>=1.20.5", Gen.map("food", food));
        return data;
    }

    private static Map<String, Object> blockItem(String name, String block) {
        Map<String, Object> data = Gen.map("item_name", nameTag(name));
        List<String> lore = Effects.loreFor(name);
        if (!lore.isEmpty()) {
            data.put("lore", lore);
        }
        Map<String, Object> item = Gen.map(
                "material", "paper",
                "custom_model_data", nextCustomModelData(),
                "data", data,
                "model", Gen.id("item/" + name),
                "behavior", Gen.map("type", "block_item", "block", block));
        applyNourishment(name, item);
        return item;
    }

    /**
     * Meals also grant Farmer's Delight's Nourishment. It is PapersDelight's effect rather than one
     * of the mod's own, so the pack asks PapersDelight for it on consume, exactly like the Farmer's
     * Delight pack does for its own meals.
     */
    private static void applyNourishment(String name, Map<String, Object> item) {
        Integer duration = Effects.nourishmentTicks(name);
        if (duration == null) {
            return;
        }
        item.put("events", Gen.list(Gen.map(
                "on", "consume",
                "functions", Gen.list(Gen.map(
                        "type", "papersdelight:nourishment_effect",
                        "duration", duration)))));
    }

    private static Map<String, Object> food(int nutrition, double saturation,
                                            boolean alwaysEat, boolean fast) {
        Map<String, Object> food = Gen.map(
                "nutrition", nutrition,
                "saturation", saturation);
        if (alwaysEat) {
            food.put("can_always_eat", true);
        }
        if (fast) {
            food.put("eat_seconds", 1.6);
        }
        return food;
    }

    /**
     * The translation key for an item's display name.
     *
     * <p>The mod names block-backed items under {@code block.fruitsdelight.*} and plain items under
     * {@code item.fruitsdelight.*}. Using the wrong one makes CraftEngine fall back to the raw id,
     * which is what showed the item as {@code yuanguo:apple_crate} instead of its name, so the
     * namespace is picked from whichever key the mod actually declares.</p>
     */
    static String nameTag(String name) {
        String kind = Lang.hasModKey("block.fruitsdelight." + name) ? "block" : "item";
        return "<!i><lang:" + kind + ".yuanguo." + name + ">";
    }

    /**
     * The translation key for cauldron contents. Blocks keep the "cauldron" in their name; the liquid
     * shown inside them does not, so the two use separate key families.
     */
    static String liquidTag(String name) {
        return "<!i><lang:liquid.yuanguo." + name + ">";
    }

    /**
     * Assigns each item a distinct custom model data value.
     *
     * <p>CraftEngine overrides the vanilla item's model through custom model data, and several
     * hundred items share a handful of vanilla materials. Without a unique value per item the
     * client keeps rendering the vanilla material, which is why leather horse armour and note
     * blocks leaked through as icons.</p>
     */
    private static final java.util.concurrent.atomic.AtomicInteger CMD =
            new java.util.concurrent.atomic.AtomicInteger(115000);

    static int nextCustomModelData() {
        return CMD.getAndIncrement();
    }
}
