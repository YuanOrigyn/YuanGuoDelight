import java.io.IOException;
import java.util.*;

/** Emits the block configuration files. */
public final class Blocks {

    private Blocks() {
    }

    private static final Map<String, Object> LEAVES = new LinkedHashMap<>();
    private static final Map<String, Object> PLANTS = new LinkedHashMap<>();
    private static final Map<String, Object> MISC = new LinkedHashMap<>();
    private static int LEAF_POOL = 0;

    static void gen() throws IOException {
        trees();
        bushes();
        melonsAndPineapple();
        durian();
        crates();
        jellies();
        cauldrons();
        meals();

        Gen.writeYaml("configuration/blocks/leaves.yml", Gen.map("blocks", LEAVES));
        Gen.writeYaml("configuration/blocks/plants.yml", Gen.map("blocks", PLANTS));
        Gen.writeYaml("configuration/blocks/misc.yml", Gen.map("blocks", MISC));
        Gen.write("configuration/mappings.yml", mappings());
    }

    /**
     * Widens the pools this pack draws from.
     *
     * <p>CraftEngine splits the vanilla block states into groups and lets every content pack borrow
     * from them. A group only holds the states listed in CraftEngine's own {@code mappings.yml}, and
     * several leaf blocks ship commented out there, so the non-tintable leaf pool is smaller than
     * the game actually allows. Declaring the remaining leaves here adds their states to the pool,
     * which is the same thing the Farmer's Delight pack does for campfires, farmland and fences.</p>
     *
     * <p>Only the dry states are added: a borrowed state carries its real properties to the client,
     * and {@code waterlogged=true} would render a water overlay inside our leaf blocks.</p>
     */
    private static String mappings() {
        StringBuilder out = new StringBuilder();
        out.append("# Widens the pools this pack borrows from.\n");
        out.append("#\n");
        out.append("# Chorus plant: the Farmer's Delight pack maps its jug's state to the empty model,\n");
        out.append("# so every chorus state can carry a jam bottle. Only the single state the bottles\n");
        out.append("# share is declared here; the rest are left to the other packs.\n");
        out.append("#\n");
        out.append("# Spruce and birch leaves ship commented out in CraftEngine's own mappings, so their\n");
        out.append("# states belong to no group. Declaring the dry ones adds them to the non-tintable\n");
        out.append("# leaf pool, exactly like the Farmer's Delight pack does for campfires and farmland.\n");
        out.append("block_state_mappings:\n");
        out.append("  #### Chorus Plant ####\n");
        out.append("  ").append(JELLY_CARRIER).append(": ").append(JELLY_CARRIER).append('\n');
        out.append('\n');
        for (String block : new String[] { "spruce_leaves", "birch_leaves" }) {
            for (int distance = 1; distance <= 7; distance++) {
                for (String persistent : new String[] { "true", "false" }) {
                    out.append("  ")
                            .append(block).append("[distance=").append(distance)
                            .append(",persistent=").append(persistent).append(",waterlogged=false]: ")
                            .append(block).append("[distance=7,persistent=true,waterlogged=false]\n");
                }
            }
        }
        return out.toString();
    }
    // ------------------------------------------------------------------ trees

    private static void trees() {
        for (String[] tree : FruitData.TREES) {
            String name = tree[0];
            String season = FruitData.season(FruitData.indexOf(name));
            String fruitItem = fruitItem(name);
            String saplingBlock = "yuanguo:" + saplingName(name);

            Map<String, Object> behavior = Gen.map(
                    "type", "yuanguo:seasonal_fruit_leaves",
                    "season", season,
                    "fruit", fruitItem,
                    "sapling", saplingBlock,
                    "fruit_min", 1,
                    "fruit_max", 2,
                    "ripen_chance", 0.3);
            Map<String, Object> properties = Gen.map("type", Gen.map(
                    "type", "string",
                    "values", Gen.list("leaves", "flowers", "fruits"),
                    "default", "leaves"));
            Map<String, Object> appearances = Gen.map(
                    "leaves", leafAppearance("yuanguo:block/" + name + "_leaves"),
                    "flowers", leafAppearance("yuanguo:block/" + name + "_flowers"),
                    "fruits", leafAppearance("yuanguo:block/" + name + "_fruits"));
            Map<String, Object> variants = Gen.map(
                    "type=leaves", Gen.map("appearance", "leaves"),
                    "type=flowers", Gen.map("appearance", "flowers"),
                    "type=fruits", Gen.map("appearance", "fruits"));

            List<Object> leafDrops = new ArrayList<>();
            leafDrops.add(Gen.map(
                    "type", "item",
                    "item", Gen.id(name + "_leaves"),
                    "conditions", Gen.list(Gen.map(
                            "type", "any_of",
                            "terms", Gen.list(
                                    Gen.map("type", "match_item", "id", "minecraft:shears"),
                                    Gen.map("type", "enchantment", "predicate", "minecraft:silk_touch>=1"))))));
            leafDrops.add(Gen.map(
                    "type", "item",
                    "item", fruitItem,
                    "conditions", Gen.list(Gen.map(
                            "type", "match_block_property",
                            "properties", Gen.map("type", "fruits"))),
                    "functions", Gen.list(Gen.map(
                            "type", "apply_bonus",
                            "enchantment", "minecraft:fortune",
                            "formula", Gen.map("type", "binomial_with_bonus_count",
                                    "extra", 1, "probability", 0.5)))));
            if (!"durian".equals(name)) {
                // The durian's own loot table has no sapling: its seed only comes from the fruit.
                leafDrops.add(Gen.map(
                        "type", "item",
                        "item", saplingBlock,
                        "conditions", Gen.list(Gen.map(
                                "type", "table_bonus",
                                "enchantment", "minecraft:fortune",
                                "chances", Gen.list(0.05, 0.0625, 0.083333333, 0.1)))));
            }
            Map<String, Object> leafLoot = Gen.map("pools", Gen.list(Gen.map(
                    "rolls", 1,
                    "conditions", Gen.list(Gen.map("type", "survives_explosion")),
                    "entries", Gen.list(Gen.map(
                            "type", "alternatives",
                            "children", leafDrops)))));

            LEAVES.put(Gen.id(name + "_leaves"), Gen.map(
                    "loot", leafLoot,
                    "settings", Gen.map(
                            "template", Gen.list("default:settings/leaves"),
                            "overrides", Gen.map(
                                    "item", Gen.id(name + "_leaves"),
                                    "map_color", "plant")),
                    "behavior", Gen.list(behavior),
                    "states", Gen.map(
                            "properties", properties,
                            "appearances", appearances,
                            "variants", variants)));

            String feature = "yuanguo:tree/" + name + "_tree";
            PLANTS.put(saplingBlock, Gen.map(
                    "loot", Gen.map("template", "default:loot_table/self"),
                    "settings", Gen.map(
                            "template", Gen.list("default:settings/sapling"),
                            "overrides", Gen.map(
                                    "item", saplingBlock,
                                    "is_suffocating", false,
                                    "is_view_blocking", false,
                                    "can_occlude", false,
                                    "is_redstone_conductor", false,
                                    "tags", Gen.list("minecraft:mineable/axe", "minecraft:saplings",
                                            "minecraft:sword_efficient"))),
                    "behavior", Gen.list(
                            Gen.map("type", "bush_block",
                                    "bottom_blocks", Gen.list("minecraft:dirt", "minecraft:grass_block",
                                            "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
                                            "minecraft:moss_block", "minecraft:mud", "minecraft:farmland",
                                            "minecraft:mycelium")),
                            Gen.map("type", "yuanguo:seasonal_sapling",
                                    "season", season,
                                    "feature", feature,
                                    "bone_meal_success_chance", 0.45,
                                    "grow_speed", 0.1)),
                    "states", Gen.map(
                            "properties", Gen.map("stage", Gen.map(
                                    "type", "int", "default", 0, "min", 0, "max", 1)),
                            "appearances", Gen.map("sapling",
                                    saplingAppearance("yuanguo:block/" + saplingName(name))),
                            "variants", Gen.map(
                                    "stage=0", Gen.map("appearance", "sapling"),
                                    "stage=1", Gen.map("appearance", "sapling")))));

            PLANTS.put(Gen.id("potted_" + saplingName(name)), Gen.map(
                    "loot", Gen.map("pools", Gen.list(Gen.map(
                            "rolls", 1,
                            "entries", Gen.list(Gen.map("type", "item", "item", saplingBlock)),
                            "functions", Gen.list(Gen.map("type", "set_count", "count", 1))))),
                    "settings", Gen.map(
                            "template", Gen.list("default:sound/stone", "default:hardness/none"),
                            "overrides", Gen.map(
                                    "is_suffocating", false,
                                    "is_view_blocking", false,
                                    "can_occlude", false,
                                    "is_redstone_conductor", false,
                                    "push_reaction", "destroy")),
                    "state", pottedAppearance("yuanguo:block/potted_" + saplingName(name))));
        }
    }

