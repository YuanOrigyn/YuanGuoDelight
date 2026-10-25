import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Collects the item tags the mod declares and republishes them for the two consumers.
 *
 * <p>CraftEngine has no root level {@code tags} node. A tag only exists because an item lists it in
 * {@code settings.tags}, so the pack tags its own items and the {@code #c:fruits/lemon} references in
 * the recipes resolve against that.</p>
 *
 * <p>PapersDelight recipes name a tag directly, so this table is also what tells the generator which
 * tags CraftEngine can resolve on its own and which ones have to be published as advanced tags.</p>
 */
public final class Tags {

    private Tags() {
    }

    /** Mod tag directories whose entries belong to the pack namespace. */
    private static final List<String> SOURCES = List.of("forge", "fruitsdelight", "minecraft");

    /** tag id -> members, in declaration order. A member may reference another tag as {@code #id}. */
    private static final Map<String, List<String>> TAG_TO_ITEMS = new LinkedHashMap<>();

    static void load() throws IOException {
        Gson gson = new Gson();
        for (String namespace : SOURCES) {
            Path dir = Gen.MOD.resolve("data/" + namespace + "/tags/items");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.walk(dir)) {
                for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                    if (!path.toString().endsWith(".json")) {
                        continue;
                    }
                    String name = dir.relativize(path).toString().replace('\\', '/').replace(".json", "");
                    List<String> values = values(gson, path);
                    if (values.isEmpty()) {
                        continue;
                    }
                    String id = prefix(namespace) + name;
                    List<String> existing = TAG_TO_ITEMS.computeIfAbsent(id, key -> new ArrayList<>());
                    for (String value : values) {
                        if (!existing.contains(value)) {
                            existing.add(value);
                        }
                    }
                }
            }
        }
        // Tags the recipes reference that no mod file declares. Farmer's Delight is a hard dependency,
        // so its own items are listed without a vanilla substitute.
        fallback("c:drinks/milk", List.of("minecraft:milk_bucket", "farmersdelight:milk_bottle"));
        fallback("c:eggs", List.of("minecraft:egg", "minecraft:brown_egg", "minecraft:blue_egg"));
        fallback("c:foods/dough", List.of("farmersdelight:wheat_dough"));
        fallback("c:crops/rice", List.of("farmersdelight:rice"));
        fallback("c:crops/cabbage", List.of("farmersdelight:cabbage", "farmersdelight:cabbage_leaf"));
        fallback("c:foods/raw_chicken", List.of("minecraft:chicken", "farmersdelight:chicken_cuts"));
        fallback("c:foods/raw_pork", List.of("minecraft:porkchop", "farmersdelight:bacon"));
    }

    /** Adds a tag only when the pack does not define it already. */
    private static void fallback(String id, List<String> values) {
        if (TAG_TO_ITEMS.containsKey(id)) {
            return;
        }
        TAG_TO_ITEMS.put(id, new ArrayList<>(values));
    }

    /**
     * Declares every tag on the items that belong to it.
     *
     * <p>CraftEngine resolves a recipe tag by asking which custom items carry it, so the tags have to
     * live on the items rather than in a tag file.</p>
     */
    @SuppressWarnings("unchecked")
    static void applyItemTags(Map<String, Object> items) {
        Map<String, Set<String>> byItem = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : TAG_TO_ITEMS.entrySet()) {
            String tag = entry.getKey();
            // c:jams only exists as a nested reference, it is expanded separately below.
            if (tag.equals("c:jams")) {
                continue;
            }
            for (String member : entry.getValue()) {
                if (member.startsWith("#")) {
                    continue;
                }
                byItem.computeIfAbsent(member, key -> new LinkedHashSet<>()).add(tag);
            }
        }
        // CraftEngine cannot nest tags, so a jelly lists the jam tag directly as well.
        for (String member : resolve("c:jams")) {
            byItem.computeIfAbsent(member, key -> new LinkedHashSet<>()).add("c:jams");
        }

        for (Map.Entry<String, Set<String>> entry : byItem.entrySet()) {
            Object raw = items.get(entry.getKey());
            if (!(raw instanceof Map)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) raw;
            Object settingsRaw = item.get("settings");
            Map<String, Object> settings;
            if (settingsRaw instanceof Map) {
                settings = (Map<String, Object>) settingsRaw;
            } else {
                settings = new LinkedHashMap<>();
                item.put("settings", settings);
            }
            Set<String> tags = new LinkedHashSet<>();
            Object existing = settings.get("tags");
            if (existing instanceof List<?> list) {
                for (Object value : list) {
                    tags.add(String.valueOf(value));
                }
            }
            tags.addAll(entry.getValue());
            settings.put("tags", new ArrayList<>(tags));
        }
    }

    /** Expands a tag into the concrete item ids it contains, following nested references. */
    static List<String> resolve(String tagId) {
        List<String> out = new ArrayList<>();
        collect(tagId, out, new HashSet<>());
        return out;
    }

    private static void collect(String tagId, List<String> out, Set<String> seen) {
        if (!seen.add(tagId)) {
            return;
        }
        for (String member : TAG_TO_ITEMS.getOrDefault(tagId, List.of())) {
            if (member.startsWith("#")) {
                collect(member.substring(1), out, seen);
            } else if (!out.contains(member)) {
                out.add(member);
            }
        }
    }


    private static List<String> values(Gson gson, Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root;
        try {
            root = gson.fromJson(json, JsonObject.class);
        } catch (Exception ignored) {
            return List.of();
        }
        if (root == null || !root.has("values")) {
            return List.of();
        }
        JsonElement element = root.get("values");
        if (!element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement child : array) {
            if (child.isJsonPrimitive()) {
                out.add(rewrite(child.getAsString()));
            } else if (child.isJsonObject() && child.getAsJsonObject().has("id")) {
                out.add(rewrite(child.getAsJsonObject().get("id").getAsString()));
            }
        }
        return out;
    }

    /**
     * The pack namespace for a mod tag directory.
     *
     * <p>Forge's convention tags become {@code c:} (the name CraftEngine and Farmer's Delight use on
     * Paper), the mod's own tags keep their own namespace, and vanilla tags stay vanilla.</p>
     */
    private static String prefix(String namespace) {
        return switch (namespace) {
            case "forge" -> "c:";
            case "fruitsdelight" -> "yuanguo:";
            default -> namespace + ":";
        };
    }

    /** Rewrites a tag name or reference onto the namespaces used by the pack. */
    private static String rewrite(String value) {
        boolean reference = value.startsWith("#");
        String body = reference ? value.substring(1) : value;
        int colon = body.indexOf(':');
        if (colon > 0) {
            body = prefix(body.substring(0, colon)) + body.substring(colon + 1);
        }
        return reference ? "#" + body : body;
    }
}