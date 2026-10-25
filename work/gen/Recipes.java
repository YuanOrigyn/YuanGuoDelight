import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Converts the Fruits Delight mod recipes.
 *
 * <p>Crafting and smelting go straight into the CraftEngine pack. The cooking pot and cutting board
 * are not CraftEngine blocks here, they belong to PapersDelight, so those recipes are emitted in
 * PapersDelight's own {@code delights/<namespace>/} format instead.</p>
 */
public final class Recipes {

    private Recipes() {
    }

    private static final Map<String, Object> CRAFTING = new LinkedHashMap<>();
    private static final Map<String, Object> COOKING = new LinkedHashMap<>();
    private static final Map<String, Object> CUTTING = new LinkedHashMap<>();
    private static final Map<String, Object> SOAKING = new LinkedHashMap<>();

    /** Advanced tag id -> concrete item ids, for tags CraftEngine cannot resolve on its own. */
    private static final Map<String, Object> ADVANCED_TAGS = new LinkedHashMap<>();

    private static final Set<String> SKIPPED_TYPES = Set.of(
            "create:filling", "create:emptying", "create:mixing", "create:compacting", "create:cutting",
            "botanypots:crop");

    /**
     * Mod tag names that do not exist under the same name once Farmer's Delight runs on Paper. The
     * right hand side is the tag PapersDelight actually registers.
     *
     * <p>These are only relevant when PapersDelight is installed; without it the cooking pot and
     * cutting board recipes are not loaded at all.</p>
     */
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            // Farmer's Delight replaces the generic wheat dough tag with its own item.
            Map.entry("c:dough", "c:foods/dough"),
            Map.entry("c:dough/wheat", "c:foods/dough"),
            Map.entry("c:grain/rice", "c:crops/rice"),
            Map.entry("c:milk/milk_bottle", "c:drinks/milk"),
            Map.entry("c:raw_chicken", "c:foods/raw_chicken"),
            Map.entry("c:raw_pork", "c:foods/raw_pork"),
            Map.entry("c:salad_ingredients/cabbage", "c:crops/cabbage"),
            Map.entry("c:tools/axes", "minecraft:axes"),
            Map.entry("c:tools/knives", "farmersdelight:tools/knives"));

    static void gen() throws IOException {
        jamFluids();
        Path dir = Gen.MOD.resolve("data/fruitsdelight/recipes");
        if (!Files.isDirectory(dir)) {
            return;
        }
        Gson gson = new Gson();
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                String relative = dir.relativize(path).toString().replace('\\', '/');
                if (relative.startsWith("botanypots/")) {
                    continue;
                }
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject recipe;
                try {
                    recipe = gson.fromJson(json, JsonObject.class);
                } catch (Exception ignored) {
                    continue;
                }
                if (recipe == null || !recipe.has("type")) {
                    continue;
                }
                String type = recipe.get("type").getAsString();
                if (SKIPPED_TYPES.contains(type)) {
                    continue;
                }
                String key = "yuanguo:" + relative.replace(".json", "");
                try {
                    switch (type) {
                        case "minecraft:crafting_shaped" -> CRAFTING.put(key, shaped(recipe));
                        case "minecraft:crafting_shapeless" -> CRAFTING.put(key, shapeless(recipe));
                        case "fruitsdelight:jelly_craft_shapeless" -> CRAFTING.put(key, shapeless(recipe));
                        case "minecraft:smelting" -> CRAFTING.put(key, cooking(recipe, "smelting"));
                        case "minecraft:smoking" -> CRAFTING.put(key, cooking(recipe, "smoking"));
                        case "minecraft:campfire_cooking" -> CRAFTING.put(key, cooking(recipe, "campfire_cooking"));
                        case "farmersdelight:cooking" -> COOKING.put(key, pot(recipe));
                        case "farmersdelight:cutting" -> CUTTING.put(key, cutting(recipe));
                        default -> { }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Gen.writeYaml("configuration/recipes.yml", Gen.map("recipes", CRAFTING));

        // The cooking pot and cutting board recipes belong to PapersDelight, which registers them as
        // CraftEngine config sections. They therefore live in the pack, next to the crafting recipes,
        // and no longer in a PapersDelight data folder.
        Map<String, Object> delight = new LinkedHashMap<>(COOKING);
        delight.putAll(CUTTING);

        jamBreadSoaking();
        delight.putAll(SOAKING);
        if (!delight.isEmpty()) {
            Gen.writeYaml("configuration/papersdelight.yml", Gen.map("papersdelight_recipes", delight));
        }
        if (!ADVANCED_TAGS.isEmpty()) {
            Map<String, Object> definitions = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ADVANCED_TAGS.entrySet()) {
                definitions.put(entry.getKey(), Gen.map("values", entry.getValue()));
            }
            Gen.writeYaml("configuration/advanced_tags.yml", Gen.map("advanced_tags", definitions));
        }
    }

    /**
     * Declares one jam fluid per fruit for NyanaFluid.
     *
     * <p>Farmer's Delight 1.2.4 dropped Libuid in favour of NyanaFluid, and its own pack now declares
     * milk the same way: a fluid entry plus the {@code bottle} container. Declaring the jam bottle as
     * the container is what lets a jug recognise it in hand and in its input slot.</p>
     */
    private static void jamFluids() throws IOException {
        Map<String, Object> fluids = new LinkedHashMap<>();
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            String jelly = "yuanguo:" + name + "_jelly";
            Map<String, Object> fluid = new LinkedHashMap<>();
            fluid.put("display_name", "<!i><lang:fluid.yuanguo." + name + "_jam>");
            fluid.put("color", "#" + fruit[2]);
            fluid.put("texture", "minecraft:block/water_still");
            fluid.put("display_item", jelly);
            fluid.put("bottle", jelly);
            fluid.put("sounds", Gen.map(
                    "fill", "minecraft:item.bottle.fill",
                    "empty", "minecraft:item.bottle.empty"));
            fluids.put("yuanguo:" + name + "_jam", fluid);
        }
        Gen.writeYaml("configuration/fluids.yml", Gen.map("nyana-fluid:fluids", fluids));
    }

    // ------------------------------------------------------------------ types

    /**
     * Bread soaked in a jug of jam becomes that fruit's jelly bread.
     *
     * <p>The mod only has the shapeless recipe (one whole jam bottle per bread) and a Create
     * filling recipe that uses 125 mB, so a jug lets the same 250 mB bottle make two breads.</p>
     */
    private static void jamBreadSoaking() {
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            SOAKING.put("yuanguo:jug/jam_bread/" + name, Gen.map(
                    "type", "soaking",
                    "ingredient", "minecraft:bread",
                    "fluid", "yuanguo:" + name + "_jam",
                    "amount", 125,
                    "result", "yuanguo:" + name + "_jelly_bread",
                    "time", 40,
                    "consume_fluid", true));
        }
    }

    private static Map<String, Object> shaped(JsonObject recipe) {
        Map<String, Object> out = Gen.map("type", "shaped");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        List<Object> lines = new ArrayList<>();
        for (JsonElement line : pattern) {
            lines.add(line.getAsString());
        }
        out.put("pattern", lines);
        out.put("ingredients", ingredients(recipe.getAsJsonObject("key")));
        out.put("result", result(recipe.getAsJsonObject("result")));
        return out;
    }

    private static Map<String, Object> shapeless(JsonObject recipe) {
        Map<String, Object> out = Gen.map("type", "shapeless");
        List<Object> list = new ArrayList<>();
        for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
            Object value = ingredient(element);
            if (value != null) {
                list.add(value);
            }
        }
        out.put("ingredients", list);
        out.put("result", result(recipe.getAsJsonObject("result")));
        return out;
    }

    private static Map<String, Object> cooking(JsonObject recipe, String type) {
        Map<String, Object> out = Gen.map(
                "type", type,
                "ingredient", ingredient(recipe.get("ingredient")),
                "result", result(recipe.getAsJsonObject("result")));
        if (recipe.has("cookingtime")) {
            out.put("cookingtime", recipe.get("cookingtime").getAsInt());
        }
        if (recipe.has("experience")) {
            out.put("experience", recipe.get("experience").getAsDouble());
        }
        return out;
    }

    /** A PapersDelight cooking pot recipe. */
    private static Map<String, Object> pot(JsonObject recipe) {
        Map<String, Object> out = Gen.map("type", "cooking");
        JsonObject result = recipe.getAsJsonObject("result");
        String resultId = resultId(map(result.get("item").getAsString()));
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        // A plain string is the single item form; a count needs the map form.
        out.put("result", count > 1 ? Gen.map("id", resultId, "count", count) : resultId);
        if (recipe.has("container")) {
            JsonObject container = recipe.getAsJsonObject("container");
            if (container != null && container.has("item")) {
                out.put("container", map(container.get("item").getAsString()));
            }
        }
        out.put("time", recipe.has("cookingtime") ? recipe.get("cookingtime").getAsInt() : 200);
        out.put("experience", recipe.has("experience") ? recipe.get("experience").getAsDouble() : 1.0D);
        List<Object> ingredients = new ArrayList<>();
        if (recipe.has("ingredients")) {
            for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
                String value = papersdelightIngredient(element);
                if (value != null) {
                    ingredients.add(value);
                }
            }
        }
        out.put("ingredients", ingredients);
        return out;
    }

    /**
     * A PapersDelight cutting board recipe.
     *
     * <p>The mod stores the input as a one element {@code ingredients} array and the tool as a single
     * {@code tool} object; PapersDelight wants flat {@code ingredient} / {@code tools} fields.</p>
     */
    private static Map<String, Object> cutting(JsonObject recipe) {
        Map<String, Object> out = Gen.map("type", "cutting");
        JsonElement input = null;
        if (recipe.has("ingredients")) {
            JsonArray array = recipe.getAsJsonArray("ingredients");
            if (!array.isEmpty()) {
                input = array.get(0);
            }
        } else if (recipe.has("ingredient")) {
            input = recipe.get("ingredient");
        }
        String inputValue = papersdelightIngredient(input);
        if (inputValue != null) {
            out.put("ingredient", inputValue);
        }
        List<Object> tools = new ArrayList<>();
        if (recipe.has("tool")) {
            String tool = papersdelightIngredient(recipe.get("tool"));
            if (tool != null) {
                tools.add(tool);
            }
        }
        out.put("tools", tools);
        List<Object> results = new ArrayList<>();
        if (recipe.has("result")) {
            JsonElement result = recipe.get("result");
            if (result.isJsonArray()) {
                for (JsonElement element : result.getAsJsonArray()) {
                    results.add(cuttingResult(element.getAsJsonObject()));
                }
            } else {
                results.add(cuttingResult(result.getAsJsonObject()));
            }
        }
        out.put("results", results);
        return out;
    }
    private static Map<String, Object> cuttingResult(JsonObject object) {
        Map<String, Object> out = Gen.map("id", resultId(map(object.get("item").getAsString())));
        out.put("count", object.has("count") ? object.get("count").getAsInt() : 1);
        // A drop chance is part of the cutting board recipe, not of the item stack.
        if (object.has("chance")) {
            out.put("chance", object.get("chance").getAsDouble());
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> ingredients(JsonObject key) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            Object value = ingredient(entry.getValue());
            if (value != null) {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    /** An ingredient is either an item id, a tag or a list of alternatives. */
    private static Object ingredient(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                Object value = ingredient(child);
                if (value != null) {
                    list.add(value);
                }
            }
            return list.isEmpty() ? null : Gen.map("items", list);
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) {
            return map(object.get("item").getAsString());
        }
        if (object.has("tag")) {
            return tagIngredient(tag(object.get("tag").getAsString()));
        }
        return null;
    }

    /**
     * A tag reference for a CraftEngine recipe.
     *
     * <p>CraftEngine resolves a tag from the custom items that declare it, so a tag whose members
     * are all vanilla or belong to another plugin resolves to nothing and the whole recipe fails to
     * load. Those tags are expanded into the concrete item list instead.</p>
     */
    private static Object tagIngredient(String tagId) {
        List<String> members = Tags.resolve(tagId);
        boolean resolvable = members.stream().anyMatch(Items.ITEMS::containsKey);
        if (resolvable || members.isEmpty()) {
            return "#" + tagId;
        }
        return Gen.map("items", new ArrayList<Object>(members));
    }

    /**
     * The same ingredient in the shape PapersDelight expects.
     *
     * <p>A PapersDelight ingredient is a single expression: a bare item id, a {@code #tag} or an
     * {@code advtag:tag}. There is no wrapped {@code item}/{@code tag} map any more.</p>
     */
    private static String papersdelightIngredient(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            // A list of alternatives is not expressible, the first entry wins.
            for (JsonElement child : element.getAsJsonArray()) {
                String value = papersdelightIngredient(child);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) {
            return map(object.get("item").getAsString());
        }
        if (object.has("tag")) {
            return advancedTag(tag(object.get("tag").getAsString()));
        }
        return null;
    }

    /**
     * Resolves a recipe result to an item id.
     *
     * <p>A serving block (a pie, a plate of fried rice) is a block whose item is spelled
     * {@code bowl_of_<name>} in this pack, while the mod's recipe names the block. Recipes have to
     * hand out the item, not the block.</p>
     */
    private static String resultId(String id) {
        if (Items.ITEMS.containsKey(id)) {
            return id;
        }
        if (id != null && id.startsWith("yuanguo:")) {
            String bowl = "yuanguo:bowl_of_" + id.substring("yuanguo:".length());
            if (Items.ITEMS.containsKey(bowl)) {
                return bowl;
            }
        }
        return id;
    }

    /**
     * A tag reference for a PapersDelight recipe.
     *
     * <p>PapersDelight resolves a plain {@code #tag} through CraftEngine, which only knows the tags
     * declared by custom items. A tag that names vanilla items or items from another plugin resolves
     * to nothing there, so it is published as an {@code advanced_tags} entry instead, which expands
     * to the concrete items.</p>
     */
    private static String advancedTag(String tagId) {
        List<String> members = Tags.resolve(tagId);
        boolean craftEngineTag = members.stream().anyMatch(Items.ITEMS::containsKey);
        if (craftEngineTag || members.isEmpty()) {
            return "#" + tagId;
        }
        ADVANCED_TAGS.computeIfAbsent(tagId, key -> new ArrayList<>(members));
        return "advtag:" + tagId;
    }
    private static Map<String, Object> result(JsonObject object) {
        Map<String, Object> out = Gen.map("id", resultId(map(object.get("item").getAsString())));
        out.put("count", object.has("count") ? object.get("count").getAsInt() : 1);
        return out;
    }

    /** Rewrites the mod namespace onto the pack namespace and the Forge tag prefix onto the common one. */
    static String map(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("fruitsdelight:")) {
            return "yuanguo:" + id.substring("fruitsdelight:".length());
        }
        return id;
    }

    static String tag(String raw) {
        String value = raw;
        if (value.startsWith("forge:")) {
            value = "c:" + value.substring("forge:".length());
        } else if (value.startsWith("fruitsdelight:")) {
            value = "yuanguo:" + value.substring("fruitsdelight:".length());
        }
        return TAG_ALIASES.getOrDefault(value, value);
    }
}
