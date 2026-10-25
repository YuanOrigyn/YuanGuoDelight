import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Converts the mod's world generation into CraftEngine placed features.
 *
 * <p>CraftEngine 26.1 removed vanilla's {@code minecraft:random_patch} feature type, so the mod's
 * patch features are unwrapped into a plain {@code minecraft:simple_block} and the old
 * {@code tries}/{@code xz_spread}/{@code y_spread} knobs become {@code minecraft:count} and
 * {@code minecraft:random_offset} placement steps. Everything is emitted inline under
 * {@code placed_features}, which is the layout the official Farmer's Delight pack uses.</p>
 */
public final class Features {

    private Features() {
    }

    private static final Gson GSON = new Gson();

    /** Block state properties the mod has but our blocks do not. */
    private static final Set<String> DROPPED_PROPERTIES =
            Set.of("distance", "persistent", "waterlogged", "fertile", "leaf", "fruit");

    static void gen() throws IOException {
        Path worldgen = Gen.MOD.resolve("data/fruitsdelight/worldgen");
        Path tags = Gen.MOD.resolve("data/fruitsdelight/tags/worldgen/biome");
        if (!Files.isDirectory(worldgen)) {
            return;
        }

        Map<String, Object> placed = new LinkedHashMap<>();
        // Trees are also declared as standalone configured features: CraftEngine only exposes the
        // ones listed under configured_feature: to configuredFeatureHolderById(), which is what the
        // sapling behavior calls when it grows a tree.
        Map<String, Object> configured = new LinkedHashMap<>();
        Path placedDir = worldgen.resolve("placed_feature");
        try (var stream = Files.walk(placedDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String relative = placedDir.relativize(path).toString().replace('\\', '/');
                JsonObject json = read(path);
                if (json == null) {
                    continue;
                }
                String key = "yuanguo:" + relative.replace(".json", "");
                Map<String, Object> entry = placed(worldgen, tags, json, relative);
                if (entry != null) {
                    placed.put(key, entry);
                }
                if (relative.startsWith("tree/")) {
                    // The placed feature grows the wild tree (more flowers), but a sapling grown by
                    // hand uses the tamer variant. CraftEngine looks configured features up by id,
                    // so the entry under the sapling's key has to be the non-wild configuration.
                    String wild = json.get("feature").getAsString();
                    String tamed = wild.endsWith("_wild")
                            ? wild.substring(0, wild.length() - "_wild".length()) : wild;
                    JsonObject source = configured(worldgen, tamed);
                    if (source != null && !"minecraft:random_patch".equals(source.get("type").getAsString())) {
                        configured.put(key, asMap(rewrite(source)));
                    }
                }
            }
        }

        if (placed.isEmpty()) {
            return;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        if (!configured.isEmpty()) {
            root.put("configured_feature", configured);
        }
        root.put("placed_features", placed);
        Gen.writeYaml("configuration/features.yml", root);
    }
    // --------------------------------------------------------------- placement

    /** Builds one {@code placed_features} entry, unwrapping a patch feature when needed. */
    private static Map<String, Object> placed(Path worldgen, Path tags, JsonObject json, String relative) {
        String reference = json.get("feature").getAsString();
        JsonObject configured = configured(worldgen, reference);
        if (configured == null) {
            return null;
        }

        List<Object> placement = new ArrayList<>();
        for (JsonElement step : json.getAsJsonArray("placement")) {
            placement.add(rewrite(step));
        }

        Map<String, Object> feature;
        String type = configured.get("type").getAsString();
        if ("minecraft:random_patch".equals(type)) {
            JsonObject config = configured.getAsJsonObject("config");
            // The inner configured feature keeps the shape {feature: {...}, placement: [...]}.
            JsonObject inner = config.getAsJsonObject("feature");
            JsonObject innerFeature = inner.getAsJsonObject("feature");
            feature = asMap(rewrite(innerFeature));
            // The mod generates the lemon tree already ripe (age 4). Because a ripe tree is two
            // blocks tall it is written as a block_column, so both halves land in the same pass;
            // writing only the lower half left a floating stump behind.
            boolean lemon = "fruitsdelight:lemon_tree".equals(stateName(innerFeature));
            if (lemon) {
                feature = lemonTreeColumn(innerFeature.getAsJsonObject("config")
                        .getAsJsonObject("to_place").getAsJsonObject("state"));
                placement.clear();
                for (JsonElement step : json.getAsJsonArray("placement")) {
                    placement.add(rewrite(step));
                }
            }
            if (!lemon) {
                for (JsonElement step : inner.getAsJsonArray("placement")) {
                    placement.add(rewrite(step));
                }
            }
            // The patch's tries/spread become ordinary placement steps. They go before the biome
            // step so the biome is sampled at the scattered position, like the official pack does.
            int insertAt = -1;
            for (int i = 0; i < placement.size(); i++) {
                Map<String, Object> step = asMap(placement.get(i));
                if (step != null && "minecraft:biome".equals(step.get("type"))) {
                    insertAt = i;
                    break;
                }
            }
            if (insertAt < 0) {
                insertAt = placement.size();
            }
            placement.add(insertAt, Gen.map("type", "minecraft:count", "count", config.get("tries").getAsInt()));
            placement.add(insertAt + 1, randomOffset(
                    config.get("xz_spread").getAsInt(), config.get("y_spread").getAsInt()));
            if (lemon) {
                // The predicate must run after the heightmap and scatter steps; otherwise it
                // checks the raw chunk position and the tree never places. A ripe tree needs two
                // blocks of air because it occupies both.
                placement.add(lemonPredicate());
            }
        } else {
            feature = asMap(rewrite(configured));
        }

        String plant = relative.replace(".json", "");
        if (plant.startsWith("tree/")) {
            plant = plant.substring("tree/".length());
        }
        plant = plant.replace("_tree", "").replace("_bush", "");
        List<Object> biomes = biomes(tags, plant + "_biomes");

        Map<String, Object> entry = new LinkedHashMap<>();
        if (!biomes.isEmpty()) {
            entry.put("biome", biomes);
        }
        entry.put("dimension_type", Gen.list("minecraft:overworld"));
        entry.put("feature", feature);
        entry.put("placement", placement);
        return entry;
    }

    /** The block id of a simple_block provider, or null when the feature is not one. */
    private static String stateName(JsonObject feature) {
        if (!"minecraft:simple_block".equals(feature.get("type").getAsString())) {
            return null;
        }
        JsonObject state = feature.getAsJsonObject("config").getAsJsonObject("to_place").getAsJsonObject("state");
        return state == null ? null : state.get("Name").getAsString();
    }

    /**
     * A ripe lemon tree is two blocks tall. Writing it as a single state leaves the upper half
     * missing, so both halves are written in one pass with block_column, the same trick the
     * FarmersDelight pack uses for wild rice.
     */
    private static Map<String, Object> lemonTreeColumn(JsonObject stateSource) {
        Map<String, Object> lower = asMap(rewrite(stateSource));
        @SuppressWarnings("unchecked")
        Map<String, Object> lowerProperties = new LinkedHashMap<>(
                (Map<String, Object>) lower.get("Properties"));
        lowerProperties.put("age", "4");
        lowerProperties.put("half", "lower");
        lower.put("Properties", lowerProperties);
        Map<String, Object> upper = new LinkedHashMap<>(lower);
        @SuppressWarnings("unchecked")
        Map<String, Object> upperProperties = new LinkedHashMap<>(
                (Map<String, Object>) lowerProperties);
        upperProperties.put("half", "upper");
        upper.put("Properties", upperProperties);
        return Gen.map(
                "type", "minecraft:block_column",
                "config", Gen.map(
                        "allowed_placement", Gen.map("type", "minecraft:replaceable"),
                        "direction", "up",
                        "prioritize_tip", false,
                        "layers", Gen.list(
                                Gen.map("height", 1, "provider", provider(lower)),
                                Gen.map("height", 1, "provider", provider(upper)))));
    }

    private static Map<String, Object> provider(Map<String, Object> state) {
        return Gen.map(
                "type", "craftengine:simple_state_provider",
                "state", state);
    }

    /** A ripe tree needs two blocks of air above grass. */
    private static Map<String, Object> lemonPredicate() {
        return Gen.map(
                "type", "minecraft:block_predicate_filter",
                "predicate", Gen.map(
                        "type", "minecraft:all_of",
                        "predicates", Gen.list(
                                Gen.map("type", "minecraft:matching_blocks", "blocks", "minecraft:air"),
                                Gen.map("type", "minecraft:matching_blocks", "blocks", "minecraft:air",
                                        "offset", Gen.list(0, 1, 0)),
                                Gen.map("type", "minecraft:matching_blocks", "blocks", "minecraft:grass_block",
                                        "offset", Gen.list(0, -1, 0)))));
    }

    /** A trapezoid offset, matching what the mod's {@code xz_spread}/{@code y_spread} did. */
    private static Map<String, Object> randomOffset(int xzSpread, int ySpread) {
        return Gen.map(
                "type", "minecraft:random_offset",
                "xz_spread", Gen.map("type", "minecraft:trapezoid",
                        "max", xzSpread, "min", -xzSpread, "plateau", 0),
                "y_spread", Gen.map("type", "minecraft:trapezoid",
                        "max", ySpread, "min", -ySpread, "plateau", 0));
    }

    /** Loads the configured feature a placed feature points at. */
    private static JsonObject configured(Path worldgen, String reference) {
        String path = reference.substring(reference.indexOf(':') + 1);
        Path file = worldgen.resolve("configured_feature").resolve(path + ".json");
        return Files.exists(file) ? read(file) : null;
    }
    // ----------------------------------------------------------------- rewriting

    /** Rewrites namespaces and strips block state properties our blocks do not have. */
    private static Object rewrite(JsonElement element) {
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                list.add(rewrite(child));
            }
            return list;
        }
        if (!element.isJsonObject()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return Recipes.map(element.getAsString());
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                String raw = element.getAsString();
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return Double.valueOf(raw);
                }
                return Long.valueOf(raw);
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String name = entry.getKey();
            if ("type".equals(name) && entry.getValue().isJsonPrimitive()) {
                // A provider that places one of our own blocks has to go through CraftEngine's
                // registry: minecraft:simple_state_provider only knows vanilla blocks, which is why
                // the placed features failed with "Unknown registry key ... yuanguo:lemon_tree".
                String type = entry.getValue().getAsString();
                out.put(name, type.replace("minecraft:simple_state_provider",
                        "craftengine:simple_state_provider"));
                continue;
            }
            if ("Properties".equals(name)) {
                Map<String, Object> kept = leafProperties(entry.getValue().getAsJsonObject());
                if (!kept.isEmpty()) {
                    out.put("Properties", kept);
                }
                continue;
            }
            out.put(name, rewrite(entry.getValue()));
        }
        return out;
    }

    /**
     * Copies the block state properties that survive the port.
     *
     * <p>Only {@code type} and {@code age} exist on our blocks. The mod's durian leaves instead use
     * a {@code fruit}/{@code leaf} pair, which is folded back into {@code type}.</p>
     */
    private static Map<String, Object> leafProperties(JsonObject properties) {
        Map<String, Object> kept = new LinkedHashMap<>();
        String fruit = null;
        String leaf = null;
        for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
            String name = property.getKey();
            if (DROPPED_PROPERTIES.contains(name)) {
                if ("fruit".equals(name)) {
                    fruit = property.getValue().getAsString();
                } else if ("leaf".equals(name)) {
                    leaf = property.getValue().getAsString();
                }
                continue;
            }
            kept.put(name, property.getValue().getAsString());
        }
        // Durian leaves carry fruit=none plus a separate leaf property; the budding variant has to
        // stay budding, otherwise the wild tree loses its flower buds.
        if ("budding".equals(leaf)) {
            kept.put("type", "budding");
        } else if (fruit != null) {
            kept.put("type", switch (fruit) {
                case "flowers" -> "flowers";
                case "fruits", "small" -> "fruits";
                default -> "leaves";
            });
        }
        return kept;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    // ------------------------------------------------------------------- biomes

    private static List<Object> biomes(Path tags, String file) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        resolve(tags, "plants/" + file + ".json", result, 0);
        List<Object> list = new ArrayList<>();
        for (String biome : result) {
            if (biome.startsWith("minecraft:")) {
                list.add(biome);
            }
        }
        return list;
    }

    private static void resolve(Path tags, String file, Set<String> out, int depth) {
        if (depth > 2) {
            return;
        }
        Path path = tags.resolve(file);
        if (!Files.exists(path)) {
            return;
        }
        JsonObject json = read(path);
        if (json == null || !json.has("values")) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray("values")) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value.startsWith("#")) {
                    String nested = value.substring(1);
                    int colon = nested.indexOf(':');
                    if (colon >= 0) {
                        nested = nested.substring(colon + 1);
                    }
                    resolve(tags, nested + ".json", out, depth + 1);
                } else if (isBiomeId(value)) {
                    out.add(value);
                }
            } else if (element.isJsonObject()) {
                JsonElement id = element.getAsJsonObject().get("id");
                if (id != null && id.isJsonPrimitive()) {
                    String value = id.getAsString();
                    if (isBiomeId(value)) {
                        out.add(value);
                    }
                }
            }
        }
    }

    /** Modded biomes are not present on the server, so they are ignored. */
    private static boolean isBiomeId(String value) {
        return value.matches("^[a-z0-9_.-]+:[a-z0-9_/]+$");
    }

    // ------------------------------------------------------------------ helpers

    private static JsonObject read(Path path) {
        try {
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
