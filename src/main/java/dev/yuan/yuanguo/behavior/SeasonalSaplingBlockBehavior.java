package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import dev.yuan.yuanguo.behavior.util.TreePlacer;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;

/**
 * A sapling that only advances to the next growth stage and finally spawns its tree while the
 * configured season is active.
 */
public final class SeasonalSaplingBlockBehavior extends BukkitBlockBehavior
        implements BonemealableBlock, RandomTickBlock {

    public static final Key KEY = Key.of("yuanguo", "seasonal_sapling");
    public static final BlockBehaviorFactory<SeasonalSaplingBlockBehavior> FACTORY = SeasonalSaplingBlockBehavior::new;

    private final String season;
    private final IntegerProperty stageProperty;
    private final Key feature;
    private final double boneMealSuccessChance;
    private final float growSpeed;
    private final int lightRequirement;
    private final int maxLightRequirement;

    private SeasonalSaplingBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.season = BehaviorConfig.season(section);
        this.stageProperty = (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "stage", Integer.class);
        this.feature = BehaviorConfig.optionalKey(section, "feature", "configured_feature");
        this.boneMealSuccessChance = BehaviorConfig.doubleValue(section, 0.45D, "bone_meal_success_chance");
        this.growSpeed = BehaviorConfig.floatValue(section, 0.1F, "grow_speed", "growth_speed");
        this.lightRequirement = BehaviorConfig.intValue(section, 9, "light_requirement", "min_light");
        this.maxLightRequirement = BehaviorConfig.intValue(section, 15, "max_light_requirement");
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return true;
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object level = args[1];
        Object blockPos = args[2];
        Object blockState = args[0];
        if (!BehaviorSupport.growthAllowed(level, this.season)) {
            return;
        }
        int brightness = BehaviorSupport.rawBrightnessAbove(level, blockPos);
        if (brightness < this.lightRequirement || brightness > this.maxLightRequirement) {
            return;
        }
        double factor = BehaviorSupport.growthFactor(level, this.season);
        float chance = (float) Math.min(1.0D, this.growSpeed * factor);
        if (RandomUtils.generateRandomFloat(0, 1) >= chance) {
            return;
        }
        BlockStateUtils.getOptionalCustomBlockState(blockState)
                .ifPresent(state -> advance(level, blockPos, state, blockState, args[3]));
    }

    private void advance(Object level, Object blockPos, ImmutableBlockState state, Object blockState, Object randomSource) {
        int stage = state.get(this.stageProperty);
        if (stage < this.stageProperty.max) {
            ImmutableBlockState next = state.cycle(this.stageProperty);
            placeCustom(level, blockPos, next);
        } else {
            TreePlacer.place(this.feature, level, blockPos, randomSource, blockState);
        }
    }

    private void placeCustom(Object level, Object blockPos, ImmutableBlockState next) {
        org.bukkit.World world = BehaviorSupport.worldOf(level);
        if (world == null) {
            return;
        }
        net.momirealms.craftengine.core.world.BlockPos pos = LocationUtils.fromBlockPos(blockPos);
        net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(
                new org.bukkit.Location(world, pos.x(), pos.y(), pos.z()), next, false);
    }

    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        return BlockStateUtils.getOptionalCustomBlockState(args[2]).isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return RandomUtils.generateRandomFloat(0, 1) < this.boneMealSuccessChance;
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[2];
        Object blockState = args[3];
        BlockStateUtils.getOptionalCustomBlockState(blockState)
                .ifPresent(state -> advance(level, blockPos, state, blockState, args[1]));
    }
}