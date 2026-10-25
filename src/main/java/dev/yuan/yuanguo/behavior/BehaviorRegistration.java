package dev.yuan.yuanguo.behavior;

import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Registers every CraftEngine block behavior provided by this plugin. */
public final class BehaviorRegistration {

    private BehaviorRegistration() {
    }

    public static Map<String, Runnable> entries() {
        Map<String, Runnable> entries = new LinkedHashMap<>();
        entries.put("yuanguo:seasonal_crop", SeasonalCropBlockBehavior::register);
        entries.put("yuanguo:seasonal_sapling", SeasonalSaplingBlockBehavior::register);
        entries.put("yuanguo:seasonal_fruit_leaves", SeasonalFruitLeavesBlockBehavior::register);
        entries.put("yuanguo:seasonal_stem", SeasonalStemBlockBehavior::register);
        entries.put("yuanguo:cauldron", dev.yuan.yuanguo.cauldron.CauldronBlockBehavior::register);
        entries.put("yuanguo:serving_block", ServingBlockBehavior::register);
        entries.put("yuanguo:hanging_fruit", HangingFruitBlockBehavior::register);
        entries.put("yuanguo:potted_sapling", PottedSaplingBlockBehavior::register);
        entries.put("yuanguo:sneak_place_block_item", SneakPlaceBlockItemBehavior::register);
        entries.put("yuanguo:jelly_surface", JellyBlockBehavior::register);
        entries.put("yuanguo:jello_bounce", JelloBlockBehavior::register);
        return entries;
    }

    public static void registerAll(Plugin plugin) {
        entries().forEach((id, registration) -> {
            try {
                registration.run();
                plugin.getLogger().info("Registered block behavior " + id);
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.SEVERE, "Failed to register block behavior " + id, throwable);
            }
        });
    }

    public static void forEach(Consumer<String> consumer) {
        entries().keySet().forEach(consumer);
    }
}
