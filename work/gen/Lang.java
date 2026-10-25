import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Reads the Fruits Delight language files and exposes them under the yuanguo namespace. */
public final class Lang {

    public static final String[] LOCALES = {
            "en_us", "zh_cn", "zh_tw", "zh_hk", "ja_jp", "ko_kr", "ru_ru", "de_de", "fr_fr",
            "es_es", "pt_br", "pt_pt", "pl_pl", "uk_ua", "it_it", "nl_nl", "sv_se", "tr_tr",
            "vi_vn", "id_id", "fi_fi", "hu_hu", "cs_cz", "da_dk", "el_gr", "no_no", "ro_ro",
            "sk_sk", "th_th", "ar_sa", "he_il", "hi_in", "bg_bg", "ca_es", "et_ee", "lt_lt",
            "lv_lv", "sr_sp", "sl_si", "is_is", "cy_gb", "ga_ie", "mt_mt", "sq_al", "mk_mk",
            "bs_ba", "hr_hr", "af_za", "eu_es", "gl_es", "fy_nl", "lb_lu", "nn_no", "sw_ke",
            "tl_ph", "ms_my", "ta_in", "te_in", "bn_bd", "ur_pk", "fa_ir", "az_az", "kk_kz",
            "uz_uz", "hy_am", "ka_ge", "mn_mn", "ne_np", "si_lk", "km_kh", "lo_la", "my_mm",
            "be_by", "ka_ge", "tt_ru", "ba_ru", "cv_ru", "sah_sah", "ce_ru", "os_ru", "ab_ru",
            "lol_us", "lzh", "tok", "en_gb", "en_nz", "en_au", "en_ca", "es_ar", "es_cl",
            "es_ec", "es_mx", "es_uy", "es_ve", "fr_ca", "nl_be", "de_at", "de_ch", "it_ch",
            "pt_ao", "sr_cs"};

    /** locale -> (original mod key -> translated value). */
    private static final Map<String, Map<String, String>> DATA = new LinkedHashMap<>();

    private Lang() {
    }

    public static Map<String, String> load(Path modDir) {
        Gson gson = new Gson();
        Path dir = modDir.resolve("assets/fruitsdelight/lang");
        for (String locale : LOCALES) {
            Path file = dir.resolve(locale + ".json");
            if (!Files.exists(file)) {
                continue;
            }
            try {
                Map<String, String> raw = gson.fromJson(
                        Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() { }.getType());
                if (raw != null) {
                    DATA.put(locale, raw);
                }
            } catch (Exception ignored) {
            }
        }
        return DATA.isEmpty() ? Map.of() : DATA.get(DATA.containsKey("zh_cn") ? "zh_cn" : DATA.keySet().iterator().next());
    }

    /** @return the translated value for {@code item.yuanguo.<name>} style lookups, or {@code null}. */
    public static String lookup(String locale, String modKey) {
        Map<String, String> table = DATA.get(locale);
        if (table == null) {
            table = DATA.get("en_us");
        }
        return table == null ? null : table.get(modKey);
    }

    /**
     * Whether the mod declares {@code modKey} in any locale.
     *
     * <p>The mod puts block-backed items under {@code block.fruitsdelight.*} and plain items under
     * {@code item.fruitsdelight.*}; the two sets overlap, so the prefix has to be read off the mod
     * instead of guessed.</p>
     */
    public static boolean hasModKey(String modKey) {
        for (Map<String, String> table : DATA.values()) {
            if (table.containsKey(modKey)) {
                return true;
            }
        }
        return false;
    }

    /** The locale a regional variant borrows its unlisted strings from. */
    private static final Map<String, String> BASE = Map.ofEntries(
            Map.entry("en", "en_us"),
            Map.entry("es", "es_es"),
            Map.entry("pt", "pt_pt"),
            Map.entry("fr", "fr_fr"),
            Map.entry("de", "de_de"),
            Map.entry("it", "it_it"),
            Map.entry("nl", "nl_nl"),
            Map.entry("sr", "sr_sp"),
            Map.entry("zh", "zh_cn"));

    /**
     * The locale table a lookup should start from.
     *
     * <p>The mod ships a handful of regional files (for example {@code en_nz}) that only list the
     * strings that differ from the base language. Reading them on their own leaves every other item
     * with a raw id, so a regional locale is layered on top of its base language and only the keys
     * it actually overrides win.</p>
     */
    private static Map<String, String> layered(String locale) {
        return layered(locale, new HashSet<>());
    }

    private static Map<String, String> layered(String locale, Set<String> seen) {
        if (!seen.add(locale)) {
            return DATA.get("en_us");
        }
        Map<String, String> own = DATA.get(locale);
        String base = baseOf(locale);
        if (base == null) {
            return own == null ? DATA.get("en_us") : own;
        }
        Map<String, String> parent = layered(base, seen);
        if (parent == null) {
            return own == null ? DATA.get("en_us") : own;
        }
        Map<String, String> result = new LinkedHashMap<>(parent);
        if (own != null) {
            result.putAll(own);
        }
        return result;
    }

    /**
     * The base locale for a regional variant: {@code en_nz -> en_us}, {@code pt_br -> pt_pt}, and so
     * on. A language that ships no base of its own falls back to English.
     */
    private static String baseOf(String locale) {
        int underscore = locale.indexOf('_');
        if (underscore < 0) {
            return locale.equals("en_us") ? null : "en_us";
        }
        String language = locale.substring(0, underscore);
        String base = BASE.get(language);
        if (base == null || base.equals(locale)) {
            return null;
        }
        // Only use the base when the mod actually ships it; otherwise English is the safety net.
        return DATA.containsKey(base) ? base : "en_us";
    }

    /**
     * The mod's Brazilian Portuguese file uses {@code coro_*} keys for the chorus fruit, but the
     * block and item ids are {@code chorus_*}. Without this the PT-BR chorus entries fall back to a
     * raw id, so the key is normalised to the id the pack actually ships.
     */
    private static String normaliseKey(String key) {
        String fixed = key.replace(".coro_", ".chorus_").replace(".coro.", ".chorus.");
        return fixed.endsWith(".coro") ? fixed.substring(0, fixed.length() - 5) + ".chorus" : fixed;
    }

    /** Rewrites every {@code fruitsdelight.} key onto {@code yuanguo.}. */
    public static Map<String, String> remapped(String locale) {
        Map<String, String> table = layered(locale);
        Map<String, String> result = new LinkedHashMap<>();
        if (table == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : table.entrySet()) {
            String key = normaliseKey(entry.getKey());
            if (key.contains("fruitsdelight.")) {
                result.put(key.replace("fruitsdelight.", "yuanguo."), entry.getValue());
            } else if (key.startsWith("fruitsdelight.")) {
                result.put("yuanguo." + key.substring("fruitsdelight.".length()), entry.getValue());
            }
        }
        return result;
    }
}