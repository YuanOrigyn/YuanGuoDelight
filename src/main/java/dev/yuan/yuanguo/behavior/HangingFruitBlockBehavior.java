package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import dev.yuan.yuanguo.behavior.util.CraftEngineBlocksState;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BlocksProxy;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A durian that hangs below a canopy leaf.
 *
 * <p>The original {@code DurianLeavesBlock} grows the fruit on the leaf block itself: a budding leaf
 * writes a flower into the block below it, and that block then walks
 * {@code flowers -> small -> fruits}. CraftEngine cannot stack a second model onto one block state,
 * so the flower and the fruit live in this separate block placed under the leaf instead. Once the
 * flower has ripened the fruit falls out of the tree exactly like the mod's {@code DurianBlock}.</p>
 */
public final class HangingFruitBlockBehavior extends BukkitBlockBehavior implements RandomTickBlock {

    public static final Key KEY = Key.of("yuanguo", "hanging_fruit");
    public static final BlockBehaviorFactory<HangingFruitBlockBehavior> FACTORY = HangingFruitBlockBehavior::new;

    private static final String STATE_FLOWERS = "flowers";
    private static final String STATE_FRUITS = "fruits";

    private final String season;
    private final Key fallingBlock;
    private final Property<String> typeProperty;
    private final double growChance;
    private final double dropChance;

    private HangingFruitBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.season = BehaviorConfig.season(section);
        this.fallingBlock = BehaviorConfig.optionalKey(section, "falling_block", "harvest_block");
        this.typeProperty = BlockBehaviorFactory.getProperty(section.path(), block, "type", String.class);
        this.growChance = BehaviorConfig.doubleValue(section, 0.1D, "grow_chance");
        this.dropChance = BehaviorConfig.doubleValue(section, 0.1D, "drop_chance");
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
        BlockStateUtils.getOptionalCustomBlockState(args[0]).ifPresent(state -> {
            World world = BehaviorSupport.worldOf(level);
            if (world == null) {
                return;
            }
            BlockPos pos = LocationUtils.fromBlockPos(blockPos);
            // The fruit only lives while its leaf is still there; chopping the canopy makes it fall.
            if (!supported(world, pos)) {
                CraftEngineBlocks.remove(world.getBlockAt(pos.x(), pos.y(), pos.z()));
                return;
            }
            if (STATE_FRUITS.equals(state.get(this.typeProperty))) {
                if (RandomUtils.generateRandomFloat(0, 1) < this.dropChance) {
                    fall(world, pos);
                }
                return;
            }
            if (!BehaviorSupport.growthAllowed(level, this.season)) {
                return;
            }
            double factor = BehaviorSupport.growthFactor(level, this.season);
            if (RandomUtils.generateRandomFloat(0, 1) >= this.growChance * factor) {
                return;
            }
            place(world, pos, state.with(this.typeProperty, STATE_FRUITS));
        });
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        if (!STATE_FRUITS.equals(state.get(this.typeProperty))) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel().platformWorld() instanceof World world)) {
            return InteractionResult.PASS;
        }
        fall(world, context.getClickedPos());
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /**
     * The hanging block dies the moment its leaf is gone. {@code direction} points from this block
     * towards the neighbour that changed, so the leaf sits in the {@code UP} direction.
     */
    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        if (args[updateShape$direction] != DirectionProxy.UP) {
            return args[0];
        }
        ImmutableBlockState neighbor = BlockStateUtils
                .getOptionalCustomBlockState(args[updateShape$neighborState]).orElse(null);
        return CraftEngineBlocksState.isSeasonalFruitLeaves(neighbor) ? args[0] : BlocksProxy.AIR$defaultState;
    }

    private boolean supported(World world, BlockPos pos) {
        return CraftEngineBlocksState.isSeasonalFruitLeaves(CraftEngineBlocksState.of(world, pos.above()));
    }

    private void place(World world, BlockPos pos, ImmutableBlockState state) {
        try {
            CraftEngineBlocks.place(new Location(world, pos.x(), pos.y(), pos.z()), state, false);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Rips the ripe fruit off the tree. The mod drops a falling durian block here, so the same
     * falling block is written into this position and left to its own tick.
     */
    private void fall(World world, BlockPos pos) {
        if (this.fallingBlock != null) {
            try {
                if (CraftEngineBlocks.place(new Location(world, pos.x(), pos.y(), pos.z()),
                        this.fallingBlock, false)) {
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        CraftEngineBlocks.remove(world.getBlockAt(pos.x(), pos.y(), pos.z()));
    }
}