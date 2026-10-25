import java.io.IOException;
import java.util.*;

/**
 * Emits configuration/categories.yml, the CraftEngine item browser layout.
 *
 * <p>The format follows the Farmer's Delight pack: a root category that lists the child categories by
 * {@code '#id'} reference, then one section per group. CraftEngine resolves a {@code '#id'} entry
 * against another category in the same file, and a bare item id against the items the pack declares.</p>
 *
 * <p>The groups are ordered the way a player meets them: raw fruit, the plants that grow it, the
 * cooked food, the drinks, the jams, the table blocks the food is served on, then storage, brewing
 * and gear. Blocks that only exist to be placed on a table are kept out of the jam group so a
 * browse of "Jam Blocks" is not diluted by the pudding and the cake.</p>
 */
public final class Categories {

    private Categories() {
    }

    static void gen() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put(Gen.id("default"), Gen.map(
                "priority", 1,
                "name", "<!i><gold><lang:itemGroup.yuanguo></gold>",
                "icon", Gen.id("lychee"),
                "list", Gen.list(
                        "#yuanguo:fruits",
                        "#yuanguo:crops",
                        "#yuanguo:leaves",
                        "#yuanguo:potted",
                        "#yuanguo:foods",
                        "#yuanguo:drinks",
                        "#yuanguo:jellies",
                        "#yuanguo:feast",
                        "#yuanguo:jam_blocks",
                        "#yuanguo:crates",
                        "#yuanguo:cauldrons",
                        "#yuanguo:equipment")));

        root.put(Gen.id("fruits"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.fruits></gray>",
                "hidden", true,
                "icon", Gen.id("lychee"),
                "list", fruitItems()));

        root.put(Gen.id("crops"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.crops></gray>",
                "hidden", true,
                "icon", Gen.id("apple_sapling"),
                "list", cropItems()));

        root.put(Gen.id("leaves"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.leaves></gray>",
                "hidden", true,
                "icon", Gen.id("apple_leaves"),
                "list", leafItems()));

        root.put(Gen.id("potted"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.potted></gray>",
                "hidden", true,
                "icon", Gen.id("potted_apple_sapling"),
                "list", pottedItems()));

        root.put(Gen.id("foods"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.foods></gray>",
                "hidden", true,
                "icon", Gen.id("hawberry_roll"),
                "list", foodItems()));

        root.put(Gen.id("drinks"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.drinks></gray>",
                "hidden", true,
                "icon", Gen.id("orange_juice"),
                "list", drinkItems()));

        root.put(Gen.id("jellies"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.jellies></gray>",
                "hidden", true,
                "icon", Gen.id("apple_jelly"),
                "list", jellyItems()));

        root.put(Gen.id("feast"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.feast></gray>",
                "hidden", true,
                "icon", Gen.id("mangosteen_cake"),
                "list", feastItems()));

        root.put(Gen.id("jam_blocks"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.jam_blocks></gray>",
                "hidden", true,
                "icon", Gen.id("apple_jelly_block"),
                "list", jamBlockItems()));

        root.put(Gen.id("crates"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.crates></gray>",
                "hidden", true,
                "icon", Gen.id("apple_crate"),
                "list", crateItems()));

        root.put(Gen.id("cauldrons"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.cauldrons></gray>",
                "hidden", true,
                "icon", Gen.id("apple_cauldron"),
                "list", cauldronItems()));

        root.put(Gen.id("equipment"), Gen.map(
                "name", "<!i><gray><lang:category.yuanguo.equipment></gray>",
                "hidden", true,
                "icon", Gen.id("durian_helmet"),
                "list", Gen.list(Gen.id("durian_helmet"))));

        Gen.writeYaml("configuration/categories.yml", Gen.map("categories", root));
    }

    /** The fruit items proper. Fruits that are a vanilla item have no entry of their own. */
    private static List<Object> fruitItems() {
        List<Object> list = new ArrayList<>();
        for (String[] fruit : FruitData.FRUITS) {
            if (FruitData.hasFruitItem(fruit[0])) {
                list.add(Gen.id(fruit[0]));
            }
        }
        return list;
    }

    /** Saplings, seeds and the wild plants that start a crop. */
    private static List<Object> cropItems() {
        List<Object> list = new ArrayList<>();
        for (String[] tree : FruitData.TREES) {
            list.add(Gen.id(tree[0].equals("durian") ? "durian_sapling" : tree[0] + "_sapling"));
        }
        list.add(Gen.id("pineapple_sapling"));
        list.add(Gen.id("hamimelon_seeds"));
        list.add(Gen.id("lemon_seeds"));
        list.add(Gen.id("wild_pineapple"));
        list.add(Gen.id("blueberry_bush"));
        return list;
    }

    /** The fruit-bearing leaves, without the decorative pots. */
    private static List<Object> leafItems() {
        List<Object> list = new ArrayList<>();
        for (String[] tree : FruitData.TREES) {
            list.add(Gen.id(tree[0] + "_leaves"));
        }
        return list;
    }

    /** Potted saplings, which are decoration rather than a growing crop. */
    private static List<Object> pottedItems() {
        List<Object> list = new ArrayList<>();
        for (String name : FruitData.POTTED_TREES) {
            list.add(Gen.id("potted_" + name + "_sapling"));
        }
        return list;
    }

    /** Slices, sheets and the other cut produce. */
    private static List<Object> foodItems() {
        List<Object> list = new ArrayList<>();
        for (String[] food : FruitData.FOODS) {
            // The pineapple fried rice is served in a bowl block, so it lives under Feast instead.
            if (!"bowl_of_pineapple_fried_rice".equals(food[0])) {
                list.add(Gen.id(food[0]));
            }
        }
        list.add(Gen.id("jelly_bread"));
        list.add(Gen.id("hamimelon_slice"));
        list.add(Gen.id("pineapple_slice"));
        list.add(Gen.id("durian_shell"));
        return list;
    }

    private static List<Object> drinkItems() {
        List<Object> list = new ArrayList<>();
        for (String[] juice : FruitData.JUICES) {
            list.add(Gen.id(juice[0]));
        }
        return list;
    }

    private static List<Object> jellyItems() {
        List<Object> list = new ArrayList<>();
        for (String[] fruit : FruitData.FRUITS) {
            list.add(Gen.id(fruit[0] + "_jelly"));
            list.add(Gen.id(fruit[0] + "_jello"));
        }
        return list;
    }

    /** The table blocks a meal is served from: the pudding, the cake and the fried rice bowl. */
    private static List<Object> feastItems() {
        return Gen.list(
                Gen.id("fig_pudding"),
                Gen.id("mangosteen_cake"),
                Gen.id("pineapple_fried_rice"),
                Gen.id("bowl_of_pineapple_fried_rice"));
    }

    /** The jam and jello blocks, which are storage rather than a dish. */
    private static List<Object> jamBlockItems() {
        List<Object> list = new ArrayList<>();
        for (String[] fruit : FruitData.FRUITS) {
            list.add(Gen.id(fruit[0] + "_jelly_block"));
            list.add(Gen.id(fruit[0] + "_jello_block"));
        }
        return list;
    }

    private static List<Object> crateItems() {
        List<Object> list = new ArrayList<>();
        for (String name : Items.crateNames()) {
            list.add(Gen.id(name + "_crate"));
        }
        return list;
    }

    private static List<Object> cauldronItems() {
        List<Object> list = new ArrayList<>();
        list.add(Gen.id("lemonade_cauldron"));
        for (String[] fruit : FruitData.FRUITS) {
            list.add(Gen.id(fruit[0] + "_cauldron"));
        }
        return list;
    }
}
