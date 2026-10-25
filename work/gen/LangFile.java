import java.io.IOException;
import java.util.*;

/** Emits configuration/lang/*.yml so item and block names stay translated. */
public final class LangFile {

    private LangFile() {
    }

    /**
     * Category names for the item browser. Only the locales that actually differ are listed; every
     * other locale falls back to the English strings below, which are also what the generator writes
     * when a locale has no entry.
     */
    private static final Map<String, String> CATEGORY_EN = Map.ofEntries(
            Map.entry("fruits", "Fruits"),
            Map.entry("crops", "Seeds & Saplings"),
            Map.entry("leaves", "Fruit Leaves"),
            Map.entry("potted", "Potted Plants"),
            Map.entry("foods", "Foods"),
            Map.entry("drinks", "Drinks"),
            Map.entry("jellies", "Jams & Jellies"),
            Map.entry("feast", "Feast"),
            Map.entry("jam_blocks", "Jam Blocks"),
            Map.entry("crates", "Crates"),
            Map.entry("cauldrons", "Cauldrons"),
            Map.entry("equipment", "Equipment"));

    private static final Map<String, String> CATEGORY_ZH = Map.ofEntries(
            Map.entry("fruits", "水果"),
            Map.entry("crops", "种子与树苗"),
            Map.entry("leaves", "果树叶"),
            Map.entry("potted", "盆栽"),
            Map.entry("foods", "食物"),
            Map.entry("drinks", "饮品"),
            Map.entry("jellies", "果酱与果冻"),
            Map.entry("feast", "盛宴"),
            Map.entry("jam_blocks", "果酱方块"),
            Map.entry("crates", "板条箱"),
            Map.entry("cauldrons", "炼药锅"),
            Map.entry("equipment", "装备"));

    static void gen() throws IOException {
        int written = 0;
        for (String locale : Lang.LOCALES) {
            Map<String, String> items = new LinkedHashMap<>();
            Map<String, String> blocks = new LinkedHashMap<>();
            Map<String, String> others = new LinkedHashMap<>();

            for (Map.Entry<String, String> entry : Lang.remapped(locale).entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("item.")) {
                    items.put(key, entry.getValue());
                } else if (key.startsWith("block.")) {
                    blocks.put(key, entry.getValue());
                } else if (key.startsWith("effect.") || key.startsWith("fluid.")
                        || key.startsWith("fruitsdelight.") || key.startsWith("yuanguo.")) {
                    others.put(key, entry.getValue());
                }
            }
            boolean zh = locale.startsWith("zh");
            Map<String, String[]> extra = new LinkedHashMap<>();



            for (Map.Entry<String, String[]> entry : extra.entrySet()) {
                String value = zh ? entry.getValue()[0] : entry.getValue()[1];
                if (entry.getKey().startsWith("item.")) {
                    items.put(entry.getKey(), value);
                } else if (entry.getKey().startsWith("block.")) {
                    blocks.put(entry.getKey(), value);
                }
            }
            // The browser root keeps this plugin's own name rather than the mod's "Fruits Delight".
            others.put("itemGroup.yuanguo", locale.startsWith("zh") ? "Yuan来是果" : "Fruits Delight");

            Map<String, String> category = locale.startsWith("zh") ? CATEGORY_ZH : CATEGORY_EN;
            for (Map.Entry<String, String> entry : CATEGORY_EN.entrySet()) {
                others.put("category.yuanguo." + entry.getKey(),
                        category.getOrDefault(entry.getKey(), entry.getValue()));
            }

            // The pack's lore names vanilla effects and PapersDelight's Nourishment directly. A
            // client only knows the keys its own language file contains, so publish the ones the
            // pack uses instead of letting an unknown key show up as raw text.
            addEffectFallbacks(others, locale);

            if (items.isEmpty() && blocks.isEmpty() && others.isEmpty()) {
                continue;
            }
            Map<String, Object> root = new LinkedHashMap<>();
            if (!items.isEmpty()) {
                root.put("lang#items", Gen.map(locale, items));
            }
            if (!blocks.isEmpty()) {
                root.put("lang#blocks", Gen.map(locale, blocks));
            }
            if (!others.isEmpty()) {
                root.put("lang#others", Gen.map(locale, others));
            }
            Gen.writeYaml("configuration/lang/" + locale + ".yml", root);
            written++;
        }
        System.out.println("language files: " + written);
    }

    /**
     * Fills the translation keys the item lore references but the mod does not ship.
     *
     * <p>Vanilla effect names are only written for English and Chinese: other clients already get
     * them from their own vanilla language file, and overriding those with English would be worse
     * than leaving them alone. Nourishment is a PapersDelight key with no vanilla equivalent, so it
     * is published everywhere.</p>
     */
    private static void addEffectFallbacks(Map<String, String> others, String locale) {
        boolean zh = locale.startsWith("zh");
        boolean en = locale.startsWith("en");
        if (zh) {
            others.putIfAbsent("effect.minecraft.absorption", "\u5438\u6536");
            others.putIfAbsent("effect.minecraft.glowing", "\u53d1\u5149");
            others.putIfAbsent("effect.minecraft.haste", "\u6025\u8feb");
            others.putIfAbsent("effect.minecraft.health_boost", "\u751f\u547d\u63d0\u5347");
            others.putIfAbsent("effect.minecraft.night_vision", "\u591c\u89c6");
            others.putIfAbsent("effect.minecraft.regeneration", "\u751f\u547d\u6062\u590d");
            others.putIfAbsent("effect.minecraft.speed", "\u901f\u5ea6");
            others.putIfAbsent("effect.minecraft.strength", "\u529b\u91cf");
        } else if (en) {
            others.putIfAbsent("effect.minecraft.absorption", "Absorption");
            others.putIfAbsent("effect.minecraft.glowing", "Glowing");
            others.putIfAbsent("effect.minecraft.haste", "Haste");
            others.putIfAbsent("effect.minecraft.health_boost", "Health Boost");
            others.putIfAbsent("effect.minecraft.night_vision", "Night Vision");
            others.putIfAbsent("effect.minecraft.regeneration", "Regeneration");
            others.putIfAbsent("effect.minecraft.speed", "Speed");
            others.putIfAbsent("effect.minecraft.strength", "Strength");
        }
        others.putIfAbsent("effect.farmersdelight.nourishment", zh ? "\u6ecb\u517b" : "Nourishment");
        others.putIfAbsent("potion.potency.2", "II");
        others.putIfAbsent("block.minecraft.water_cauldron", zh ? "\u6c34\u70bc\u836f\u9505" : "Water Cauldron");
        others.putIfAbsent("item.yuanguo.attached_hamimelon_stem", zh ? "\u54c8\u5bc6\u74dc\u830e" : "Hamimelon Stem");
    }
}