    private static String saplingName(String name) {
        return name + "_sapling";
    }

    static String fruitItem(String name) {
        if (FruitData.hasFruitItem(name)) {
            return Gen.id(name);
        }
        String vanilla = FruitData.vanillaFruitItem(name);
        return vanilla == null ? Gen.id(name) : vanilla;
    }
    // ----------------------------------------------------------------- bushes

    private static void bushes() {
        for (String[] bush : FruitData.BUSHES) {
            String name = bush[0];
            String season = FruitData.season(FruitData.indexOf(name));
            String type = bush[2];
            if ("CROSS".equals(type)) {
                Map<String, Object> bushOverrides = Gen.map(
                        "item", Gen.id(name),
                        "push_reaction", "destroy",
                        "map_color", "plant",
                        "tags", Gen.list("minecraft:sword_efficient", "minecraft:crops"));
                bushOverrides.put("sounds", blockSounds("sweet_berry_bush"));
                PLANTS.put(Gen.id(name + "_bush"), Gen.map(
                        "loot", Gen.map("pools", Gen.list(fruitPool(name, 4))),
                        "settings", Gen.map(
                                "template", Gen.list("default:hardness/none"),
                                "overrides", bushOverrides),
                        "behavior", Gen.list(
                                Gen.map("type", "yuanguo:seasonal_crop",
                                        "season", season, "grow_speed", 0.2, "light_requirement", 9,
                                        "harvest_item", Gen.id(name), "harvest_min", 1, "harvest_max", 2,
                                        "harvest_reset_age", 2),
                                Gen.map("type", "bush_block",
                                        "bottom_blocks", Gen.list("minecraft:grass_block", "minecraft:dirt",
                                                "minecraft:coarse_dirt", "minecraft:podzol",
                                                "minecraft:moss_block", "minecraft:farmland"))),
                        "states", cropStates(4, new String[] {
                                "yuanguo:block/cranberry_bush_0", "yuanguo:block/cranberry_bush_0",
                                "yuanguo:block/cranberry_bush_1", "yuanguo:block/cranberry_bush_2",
                                "yuanguo:block/cranberry_bush_3" })));
            } else if ("BLOCK".equals(type)) {
                Map<String, Object> bushOverrides = Gen.map(
                        "item", Gen.id(name),
                        "push_reaction", "destroy",
                        "map_color", "plant",
                        "is_suffocating", false,
                        "is_view_blocking", false,
                        "can_occlude", false,
                        "is_redstone_conductor", false,
                        "tags", Gen.list("minecraft:mineable/axe", "minecraft:sword_efficient",
                                "minecraft:crops"));
                bushOverrides.put("sounds", blockSounds("azalea"));
                PLANTS.put(Gen.id(name + "_bush"), Gen.map(
                        "loot", Gen.map("pools", Gen.list(
                                bushBlockPool("yuanguo:" + name + "_bush"),
                                fruitPool(name, 4))),
                        "settings", Gen.map(
                                "template", Gen.list("default:hardness/none"),
                                "overrides", bushOverrides),
                        "behavior", Gen.list(
                                Gen.map("type", "yuanguo:seasonal_crop",
                                        "season", season, "grow_speed", 0.2, "light_requirement", 9,
                                        "harvest_item", Gen.id(name), "harvest_min", 1, "harvest_max", 2,
                                        "harvest_reset_age", 2),
                                Gen.map("type", "bush_block",
                                        "bottom_blocks", Gen.list("minecraft:grass_block", "minecraft:dirt",
                                                "minecraft:coarse_dirt", "minecraft:podzol",
                                                "minecraft:moss_block", "minecraft:farmland"))),
                        "states", cropStates(4, new String[] {
                                "yuanguo:block/blueberry_bush_small", "yuanguo:block/blueberry_bush_small",
                                "yuanguo:block/blueberry_bush", "yuanguo:block/blueberry_bush_flowers",
                                "yuanguo:block/blueberry_bush_fruits" })));
            } else {
                Map<String, Object> bushOverrides = Gen.map(
                        "item", Gen.id("lemon_seeds"),
                        "push_reaction", "destroy",
                        "map_color", "plant",
                        "is_suffocating", false,
                        "is_view_blocking", false,
                        "can_occlude", false,
                        "is_redstone_conductor", false,
                        "tags", Gen.list("minecraft:mineable/axe", "minecraft:sword_efficient",
                                "minecraft:crops"));
                bushOverrides.put("sounds", blockSounds("azalea"));
                PLANTS.put(Gen.id("lemon_tree"), Gen.map(
                        "loot", Gen.map("pools", Gen.list(Gen.map(
                                "rolls", 1,
                                "conditions", Gen.list(Gen.map(
                                        "type", "match_block_property",
                                        "properties", Gen.map("half", "lower"))),
                                "entries", Gen.list(Gen.map(
                                        "type", "item",
                                        "item", Gen.id("lemon"),
                                        "conditions", Gen.list(Gen.map(
                                                "type", "match_block_property",
                                                "properties", Gen.map("age", 4))),
                                        "functions", Gen.list(Gen.map(
                                                "type", "apply_bonus",
                                                "enchantment", "minecraft:fortune",
                                                "formula", Gen.map("type", "binomial_with_bonus_count", "extra", 1, "probability", 0.5)))))))),
                        "settings", Gen.map(
                                "template", Gen.list("default:hardness/none"),
                                "overrides", bushOverrides),
                        "behavior", Gen.list(
                                Gen.map("type", "yuanguo:seasonal_crop",
                                        "season", season, "grow_speed", 0.2, "light_requirement", 9,
                                        "harvest_item", Gen.id("lemon"), "harvest_min", 1, "harvest_max", 2,
                                        "harvest_reset_age", 2, "double_high_from", 2),
                                Gen.map("type", "bush_block",
                                        "stackable", true, "max_height", 2,
                                        "bottom_blocks", Gen.list("minecraft:grass_block", "minecraft:dirt",
                                                "minecraft:coarse_dirt", "minecraft:podzol",
                                                "minecraft:moss_block", "minecraft:farmland")),
                                Gen.map("type", "double_high_block")),
                        "states", tallCropStates("lemon_tree", 4)));
            }
        }
    }
    // ------------------------------------------------------- melon/pineapple

