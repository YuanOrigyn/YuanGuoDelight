import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builds the cauldron content textures and the models that show them.
 *
 * <p>The mod draws every cauldron's content with vanilla's animated water texture and lets a block
 * tint colour it: lemonade is 30% lemon over white, and a fruit cauldron blends from lemon to the
 * fruit's own colour as its level rises from 1 to 12. CraftEngine has no block tint, so the vanilla
 * water frames are tinted and baked into one texture per fruit and level instead, and each level
 * gets its own model pointing at it. The animated frames are kept, together with the matching
 * {@code .png.mcmeta}.</p>
 *
 * <p>Usage: {@code CauldronTextures <resourcepack assets dir> [vanilla water_still.png]}</p>
 */
public final class CauldronTextures {

    private CauldronTextures() {
    }

    public static void main(String[] args) throws Exception {
        Path assets = Paths.get(args[0]);
        Path textures = assets.resolve("textures/block");
        Path models = assets.resolve("models/block");
        Files.createDirectories(textures);
        Files.createDirectories(models);

        Path waterSource = args.length > 1
                ? Paths.get(args[1])
                : Paths.get("work/vanilla-water/assets/minecraft/textures/block/water_still.png");
        BufferedImage water = water(waterSource);
        String animation = animationMeta(waterSource);
        // The mod draws the jam and jello cauldrons with two different 16x16 grayscale textures,
        // both tinted by the fruit colour. They are static, unlike the water surface.
        BufferedImage jelly = source(textures.resolve("jelly.png"));
        BufferedImage jello = source(textures.resolve("jello.png"));

        int[] lemon = mix(rgb(0xFFFFFF), rgb(0xFFE47A), 0.30F);
        writeTinted(water, packed(lemon), textures.resolve("lemonade_cauldron_content.png"), animation);
        writeBodyModel(models.resolve("cauldron_body.json"));
        writeLiquidModel(models.resolve("lemonade_cauldron_liquid.json"),
                "yuanguo:block/lemonade_cauldron_content", 15);
        writeModel(models.resolve("lemonade_cauldron.json"), "yuanguo:block/lemonade_cauldron_content");

        int count = 0;
        for (String[] fruit : FruitData.FRUITS) {
            String name = fruit[0];
            int[] colour = rgb(Integer.parseInt(fruit[2], 16));
            for (int level = 1; level <= 12; level++) {
                String texture = name + "_cauldron_content_" + level;
                writeTinted(water, packed(mix(lemon, colour, level / 12f)),
                        textures.resolve(texture + ".png"), animation);
                writeModel(models.resolve(texture + ".json"), "yuanguo:block/" + texture);
                writeLiquidModel(models.resolve(name + "_cauldron_liquid_" + level + ".json"),
                        "yuanguo:block/" + texture, 15);
                count++;
            }
            // The item form shows the full cauldron, which is what the mod's item colour resolves to.
            writeModel(models.resolve(name + "_cauldron.json"),
                    "yuanguo:block/" + name + "_cauldron_content_12");
            // JellyCauldronBlock uses block/jelly, the jello cauldron block/jello. They share the
            // fruit tint but not the pattern, so each is baked from its own source texture.
            write(tint(jelly, packed(colour)), textures.resolve("jelly_" + name + ".png"));
            write(tint(jello, packed(colour)), textures.resolve("jello_" + name + ".png"));
            for (String kind : new String[] { "jelly", "jello" }) {
                String texture = (kind.equals("jelly") ? "jelly_" : "jello_") + name;
                for (int level = 1; level <= 3; level++) {
                    int height = level == 1 ? 9 : level == 2 ? 12 : 15;
                    writeLiquidModel(models.resolve(name + "_cauldron_" + kind + "_liquid_"
                            + level + ".json"), "yuanguo:block/" + texture, height);
                }
            }
        }
        System.out.println("cauldron content textures: " + (count + 1));
    }

    /** The empty vanilla cauldron body; liquids are drawn by a separate display entity. */
    private static void writeBodyModel(Path target) throws Exception {
        String json = """
                {
                  "parent": "minecraft:block/cauldron"
                }
                """;
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
    }

    /** A liquid-only model so the cauldron body can be tinted and lit independently. */
    private static void writeLiquidModel(Path target, String content, int height) throws Exception {
        String json = """
                {
                  "parent": "minecraft:block/block",
                  "ambientocclusion": false,
                  "textures": {
                    "particle": "%s",
                    "content": "%s"
                  },
                  "elements": [
                    {
                      "from": [2, 4, 2],
                      "to": [14, %d, 14],
                      "faces": {
                        "up": { "texture": "#content" }
                      }
                    }
                  ]
                }
                """.formatted(content, content, height);
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
    }

    /** The mod's cauldron template with our own content texture. */
    private static void writeModel(Path target, String content) throws Exception {
        String json = """
                {
                  "parent": "minecraft:block/template_cauldron_full",
                  "display": {
                    "gui": { "rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625] }
                  },
                  "gui_light": "side",
                  "textures": {
                    "bottom": "minecraft:block/cauldron_bottom",
                    "content": "%s",
                    "inside": "minecraft:block/cauldron_inner",
                    "particle": "minecraft:block/cauldron_side",
                    "side": "minecraft:block/cauldron_side",
                    "top": "minecraft:block/cauldron_top"
                  }
                }
                """.formatted(content);
        Files.createDirectories(target.getParent());
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Vanilla's animated water strip: 16 pixels wide, one 16x16 frame per 16 rows. */
    private static BufferedImage water(Path source) throws Exception {
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Missing vanilla water texture: " + source);
        }
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null || image.getWidth() != 16 || image.getHeight() % 16 != 0) {
            throw new IllegalArgumentException("Not an animated 16-wide water texture: " + source);
        }
        return image;
    }

    /** A static source texture; the cauldron body textures are 16x16 and are not animated. */
    private static BufferedImage source(Path path) throws Exception {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Missing texture: " + path);
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IllegalArgumentException("Not a readable texture: " + path);
        }
        return image;
    }

    /** The animation metadata next to the source, so the baked copy animates at the same speed. */
    private static String animationMeta(Path source) throws Exception {
        Path meta = source.resolveSibling(source.getFileName().toString() + ".mcmeta");
        if (Files.exists(meta)) {
            return Files.readString(meta, StandardCharsets.UTF_8);
        }
        return """
                {
                  "animation": {
                    "frametime": 2
                  }
                }
                """;
    }

    private static void writeTinted(BufferedImage source, int rgb, Path target, String animation)
            throws Exception {
        write(tint(source, rgb), target);
        if (animation != null) {
            Files.writeString(Path.of(target + ".mcmeta"), animation, StandardCharsets.UTF_8);
        }
    }

    private static int[] rgb(int value) {
        return new int[] { (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF };
    }

    private static int[] mix(int[] from, int[] to, float factor) {
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = Math.round(from[i] * (1 - factor) + to[i] * factor);
        }
        return out;
    }

    private static int packed(int[] channels) {
        return (channels[0] << 16) | (channels[1] << 8) | channels[2];
    }

    private static BufferedImage tint(BufferedImage source, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int sr = (argb >> 16) & 0xFF, sg = (argb >> 8) & 0xFF, sb = argb & 0xFF;
                out.setRGB(x, y, (a << 24) | ((sr * r / 255) << 16) | ((sg * g / 255) << 8) | (sb * b / 255));
            }
        }
        return out;
    }

    private static void write(BufferedImage image, Path target) throws Exception {
        ImageIO.write(image, "png", target.toFile());
    }
}
