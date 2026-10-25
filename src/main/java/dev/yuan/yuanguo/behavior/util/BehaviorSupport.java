package dev.yuan.yuanguo.behavior.util;

import dev.yuan.yuanguo.season.SeasonService;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelReaderProxy;
import org.bukkit.World;

/** Stateless helpers shared by the season aware behaviors. */
public final class BehaviorSupport {

    private BehaviorSupport() {
    }

    public static World worldOf(Object level) {
        if (level == null) {
            return null;
        }
        try {
            return LevelProxy.INSTANCE.getWorld(level);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static int rawBrightness(Object level, Object blockPos) {
        try {
            return LevelReaderProxy.INSTANCE.getMaxLocalRawBrightness(level, blockPos);
        } catch (Throwable throwable) {
            return 15;
        }
    }

    public static int rawBrightnessAbove(Object level, Object blockPos) {
        try {
            return LevelReaderProxy.INSTANCE.getMaxLocalRawBrightness(
                    level, net.momirealms.craftengine.bukkit.util.LocationUtils.above(blockPos));
        } catch (Throwable throwable) {
            return 15;
        }
    }

    /**
     * @return the growth chance multiplier that applies right now, or a negative value when the plant
     * must not advance at all.
     */
    public static double growthFactor(Object level, String season) {
        SeasonService service = SeasonService.active();
        if (service == null) {
            return 1.0D;
        }
        World world = worldOf(level);
        if (world == null) {
            return 1.0D;
        }
        if (!service.allows(world, season)) {
            return -1.0D;
        }
        return service.multiplier(world, season);
    }

    public static boolean growthAllowed(Object level, String season) {
        return growthFactor(level, season) >= 0.0D;
    }
}