    private static void melonsAndPineapple() {
        PLANTS.put(Gen.id("pineapple"), Gen.map(
                "loot", Gen.map("pools", Gen.list(Gen.map(
                        "rolls", 1,
                        "entries", Gen.list(Gen.map(
                                "type", "alternatives",
                                "children", Gen.list(
                                        Gen.map(
                                                "type", "item",
                                                "item", Gen.id("pineapple"),
                                                "conditions", Gen.list(Gen.map(
                                                        "type", "match_block_property",
                                                        "properties", Gen.map("age", 4)))),
                                        Gen.map(
                                                "type", "item",
                                                "item", Gen.id("pineapple_sapling"),
                                                "functions", Gen.list(Gen.map(
                                                        "type", "explosion_decay"))))))))),
                "settings", Gen.map(
                        "template", Gen.list("default:sound/crop", "default:hardness/none"),
                        "overrides", Gen.map(
                                "item", Gen.id("pineapple_sapling"),
                                "push_reaction", "destroy",
                                "map_color", "plant",
                                "tags", Gen.list("minecraft:crops"))),
                "behavior", Gen.list(
                        Gen.map("type", "yuanguo:seasonal_crop",
                                "season", "SUMMER", "grow_speed", 0.2, "light_requirement", 9,
                                "harvest_item", Gen.id("pineapple"), "harvest_min", 1, "harvest_max", 1,
                                "harvest_remove", true),
                        Gen.map("type", "bush_block",
                                "bottom_blocks", Gen.list("minecraft:sand", "minecraft:red_sand",
                                        "minecraft:coarse_dirt"))),
                "states", cropStates(4, new String[] {
                        "yuanguo:block/pineapple_stage_0", "yuanguo:block/pineapple_stage_1",
                        "yuanguo:block/pineapple_stage_2", "yuanguo:block/pineapple_stage_3",
                        "yuanguo:block/pineapple_stage_4" })));

        PLANTS.put(Gen.id("wild_pineapple"), Gen.map(
                "loot", Gen.map("pools", Gen.list(Gen.map(
                        "rolls", 1,
                        "entries", Gen.list(Gen.map(
                                "type", "alternatives",
                                "children", Gen.list(
                                        Gen.map(
                                                "type", "item",
                                                "item", Gen.id("wild_pineapple"),
                                                "conditions", Gen.list(Gen.map(
                                                        "type", "enchantment",
                                                        "predicate", "minecraft:silk_touch>=1"))),
                                        Gen.map(
                                                "type", "item",
                                                "item", Gen.id("pineapple"),
                                                "functions", Gen.list(Gen.map(
                                                        "type", "explosion_decay"))))))))),
                "settings", Gen.map(
                        "template", Gen.list("default:sound/grass", "default:hardness/none"),
                        "overrides", Gen.map(
                                "item", Gen.id("wild_pineapple"),
                                "push_reaction", "destroy",
                                "map_color", "plant",
                                "tags", Gen.list("minecraft:sword_efficient"))),
                "behavior", Gen.map("type", "bush_block",
                        "bottom_blocks", Gen.list("minecraft:sand", "minecraft:red_sand",
                                "minecraft:coarse_dirt")),
                "state", thinAppearance("yuanguo:block/pineapple_wild")));

        Map<String, Object> silkTouch = Gen.map(
                "type", "item",
                "item", Gen.id("hamimelon"),
                "conditions", Gen.list(Gen.map(
                        "type", "enchantment",
                        "predicate", "minecraft:silk_touch>=1")));
        Map<String, Object> sliceDrop = Gen.map(
                "type", "item",
                "item", Gen.id("hamimelon_slice"),
                "functions", Gen.list(
                        Gen.map("type", "set_count", "count", "3~7", "add", false),
                        Gen.map("type", "apply_bonus",
                                "enchantment", "minecraft:fortune",
                                "formula", Gen.map("type", "ore_drops")),
                        Gen.map("type", "limit_count", "max", 9),
                        Gen.map("type", "explosion_decay")));
        Map<String, Object> melonPool = Gen.map(
                "rolls", 1,
                "entries", Gen.list(Gen.map(
                        "type", "alternatives",
                        "children", Gen.list(silkTouch, sliceDrop))));
        MISC.put(Gen.id("hamimelon"), Gen.map(
                "loot", Gen.map("pools", Gen.list(melonPool)),
                "settings", Gen.map(
                        "template", Gen.list("default:sound/wood", "default:hardness/melon"),
                        "overrides", Gen.map(
                                "item", Gen.id("hamimelon"),
                                "map_color", "plant",
                                "push_reaction", "destroy",
                                "tags", Gen.list("minecraft:enderman_holdable", "minecraft:mineable/axe",
                                        "minecraft:sword_efficient"))),
                "state", appearance("yuanguo:block/hamimelon")));
        MISC.put(Gen.id("hamimelon_stem"), Gen.map(
                "loot", Gen.map("template", "default:loot_table/crop",
                        "arguments", Gen.map("crop_item", Gen.id("hamimelon_seeds"), "ripe_age", 7)),
                "settings", Gen.map(
                        "template", Gen.list("default:sound/stem_crop", "default:hardness/none"),
                        "overrides", Gen.map(
                                "item", Gen.id("hamimelon_seeds"),
                                "push_reaction", "destroy",
                                "is_suffocating", false,
                                "is_view_blocking", false,
                                "can_occlude", false,
                                "is_redstone_conductor", false,
                                "map_color", "plant",
                                "tags", Gen.list("minecraft:crops", "minecraft:maintains_farmland"))),
                "behavior", Gen.list(
                        Gen.map("type", "yuanguo:seasonal_stem",
                                "season", "SUMMER",
                                "fruit", Gen.id("hamimelon"),
                                "attached_stem", Gen.id("attached_hamimelon_stem"),
                                "light_requirement", 9),
                        Gen.map("type", "bush_block",
                                "bottom_blocks", Gen.list("minecraft:farmland"))),
                "states", cropStates(7, new String[] {
                        "yuanguo:block/hamimelon_stem_stage0", "yuanguo:block/hamimelon_stem_stage1",
                        "yuanguo:block/hamimelon_stem_stage2", "yuanguo:block/hamimelon_stem_stage3",
                        "yuanguo:block/hamimelon_stem_stage4", "yuanguo:block/hamimelon_stem_stage5",
                        "yuanguo:block/hamimelon_stem_stage6", "yuanguo:block/hamimelon_stem_stage7" },
                        "higher_tripwire")));

        MISC.put(Gen.id("attached_hamimelon_stem"), Gen.map(
                "loot", Gen.map("template", "default:loot_table/crop",
                        "arguments", Gen.map("crop_item", Gen.id("hamimelon_seeds"), "ripe_age", 7)),
                "settings", Gen.map(
                        "template", Gen.list("default:sound/wood", "default:hardness/none"),
                        "overrides", Gen.map(
                                "item", Gen.id("hamimelon_seeds"),
                                "push_reaction", "destroy",
                                "is_suffocating", false,
                                "is_view_blocking", false,
                                "can_occlude", false,
                                "is_redstone_conductor", false,
                                "map_color", "plant",
                                "tags", Gen.list("minecraft:crops", "minecraft:maintains_farmland"))),
                "behavior", Gen.list(
                        Gen.map("type", "attached_stem_block",
                                "fruit", Gen.id("hamimelon"),
                                "stem", Gen.id("hamimelon_stem")),
                        Gen.map("type", "bush_block",
                                "bottom_blocks", Gen.list("minecraft:farmland"))),
                "states", Gen.map(
                        "properties", Gen.map("facing", Gen.map(
                                "type", "horizontal_direction", "default", "north")),
                        "appearances", Gen.map(
                                "facing=north", Gen.map(
                                        "state", "attached_melon_stem[facing=north]",
                                        "model", Gen.map("path", "yuanguo:block/attached_hamimelon_stem",
                                                "y", 90)),
                                "facing=east", Gen.map(
                                        "state", "attached_melon_stem[facing=east]",
                                        "model", Gen.map("path", "yuanguo:block/attached_hamimelon_stem",
                                                "y", 180)),
                                "facing=south", Gen.map(
                                        "state", "attached_melon_stem[facing=south]",
                                        "model", Gen.map("path", "yuanguo:block/attached_hamimelon_stem",
                                                "y", 270)),
                                "facing=west", Gen.map(
                                        "state", "attached_melon_stem[facing=west]",
                                        "model", Gen.map("path", "yuanguo:block/attached_hamimelon_stem"))),
                        "variants", Gen.map(
                                "facing=north", Gen.map("appearance", "facing=north"),
                                "facing=east", Gen.map("appearance", "facing=east"),
                                "facing=south", Gen.map("appearance", "facing=south"),
                                "facing=west", Gen.map("appearance", "facing=west")))));
    }
    // ----------------------------------------------------------------- durian

