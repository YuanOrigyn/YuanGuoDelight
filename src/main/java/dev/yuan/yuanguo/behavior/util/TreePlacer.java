package dev.yuan.yuanguo.behavior.util;

import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.proxy.minecraft.core.HolderProxy;
import net.momirealms.craftengine.proxy.minecraft.server.level.ServerChunkCacheProxy;
import net.momirealms.craftengine.proxy.minecraft.server.level.ServerLevelProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.levelgen.feature.ConfiguredFeatureProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.material.FluidStateProxy;

/** Places a CraftEngine configured feature (a tree) at a block position. */
public final class TreePlacer {

    private TreePlacer() {
    }

    public static boolean place(Key feature, Object level, Object blockPos, Object randomSource, Object previousState) {
        if (feature == null) {
            return false;
        }
        try {
            BukkitWorldManager manager = BukkitWorldManager.instance();
            Object holder = manager == null ? null : manager.configuredFeatureHolderById(feature);
            if (holder == null) {
                CraftEngine.instance().logger().warn("Configured feature not found: " + feature);
                return false;
            }
            Object chunkGenerator = ServerChunkCacheProxy.INSTANCE.getGenerator(
                    ServerLevelProxy.INSTANCE.getChunkSource(level));
            Object configuredFeature = HolderProxy.INSTANCE.value(holder);
            Object fluidState = BlockGetterProxy.INSTANCE.getFluidState(level, blockPos);
            Object legacyState = FluidStateProxy.INSTANCE.createLegacyBlock(fluidState);
            LevelWriterProxy.INSTANCE.setBlock(level, blockPos, legacyState, 0);
            if (ConfiguredFeatureProxy.INSTANCE.place(configuredFeature, level, chunkGenerator, randomSource, blockPos)) {
                if (BlockGetterProxy.INSTANCE.getBlockState(level, blockPos) == legacyState) {
                    ServerLevelProxy.INSTANCE.sendBlockUpdated(level, blockPos, previousState, legacyState, 3);
                }
                return true;
            }
            LevelWriterProxy.INSTANCE.setBlock(level, blockPos, previousState, 0);
            return false;
        } catch (Throwable throwable) {
            CraftEngine.instance().logger().warn("Failed to place feature " + feature, throwable);
            return false;
        }
    }
}