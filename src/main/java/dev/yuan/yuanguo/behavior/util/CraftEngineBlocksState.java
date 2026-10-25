package dev.yuan.yuanguo.behavior.util;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.World;

/** Null-safe lookups for CraftEngine custom block states at a Bukkit location. */
public final class CraftEngineBlocksState {

    private CraftEngineBlocksState() {
    }

    public static ImmutableBlockState of(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(
                    world.getBlockAt(pos.x(), pos.y(), pos.z()));
            return state == null || state.isEmpty() ? null : state;
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static boolean isSeasonalFruitLeaves(ImmutableBlockState state) {
        if (state == null) {
            return false;
        }
        return state.behavior() != null
                && state.behavior().getFirst(dev.yuan.yuanguo.behavior.SeasonalFruitLeavesBlockBehavior.class) != null;
    }
}