    private static void durian() {
        MISC.put(Gen.id("durian"), Gen.map(
                "loot", Gen.map("template", "default:loot_table/self"),
                "settings", Gen.map(
                        "template", Gen.list("default:sound/wood", "default:hardness/wood"),
                        "overrides", Gen.map(
                                "item", Gen.id("durian"),
                                "map_color", "plant",
                                "is_suffocating", true,
                                "tags", Gen.list("minecraft:mineable/axe"))),
                "behavior", Gen.map(
                        "type", "falling_block",
                        "hurt_amount", 2.0,
                        "max_hurt", 40),
                "state", appearance("yuanguo:block/durian", SOLID_CARRIER)));
    }

    // ----------------------------------------------------------------- crates

    private static void crates() {
        List<String> names = new ArrayList<>();
        for (String[] tree : FruitData.TREES) {
            if (!"durian".equals(tree[0])) {
                names.add(tree[0]);
            }
        }
        for (String[] bush : FruitData.BUSHES) {
            names.add(bush[0]);
        }
        names.add("pineapple");
        names.add("apple");
        names = new ArrayList<>(new LinkedHashSet<>(names));

        for (String name : names) {
            MISC.put(Gen.id(name + "_crate"), Gen.map(
                    "loot", Gen.map("template", "default:loot_table/self"),
                    "settings", Gen.map(
                            "template", Gen.list("default:sound/wood", "default:hardness/planks",
                                    "default:burn_data/planks"),
                            "overrides", Gen.map(
                                    "item", Gen.id(name + "_crate"),
                                    "instrument", "bass",
                                    "map_color", "wood",
                                    "is_suffocating", true,
                                    "is_redstone_conductor", true,
                                    "tags", Gen.list("minecraft:mineable/axe"))),
                    "state", appearance("yuanguo:block/" + name + "_crate")));
        }
    }

    // ---------------------------------------------------------------- jellies

