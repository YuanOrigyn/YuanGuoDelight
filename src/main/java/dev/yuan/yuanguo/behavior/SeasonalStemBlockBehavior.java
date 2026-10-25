package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.proxy.minecraft.core.BlockPosProxy;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.state.BlockBehaviourProxy;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

/**
 * A melon style stem that only grows and only spawns its fruit while the configured season is active.
 */
public final class SeasonalStemBlockBehavior extends BukkitBlockBehavior
        implements BonemealableBlock, RandomTickBlock {

    public static final Key KEY = Key.of("yuanguo", "seasonal_stem");
    public static final BlockBehaviorFactory<SeasonalStemBlockBehavior> FACTORY = SeasonalStemBlockBehavior::new;

    private final String season;
    private final IntegerProperty ageProperty;
    private final Key fruit;
    private final Key attachedStem;
    private final int lightRequirement;
    private final int maxLightRequirement;
    private final double fruitSpawnChance;

    private SeasonalStemBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.season = BehaviorConfig.season(section);
        this.ageProperty = (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class);
        this.fruit = BehaviorConfig.optionalKey(section, "fruit", "fruit_block");
        this.attachedStem = BehaviorConfig.optionalKey(section, "attached_stem", "attached");
        this.lightRequirement = BehaviorConfig.intValue(section, 9, "light_requirement", "min_light");
        this.maxLightRequirement = BehaviorConfig.intValue(section, 15, "max_light_requirement");
        this.fruitSpawnChance = BehaviorConfig.doubleValue(section, 0.5D, "fruit_spawn_chance");
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
        Object pos = args[2];
        if (!BehaviorSupport.growthAllowed(level, this.season)) {
            return;
        }
        int brightness = BehaviorSupport.rawBrightness(level, pos);
        if (brightness < this.lightRequirement || brightness > this.maxLightRequirement) {
            return;
        }
        double factor = BehaviorSupport.growthFactor(level, this.season);
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (state == null || state.isEmpty()) {
            return;
        }
        int age = state.get(this.ageProperty);
        if (age < this.ageProperty.max) {
            double chance = Math.min(1.0D, factor);
            if (chance < 1.0D && RandomUtils.generateRandomFloat(0, 1) >= chance) {
                return;
            }
            LevelWriterProxy.INSTANCE.setBlock(level, pos,
                    state.with(this.ageProperty, age + 1).customBlockState().minecraftState(), UpdateFlags.UPDATE_ALL);
            return;
        }
        if (RandomUtils.generateRandomFloat(0, 1) >= this.fruitSpawnChance * Math.min(1.0D, factor)) {
            return;
        }
        spawnFruit(level, pos);
    }

    private void spawnFruit(Object level, Object pos) {
        if (this.fruit == null || this.attachedStem == null) {
            return;
        }
        Object randomDirection = DirectionProxy.VALUES[RandomUtils.generateRandomInt(2, 6)];
        Object fruitPos = BlockPosProxy.INSTANCE.relative(pos, randomDirection);
        Object aboveState = BlockGetterProxy.INSTANCE.getBlockState(level, fruitPos);
        if (!BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isAir(aboveState)) {
            return;
        }
        Object belowPos = BlockPosProxy.INSTANCE.relative(fruitPos, DirectionProxy.DOWN);
        Object belowState = BlockGetterProxy.INSTANCE.getBlockState(level, belowPos);
        if (!BlockBehaviourProxy.BlockStateBaseProxy.INSTANCE.isSolidRender(belowState)) {
            return;
        }
        World world = BehaviorSupport.worldOf(level);
        if (world == null) {
            return;
        }
        net.momirealms.craftengine.core.world.BlockPos fruitBlockPos = LocationUtils.fromBlockPos(fruitPos);
        if (!CraftEngineBlocks.place(new Location(world, fruitBlockPos.x(), fruitBlockPos.y(), fruitBlockPos.z()),
                this.fruit, true)) {
            return;
        }
        Optional<BlockDefinition> optionalAttached = BukkitBlockManager.instance().blockById(this.attachedStem);
        if (optionalAttached.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Property<Direction> facing = (Property<Direction>) optionalAttached.get().getProperty("facing");
        if (facing == null) {
            return;
        }
        net.momirealms.craftengine.core.world.BlockPos stemPos = LocationUtils.fromBlockPos(pos);
        CraftEngineBlocks.place(new Location(world, stemPos.x(), stemPos.y(), stemPos.z()),
                optionalAttached.get().defaultState().with(facing, DirectionUtils.fromNMSDirection(randomDirection)),
                false);
    }

    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        return BlockStateUtils.getOptionalCustomBlockState(args[2])
                .filter(state -> state.get(this.ageProperty) != this.ageProperty.max)
                .isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object pos = args[2];
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[3]).orElse(null);
        if (state == null || state.isEmpty()) {
            return;
        }
        int age = state.get(this.ageProperty);
        if (age >= this.ageProperty.max) {
            spawnFruit(level, pos);
            return;
        }
        int next = Math.min(this.ageProperty.max, age + RandomUtils.generateRandomInt(2, 5));
        LevelWriterProxy.INSTANCE.setBlock(level, pos,
                state.with(this.ageProperty, next).customBlockState().minecraftState(), UpdateFlags.UPDATE_ALL);
    }
}