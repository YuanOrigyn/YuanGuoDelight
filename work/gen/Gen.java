import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Builds the YuanGuoDelight CraftEngine content pack from the Fruits Delight mod data.
 *
 * <p>Everything is emitted into the CraftEngine pack: the cooking pot and cutting board recipes are
 * registered by PapersDelight as CraftEngine config sections, so they live next to the crafting
 * recipes instead of in a separate data folder.</p>
 *
 * <p>Usage: {@code Gen <modDir> <packDir>}</p>
 */
public final class Gen {

    static Path MOD;
    static Path OUT;
    static Yaml YAML;
    static int files = 0;

    public static void main(String[] args) throws Exception {
        MOD = Paths.get(args[0]);
        OUT = Paths.get(args[1]);


        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setWidth(4096);
        options.setAllowUnicode(true);
        Representer representer = new Representer(options);
        representer.addClassTag(Double.class, Tag.FLOAT);
        representer.addClassTag(Float.class, Tag.FLOAT);
        representer.addClassTag(Integer.class, Tag.INT);
        representer.addClassTag(Long.class, Tag.INT);
        YAML = new Yaml(representer, options);

        deleteRecursively(OUT);
        Files.createDirectories(OUT);

        Lang.load(MOD);
        // Item tags must be known before the items are emitted: CraftEngine declares a tag
        // by listing it on the item, not by reading a tag file.
        Tags.load();

        write("pack.yml", """
                author: Yuan
                version: 1.0.0
                description: Yuan来是果 - Fruits Delight for CraftEngine
                namespace: yuanguo
                enable: true
                """);

        // The effect table has to exist before the items so each food can carry its tooltip lore.
        Effects.build();
        Items.gen();
        Blocks.gen();
        Recipes.gen();
        Features.gen();
        LangFile.gen();
        Categories.gen();
        Effects.gen();

        System.out.println("generated " + files + " files");
    }

    // ------------------------------------------------------------------ utils

    static void writeYaml(String relative, Object root) throws IOException {
        write(relative, "# Generated from the Fruits Delight mod by the YuanGuoDelight build script.\n"
                + YAML.dump(root));
    }


    static void write(String relative, String content) throws IOException {
        Path target = OUT.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        files++;
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static List<Object> list(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    static String id(String name) {
        return "yuanguo:" + name;
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}