    private static void jellies() {
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            MISC.put(Gen.id(name + "_jelly"), jellyBlock(name, "_jelly", "default:sound/glass",
                    "default:hardness/glass", "plant"));
            MISC.put(Gen.id(name + "_jelly_block"), jellyBlock(name, "_jelly_block", "default:sound/grass",
                    "default:hardness/none", "plant"));
            MISC.put(Gen.id(name + "_jello_block"), jellyBlock(name, "_jello_block", "default:sound/grass",
                    "default:hardness/none", "plant"));
        }
    }

    private static Map<String, Object> jellyBlock(String name, String suffix, String sound,
                                                  String hardness, String mapColor) {
        Map<String, Object> overrides = Gen.map(
                "item", Gen.id(name + suffix),
                "is_suffocating", false,
                "is_view_blocking", false,
                "can_occlude", false,
                "is_redstone_conductor", false,
                "push_reaction", "destroy",
                "map_color", mapColor);
        if (suffix.equals("_jello_block")) {
            overrides.put("bounce_restitution", 1.0);
        }
        if (suffix.equals("_jelly_block")) {
            overrides.put("sounds", blockSounds("honey_block"));
        } else if (suffix.equals("_jello_block")) {
            overrides.put("sounds", blockSounds("slime_block"));
        }
        boolean customSound = suffix.equals("_jelly_block") || suffix.equals("_jello_block");
        Map<String, Object> block = Gen.map(
                "loot", Gen.map("template", "default:loot_table/self"),
                "settings", Gen.map(
                        "template", customSound
                                ? Gen.list(hardness)
                                : Gen.list(sound, hardness),
                        "overrides", overrides));
        if (suffix.equals("_jello_block")) {
            block.put("behavior", Gen.map(
                    "type", "bouncing_block",
                    "bounce_height", 1.0,
                    "sync_player_position", true,
                    "fall_damage_multiplier", 0.0));
        } else if (suffix.equals("_jelly_block")) {
            block.put("behavior", Gen.map(
                    "type", "yuanguo:jelly_surface",
                    "fall_damage_multiplier", 0.2,
                    "slide_multiplier", 0.4,
                    "effect_duration", 10));
        }
        block.put("state", suffix.equals("_jelly") ? jellyAppearance(name)
                : appearance("yuanguo:block/" + name + suffix, SOLID_CARRIER));
        return block;
    }

    private static Map<String, Object> blockSounds(String blockType) {
        return Gen.map(
                "break", "minecraft:block." + blockType + ".break",
                "step", "minecraft:block." + blockType + ".step",
                "place", "minecraft:block." + blockType + ".place",
                "hit", "minecraft:block." + blockType + ".hit",
                "fall", "minecraft:block." + blockType + ".fall");
    }
    // --------------------------------------------------------------- cauldrons

    /**
     * One cauldron block per fruit, with the liquid itself drawn by a display entity.
     *
     * <p>The mod has four cauldron shapes (lemonade, juice, jam, jello) times 22 fruits. The old
     * layout needed three blocks per fruit because every shape had its own block id. Here a block
     * only keeps the parts the game has to know about: the fruit, the kind and the level. The
     * surface is an {@code item_display} whose model is chosen from the kind and level, so a level
     * change swaps one entity model instead of a whole block state.</p>
     *
     * <p>The existing resource pack already has twelve juice textures per fruit plus the three jam
     * and three jello stages, so those models are reused as-is instead of regenerating textures.</p>
     */
    private static void cauldrons() {
        // Lemonade is a single full cauldron: water plus a lemon slice, 30% lemon over white.
        MISC.put(Gen.id("lemonade_cauldron"), Gen.map(
                "loot", cauldronLoot(),
                "settings", cauldronSettings(),
                "behavior", cauldronBehavior("lemonade"),
                "state", cauldronAppearance("lemonade_cauldron")));

        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];

            // Juice holds 1..12 doses; jam and jello hold 1..3. All three share one block per fruit
            // and are told apart by the "kind" property, which keeps the block count at 23.
            MISC.put(Gen.id(name + "_cauldron"), Gen.map(
                    "loot", cauldronLoot(),
                    "settings", cauldronSettings(),
                    "behavior", cauldronBehavior(name),
                    "states", cauldronStates(name)));
        }
    }

    /**
     * The states of one fruit cauldron. Juice has one appearance per level, jam and jello have one
     * per stage. The state space is the full 3 x 12 matrix, so the jam and jello levels above three
     * are mapped back to their full stage instead of leaving missing variants behind.
     */
    private static Map<String, Object> cauldronStates(String fruit) {
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();

        for (int level = 1; level <= 12; level++) {
            String appearance = "juice_" + level;
            appearances.put(appearance, cauldronAppearance(fruit + "_cauldron_juice_" + level));
            variants.put("kind=juice,level=" + level, Gen.map("appearance", appearance));
        }
        for (String kind : new String[] { "jelly", "jello" }) {
            for (int level = 1; level <= 3; level++) {
                String appearance = kind + "_" + level;
                appearances.put(appearance, cauldronAppearance(fruit + "_cauldron_" + kind + "_" + level));
                variants.put("kind=" + kind + ",level=" + level, Gen.map("appearance", appearance));
            }
            for (int level = 4; level <= 12; level++) {
                variants.put("kind=" + kind + ",level=" + level,
                        Gen.map("appearance", kind + "_3"));
            }
        }
        return Gen.map(
                "properties", Gen.map(
                        "kind", Gen.map(
                                "type", "string",
                                "values", Gen.list("juice", "jelly", "jello"),
                                "default", "juice"),
                        "level", Gen.map(
                                "type", "int", "default", 12, "min", 1, "max", 12)),
                "appearances", appearances,
                "variants", variants);
    }

    /**
     * One cauldron appearance: the vanilla cauldron renders the body, while a bright display entity
     * draws only the liquid. The fixed cauldron state keeps the real collision shape and avoids
     * culling the faces of neighbouring full-cube blocks.
     */
    private static Map<String, Object> cauldronAppearance(String renderer) {
        return Gen.map(
                "state", "minecraft:cauldron",
                "model", Gen.map("path", "yuanguo:block/cauldron_body"),
                "entity_renderer", Gen.map(
                        "type", "item_display",
                        "item", "yuanguo:entity_renderer/" + renderer,
                        "rotation", 0,
                        "brightness", Gen.map(
                                "block_light", 15,
                                "sky_light", 15)));
    }

    private static Map<String, Object> cauldronLoot() {
        return Gen.map("template", "default:loot_table/basic",
                "arguments", Gen.map("item", "minecraft:cauldron", "count", 1));
    }

    /**
     * The whole cauldron chain is implemented by the plugin, because a data pack cannot express it:
     * the recipes mix a block state with an item, and CraftEngine only exposes item in and item out.
     * {@code liquid} names the jam fluid the cauldron holds, which is also what tells the behavior
     * which fruit a cauldron belongs to.
     */
    private static Map<String, Object> cauldronBehavior(String liquid) {
        return Gen.map("type", "yuanguo:cauldron", "liquid", liquid);
    }

    /** A fresh settings map per block keeps the generated YAML free of aliases. */
    private static Map<String, Object> cauldronSettings() {
        return Gen.map(
                "template", Gen.list("default:sound/stone"),
                "overrides", Gen.map(
                        "hardness", 2,
                        "resistance", 2,
                        "is_suffocating", false,
                        "is_view_blocking", false,
                        "can_occlude", false,
                        "is_redstone_conductor", false,
                        "push_reaction", "destroy",
                        "map_color", "stone"));
    }
    // ------------------------------------------------------------------ meals

    /**
     * The three meals the mod places as a block and eats from.
     *
     * <p>All three start at their fullest state and are emptied one serving at a time, exactly like
     * the mod: {@code bites} / {@code servings} counts what is left, so the placed block borrows the
     * mod's own {@code max} as its default. The loot tables are the mod's: the dish only comes back
     * when it is still untouched, and a partly eaten one leaves its bowl behind.</p>
     */
    private static void meals() {
        MISC.put(Gen.id("fig_pudding"), servingBlock("fig_pudding", "bites", 4,
                "wool", false,
                Gen.map("slice_item", Gen.id("fig_pudding_slice"), "slice", true,
                        "eat_food", 3, "eat_saturation", 0.4,
                        "effect_item", Gen.id("fig_pudding_slice")),
                selfOnlyLoot("bites", 3, "fig_pudding")));
        MISC.put(Gen.id("mangosteen_cake"), servingBlock("mangosteen_cake", "bites", 4,
                "wool", false,
                Gen.map("eat_food", 3, "eat_saturation", 0.6,
                        "effect_item", Gen.id("mangosteen_cake")),
                bowlLoot("bites", 3, "mangosteen_cake")));
        MISC.put(Gen.id("pineapple_fried_rice"), servingBlock("pineapple_fried_rice", "servings", 5,
                "wool", true,
                Gen.map("take_with_bowl", true, "serving_item", Gen.id("bowl_of_pineapple_fried_rice"),
                        "keep_empty", true, "eat_food", 14, "eat_saturation", 0.8,
                        "effect_item", Gen.id("bowl_of_pineapple_fried_rice")),
                bowlLoot("servings", 4, "pineapple_fried_rice")));
    }

    /** The mod's dish loot table: the dish drops only while it is still full. */
    private static Map<String, Object> selfOnlyLoot(String property, int full, String id) {
        return Gen.map("pools", Gen.list(Gen.map(
                "rolls", 1,
                "conditions", Gen.list(
                        Gen.map("type", "survives_explosion"),
                        Gen.map("type", "match_block_property",
                                "properties", Gen.map(property, full))),
                "entries", Gen.list(Gen.map("type", "item", "item", Gen.id(id))))));
    }

    /** The mod's feast loot table: the dish while full, its bowl once a serving was taken. */
    private static Map<String, Object> bowlLoot(String property, int full, String id) {
        return Gen.map("pools", Gen.list(
                Gen.map(
                        "rolls", 1,
                        "conditions", Gen.list(
                                Gen.map("type", "survives_explosion"),
                                Gen.map("type", "match_block_property",
                                        "properties", Gen.map(property, full))),
                        "entries", Gen.list(Gen.map("type", "item", "item", Gen.id(id)))),
                Gen.map(
                        "rolls", 1,
                        "conditions", Gen.list(
                                Gen.map("type", "survives_explosion"),
                                Gen.map("type", "inverted", "term", Gen.map(
                                        "type", "match_block_property",
                                        "properties", Gen.map(property, full)))),
                        "entries", Gen.list(Gen.map("type", "item", "item", "minecraft:bowl")))));
    }

    private static Map<String, Object> servingBlock(String id, String property, int servings,
                                                    String sound, boolean reversed,
                                                    Map<String, Object> behavior,
                                                    Map<String, Object> loot) {
        Map<String, Object> properties = Gen.map(
                property, Gen.map("type", "int", "default", servings - 1, "min", 0, "max", servings - 1),
                "facing", Gen.map("type", "horizontal_direction", "default", "north"));
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int value = 0; value < servings; value++) {
            String model;
            if (reversed) {
                model = value == servings - 1 ? id : id + "_" + (servings - 1 - value);
            } else {
                model = id + "_" + value;
            }
            // The Farmer's Delight pack draws every feast with an item display entity riding a
            // transparent, non-full carrier instead of a note block: the campfire box is not a full
            // cube, neighbours keep their faces, and the facing becomes an entity rotation rather
            // than another borrowed block state.
            for (String facing : new String[] { "north", "east", "south", "west" }) {
                String appearanceKey = facing + "_" + property + value;
                appearances.put(appearanceKey, feastAppearance(model, facing, value == 0));
                String key = property + "=" + value + ",facing=" + facing;
                variants.put(key, Gen.map("appearance", appearanceKey));
            }
        }
        boolean templateSound = sound.startsWith("default:");
        Map<String, Object> overrides = Gen.map(
                "item", Gen.id(id),
                "is_suffocating", false,
                "is_view_blocking", false,
                "can_occlude", false,
                "is_redstone_conductor", false,
                "push_reaction", "destroy");
        if (!templateSound) {
            overrides.put("sounds", blockSounds(sound));
        }
        Map<String, Object> block = Gen.map(
                "loot", loot,
                "settings", Gen.map(
                        "template", templateSound
                                ? Gen.list(sound, "default:hardness/none")
                                : Gen.list("default:hardness/none"),
                        "overrides", overrides),
                "states", Gen.map(
                        "properties", properties,
                        "appearances", appearances,
                        "variants", variants));
        behavior.put("type", "yuanguo:serving_block");
        behavior.put("property", property);
        block.put("behavior", behavior);
        return block;
    }
    /**
     * A placed jelly is a bottle, not a block, so it borrows the empty {@code chorus_plant} state the
     * Farmer's Delight pack uses for its jugs and renders through an item display entity. The vanilla
     * state is invisible, never waterlogged and does not occlude neighbours, so the bottle neither
     * culls the faces of the block behind it nor shows a water overlay.
     */
    static Map<String, Object> jellyAppearance(String name) {
        return Gen.map(
                "state", JELLY_CARRIER,
                "transparent", true,
                "entity_renderer", Gen.map(
                        "type", "item_display",
                        "item", Gen.id("entity_renderer/" + name + "_jelly"),
                        "rotation", 0));
    }

    /** Carrier pair the placed feasts ride on, copied from the Farmer's Delight pack. */
    private static final String FEAST_CARRIER =
            "soul_campfire[facing=east,lit=false,signal_fire=true,waterlogged=false]";
    private static final String FEAST_EMPTY_CARRIER =
            "iron_trapdoor[facing=north,half=bottom,open=false,powered=true,waterlogged=false]";

    /**
     * A placed feast the way the Farmer's Delight pack renders its roasts and pies: a transparent
     * borrowed state plus an item display holding the block model. The soul campfire is a partial
     * cube that neither walls off the floor nor culls the faces of its neighbours, and the empty
     * stage swaps to the flat trapdoor the FD pack uses for leftovers. The rotation mirrors the
     * mod's blockstate turns, where north is the model's base direction.
     */
    static Map<String, Object> feastAppearance(String model, String facing, boolean empty) {
        int rotation = switch (facing) {
            case "north" -> 180;
            case "east" -> -90;
            case "south" -> 0;
            default -> 90;
        };
        return Gen.map(
                "state", empty ? FEAST_EMPTY_CARRIER : FEAST_CARRIER,
                "transparent", true,
                "entity_renderer", Gen.map(
                        "type", "item_display",
                        "item", Gen.id("entity_renderer/" + model),
                        "rotation", rotation));
    }
    /**
     * The empty chorus plant state every jam bottle sits on.
     *
     * <p>The mod draws a bottle in a 3..13 x 0..15 x 3..13 box. A borrowed vanilla state carries its
     * own collision and outline shape to the client, and that shape is what the client highlights and
     * raycasts, so the bottle only looks right when the carrier matches. Chorus plant states have
     * sixty-four different boxes; exactly one of them, the fully grown column, is 3..13 x 0..16 x
     * 3..13, which is the shape the mod's bottle uses. Sharing that one state between all the bottles
     * is what makes them render and interact identically.</p>
     *
     * <p>The state itself renders the empty model the Farmer's Delight pack maps its jug to, so the
     * borrowed block is invisible and only the item display shows.</p>
     */
    private static final String JELLY_CARRIER =
            "chorus_plant[down=true,east=false,north=false,south=false,up=true,west=false]";
    /**
     * The carrier for our full-cube models.
     *
     * <p>CraftEngine's {@code solid} group is not just note blocks: it also contains the three
     * mushroom blocks, whose states are shared with the {@code mushroom} group the Brewin' and
     * Chewin' pack draws from. Our hundreds of crates and cauldrons drained that pool and left the
     * other pack with no mushroom state at all. {@code note_block} alone has 1300 slots and nobody
     * else needs more than a handful, so every full cube takes a note block state instead.</p>
     */
    private static final String SOLID_CARRIER = "note_block";

    // ---------------------------------------------------------------- helpers

    static Map<String, Object> appearance(String model) {
        return appearance(model, SOLID_CARRIER);
    }

    /**
     * Non-occluding states, in the order they are handed out.
     *
     * <p>An appearance borrows a real vanilla block state, and the {@code solid} / {@code mushroom}
     * groups are full cubes whose occlusion shape makes the client cull the touching faces of
     * neighbouring blocks. Thin models therefore have to sit on one of these groups instead.</p>
     *
     * <p>Every group is a server-wide pool shared with the other packs, so the quotas below only
     * draw what is left after CraftEngine's own assets and the Farmer's Delight pack. {@code
     * sugar_cane} is deliberately unused: those fifteen states are already taken by the other packs,
     * which is what made CraftEngine report the exhausted group.</p>
     */
    private static final Map<String, Integer> THIN_BUDGET = thinBudget();

    private static final Map<String, Integer> THIN_USED = new LinkedHashMap<>();

    /**
     * How many states of each non-occluding group this pack may still take.
     *
     * <p>The numbers are the groups' capacities minus what CraftEngine's own assets and the
     * Farmer's Delight pack already hold, measured from CraftEngine's allocation cache. They are
     * deliberately below the exact remainder so that a small change on the other packs cannot
     * push us over. The {@code tripwire} pool is not listed separately: it is the union of the two
     * tripwire groups, whose capacities already add up to it.</p>
     */
    /**
     * The pool a hand-picked or broken fruit bush drops when its fruit is ripe.
     *
     * <p>The mod's bush tables contain only this one pool: the bush itself is never dropped, so
     * breaking a ripe bush yields the fruit (with the fortune bonus), and picking it by hand
     * yields the same fruit.</p>
     */
    private static Map<String, Object> fruitPool(String name, int ripeAge) {
        return Gen.map("rolls", 1,
                "conditions", Gen.list(
                        Gen.map("type", "survives_explosion"),
                        Gen.map("type", "match_block_property",
                                "properties", Gen.map("age", ripeAge))),
                "entries", Gen.list(Gen.map(
                        "type", "item",
                        "item", Gen.id(name),
                        "functions", Gen.list(Gen.map(
                                "type", "apply_bonus",
                                "enchantment", "minecraft:fortune",
                                "formula", Gen.map("type", "binomial_with_bonus_count",
                                        "extra", 1, "probability", 0.5))))));
    }

    /**
     * The blueberry bush's own drop: the bush block, but only above the two seedling ages.
     *
     * <p>The mod gates this on a silk-touch tool and on the age being 2 or higher; a seedling
     * yields nothing but the fruit pool stays empty too, so breaking one gives nothing.</p>
     */
    private static Map<String, Object> bushBlockPool(String block) {
        return Gen.map("rolls", 1,
                "conditions", Gen.list(
                        Gen.map("type", "survives_explosion"),
                        Gen.map("type", "enchantment", "predicate", "minecraft:silk_touch>=1"),
                        Gen.map("type", "!match_block_property",
                                "properties", Gen.map("age", 0)),
                        Gen.map("type", "!match_block_property",
                                "properties", Gen.map("age", 1))),
                "entries", Gen.list(Gen.map("type", "item", "item", block)));
    }
    /**
     * The pools a thin block may borrow from, and how many states each can give.
     *
     * <p>Only groups whose outline is worth clicking are listed. A borrowed state carries its real
     * vanilla outline to the client, and that outline is what the client raycasts against, so the
     * choice decides how hard the block is to interact with:</p>
     *
     * <ul>
     *   <li>{@code higher_tripwire} is {@code tripwire[attached=false]}: a full width 16x8x16 box
     *       sitting on the floor, the most forgiving of them all.</li>
     *   <li>{@code cave_vine} is 14x16x14, {@code kelp} is 16x9x16 and {@code twisting_vine} is
     *       8x15x8: all bottom anchored and tall enough to aim at.</li>
     *   <li>{@code cactus} is 14x16x14, the same shape as {@code cave_vine} but drawn from a second
     *       block, so it doubles the pool the 14x16x14 boxes can come from.</li>
     * </ul>
     *
     * <p>The numbers are not the group sizes: CraftEngine pools these borrowed states across every
     * content pack, and the official Farmer's Delight pack already claims 23 of the 63
     * {@code higher_tripwire} slots and 13 of the 15 {@code sugar_cane} slots. A budget that ignored
     * that would overflow the group and leave the tail of our blocks with no state at all, which is
     * exactly what the {@code 视觉状态组...已达到最大容量} warnings are. Each figure below is the
     * group's size minus what Farmer's Delight takes, so the two packs fit side by side.</p>
     *
     * <p>{@code lower_tripwire} ({@code tripwire[attached=true]}, a 16x1.5x16 sliver),
     * {@code weeping_vine} (8x7x8 hung from the ceiling of the block) and {@code pressure_plate}
     * (14x1x14) are deliberately absent. They are bottom-anchored-but-flat or top-anchored, which
     * would leave a placed dish or crop with almost nothing to click. Only
     * {@link #pottedAppearance} still borrows the sliver, where a small box suits the small pot.</p>
     */
    private static Map<String, Integer> thinBudget() {
        Map<String, Integer> budget = new LinkedHashMap<>();
        // lower_tripwire first: a 16x16 flat pad on the floor that never blocks walking, exactly
        // what the Farmer's Delight pack gives its crops. note_block (1300 slots, ~700 free) is
        // the spill-over pool, so a full first pool can never leave a block unregistered.
        budget.put("lower_tripwire", 48);
        budget.put("note_block", 700);
        return budget;
    }

    /** Picks the next non-occluding state for a model that is not a full cube. */
    static Map<String, Object> thinAppearance(String model) {
        return appearance(model, nextThinState());
    }

    /**
     * Hands out the group with the most headroom left, so no pool is ever exhausted. The borrowed
     * state is invisible; only its occlusion shape reaches the client, which is why any of these
     * groups can carry any of our thin models.
     */
    private static String nextThinState() {
        // Declaration order is the priority: the flat tripwire pad is handed out first and the
        // oversized note block pool absorbs everything after it. If both budgets run out the
        // note block carrier is returned anyway - losing a state means CraftEngine skips the
        // whole block ("Failed to place unknown block"), which is far worse than a full cube.
        for (Map.Entry<String, Integer> entry : THIN_BUDGET.entrySet()) {
            int used = THIN_USED.getOrDefault(entry.getKey(), 0);
            if (used < entry.getValue()) {
                THIN_USED.put(entry.getKey(), used + 1);
                return entry.getKey();
            }
        }
        return SOLID_CARRIER;
    }

    /** CraftEngine 26.x rejects a bare model: every appearance needs an auto state. */
    static Map<String, Object> appearance(String model, String autoState) {
        return Gen.map("auto_state", autoState, "model", Gen.map("path", model));
    }

    /**
     * A sapling on a large interaction box.
     *
     * <p>A cross model only answers to clicks inside its own thin geometry, so a bone meal or a
     * sapling in hand barely ever connects. Vanilla's {@code tripwire[attached=false]} state has a
     * 16x16x8 outline and no collision at all, which gives the sapling a whole half-block of
     * clickable space while still letting the player walk through it. This is the same trick the
     * Farmer's Delight pack uses for its crops.</p>
     */
    static Map<String, Object> saplingAppearance(String model) {
        return thinAppearance(model);
    }

    /**
     * A potted sapling gets a generous outline too.
     *
     * <p>{@code lower_tripwire} would be the obvious pick for something small, but it is a 16x1.5x16
     * sliver: aiming at a pot from the side would miss it entirely. The saplings take the whole
     * {@code higher_tripwire} pool, so the pots are spread over the remaining tall groups, all of
     * which are bottom anchored and comfortably larger than the pot itself.</p>
     */
    static Map<String, Object> pottedAppearance(String model) {
        return appearance(model, "note_block");
    }

    /**
     * Leaves render like vanilla leaves, so they need a matching auto state.
     *
     * <p>Our leaf textures are already coloured, so only the non-tintable pools may be used: the
     * tintable ones would have the biome colour multiplied over them.</p>
     *
     * <p>The waterlogged pool is deliberately avoided. A borrowed state carries its real vanilla
     * properties to the client, and a {@code waterlogged=true} leaves state renders a water overlay
     * inside the block, which is what put water inside the trees. Only the dry pool is used, and the
     * 39 appearances are spread over its four leaf blocks.</p>
     */
    static Map<String, Object> leafAppearance(String model) {
        return appearance(model, "non_tintable_leaves");
    }

    /** age -> model, with a per-age auto state. */
    private static Map<String, Object> cropStates(int maxAge, String[] models) {
        return cropStates(maxAge, models, null);
    }

    /** age -> model on one fixed auto state, for crops that must not fall onto note blocks. */
    private static Map<String, Object> cropStates(int maxAge, String[] models, String autoState) {
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int age = 0; age <= maxAge; age++) {
            String key = "age=" + age;
            appearances.put(key, Gen.map(
                    "auto_state", autoState == null ? nextThinState() : autoState,
                    "model", Gen.map("path", models[age])));
            variants.put(key, Gen.map("appearance", key));
        }
        return Gen.map(
                "properties", Gen.map("age", Gen.map(
                        "type", "int", "default", 0, "min", 0, "max", maxAge)),
                "appearances", appearances,
                "variants", variants);
    }

    /** Lemon tree style: age x half. */
    private static Map<String, Object> tallCropStates(String prefix, int maxAge) {
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        String[] lower = { "small", "mid", "", "flowers", "fruits" };
        String[] upper = { "small_upper", "mid_upper", "upper", "flowers_upper", "fruits_upper" };
        for (int age = 0; age <= maxAge; age++) {
            for (String half : new String[] { "lower", "upper" }) {
                String key = "age=" + age + ",half=" + half;
                String suffix = "lower".equals(half) ? lower[age] : upper[age];
                String model = "yuanguo:block/" + prefix + (suffix.isEmpty() ? "" : "_" + suffix);
                appearances.put(key, Gen.map(
                        "auto_state", nextThinState(),
                        "model", Gen.map("path", model)));
                variants.put(key, Gen.map("appearance", key));
            }
        }
        return Gen.map(
                "properties", Gen.map(
                        "age", Gen.map("type", "int", "default", 0, "min", 0, "max", maxAge),
                        "half", Gen.map("type", "double_block_half", "default", "lower")),
                "appearances", appearances,
                "variants", variants);
    }
}
