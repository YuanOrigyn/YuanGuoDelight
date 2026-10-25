package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import dev.yuan.yuanguo.behavior.util.CraftEngineBlocksState;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import net.momirealms.craftengine.proxy.minecraft.core.AxisProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelWriterProxy;
import net.momirealms.craftengine.proxy.minecraft.core.DirectionProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.block.BlocksProxy;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.event.CraftEventFactoryProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * A growth-stage block (fruit bushes, pineapple, hamimelon stem) whose advancement is limited to a
 * single RealisticSeasons season.
 *
 * <p>Fruits Delight expresses that restriction with SereneSeasons crop tags, which CraftEngine custom
 * blocks cannot join, so the gate is implemented here instead.</p>
 */
public final class SeasonalCropBlockBehavior extends BukkitBlockBehavior
        implements BonemealableBlock, RandomTickBlock {

    public static final Key KEY = Key.of("yuanguo", "seasonal_crop");
    public static final BlockBehaviorFactory<SeasonalCropBlockBehavior> FACTORY = SeasonalCropBlockBehavior::new;

    private final String season;
    private final IntegerProperty ageProperty;
    private final int maxAge;
    private final double growSpeed;
    private final int lightRequirement;
    private final int maxLightRequirement;
    private final boolean boneMealTarget;
    private final double boneMealBonus;
    private final net.momirealms.craftengine.core.util.Key harvestItem;
    private final int harvestMin;
    private final int harvestMax;
    private final Integer harvestResetAge;
    private final boolean harvestRemove;
    private final boolean harvestOnRightClick;
    private final Integer doubleHighFrom;
    private final Property<DoubleBlockHalf> halfProperty;

    private SeasonalCropBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.season = BehaviorConfig.season(section);
        this.ageProperty = (IntegerProperty) BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class);
        this.maxAge = this.ageProperty.max;
        this.growSpeed = BehaviorConfig.optionalDouble(section, 0.2D, "grow_speed", "growth_speed");
        this.lightRequirement = BehaviorConfig.intValue(section, 9, "light_requirement", "min_light");
        this.maxLightRequirement = BehaviorConfig.intValue(section, 15, "max_light_requirement");
        this.boneMealTarget = BehaviorConfig.bool(section, true, "is_bone_meal_target", "bone_meal_target");
        this.boneMealBonus = BehaviorConfig.optionalDouble(section, 1.0D, "bone_meal_age_bonus", "bone_meal_bonus");
        this.harvestItem = BehaviorConfig.optionalKey(section, "harvest_item", "pick_item");
        this.harvestMin = BehaviorConfig.intValue(section, 1, "harvest_min", "pick_min");
        this.harvestMax = Math.max(this.harvestMin, BehaviorConfig.intValue(section, 2, "harvest_max", "pick_max"));
        this.harvestResetAge = section.containsKey("harvest_reset_age")
                ? BehaviorConfig.intValue(section, 2, "harvest_reset_age") : null;
        this.harvestRemove = BehaviorConfig.bool(section, false, "harvest_remove");
        this.harvestOnRightClick = BehaviorConfig.bool(section, true, "harvest_on_right_click");
        this.doubleHighFrom = section.containsKey("double_high_from")
                ? BehaviorConfig.intValue(section, 2, "double_high_from") : null;
        Property<?> half = block.getProperty("half");
        this.halfProperty = half instanceof Property<?> ? (Property<DoubleBlockHalf>) half : null;
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    private int age(ImmutableBlockState state) {
        return state.get(this.ageProperty);
    }

    private boolean mature(ImmutableBlockState state) {
        return age(state) >= this.maxAge;
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        // Only the lower half of a two-block plant grows; the upper half is kept in step by
        // updateUpperHalf, exactly like the mod's DoubleBushBlock. A fully grown two-block plant
        // keeps ticking so a missing upper half (for example after world generation) is restored.
        if (this.halfProperty != null && state.getNullable(this.halfProperty) == DoubleBlockHalf.UPPER) {
            return false;
        }
        if (mature(state)) {
            return this.doubleHighFrom != null;
        }
        return true;
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object level = args[1];
        Object blockPos = args[2];
        // A fully grown two-block plant only needs its upper half kept alive.
        ImmutableBlockState tickState = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (tickState != null && !tickState.isEmpty() && mature(tickState)) {
            if (this.doubleHighFrom != null && age(tickState) >= this.doubleHighFrom) {
                updateUpperHalf(level, blockPos, tickState, age(tickState));
            }
            return;
        }
        if (!BehaviorSupport.growthAllowed(level, this.season)) {
            return;
        }
        int brightness = BehaviorSupport.rawBrightness(level, blockPos);
        if (brightness < this.lightRequirement || brightness > this.maxLightRequirement) {
            return;
        }
        double factor = BehaviorSupport.growthFactor(level, this.season);
        double chance = this.growSpeed * factor;
        if (chance < 1.0D && java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        int steps = Math.max(1, (int) Math.floor(chance));
        BlockStateUtils.getOptionalCustomBlockState(args[0])
                .ifPresent(state -> advance(level, blockPos, state, steps));
    }

    /**
     * Hand-picking a ripe bush or pineapple.
     *
     * <p>The mod lets the player harvest a fruit bush by right-clicking it once it is fully grown:
     * the fruit drops and the plant falls back to the seedling stage. A pineapple instead drops its
     * whole fruit and the plant is consumed. Both are configured per block; without a
     * {@code harvest_item} the block is only breakable.</p>
     */
    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        if (!this.harvestOnRightClick) {
            return InteractionResult.PASS;
        }
        if (this.harvestItem == null || !mature(state)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel().platformWorld() instanceof World world)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        // A two-block plant is picked from either half, so the state that carries the age and the
        // spot the fruit drops from are resolved from the lower half.
        ImmutableBlockState harvestState = state;
        BlockPos dropPos = pos;
        if (this.halfProperty != null) {
            Object halfValue = state.getNullable(this.halfProperty);
            if (halfValue == DoubleBlockHalf.UPPER) {
                BlockPos below = pos.below();
                ImmutableBlockState lower = CraftEngineBlocksState.of(world, below);
                if (lower == null || lower.behavior().getFirst(SeasonalCropBlockBehavior.class) != this) {
                    return InteractionResult.PASS;
                }
                harvestState = lower;
                dropPos = below;
            }
        }
        if (!mature(harvestState)) {
            return InteractionResult.PASS;
        }
        int count = RandomUtils.generateRandomInt(this.harvestMin, this.harvestMax + 1);
        ItemStack stack = buildHarvest();
        if (stack != null && count > 0) {
            stack.setAmount(Math.min(count, stack.getMaxStackSize()));
            world.dropItemNaturally(new Location(world, dropPos.x() + 0.5D, dropPos.y() + 0.5D, dropPos.z() + 0.5D), stack);
        }
        Location location = new Location(world, dropPos.x(), dropPos.y(), dropPos.z());
        if (this.harvestRemove) {
            CraftEngineBlocks.remove(world.getBlockAt(dropPos.x(), dropPos.y(), dropPos.z()));
        } else if (this.harvestResetAge != null) {
            int reset = Math.max(this.ageProperty.min, Math.min(this.maxAge, this.harvestResetAge));
            ImmutableBlockState resetState = harvestState.with(this.ageProperty, reset);
            CraftEngineBlocks.place(location, resetState, false);
            if (this.doubleHighFrom != null && this.halfProperty != null) {
                ImmutableBlockState upper = CraftEngineBlocksState.of(world, dropPos.above());
                if (upper != null && upper.behavior().getFirst(SeasonalCropBlockBehavior.class) == this) {
                    if (reset < this.doubleHighFrom) {
                        // Picking a two-block plant back to a single-block age takes its upper half down.
                        CraftEngineBlocks.remove(world.getBlockAt(dropPos.x(), dropPos.y() + 1, dropPos.z()));
                    } else if (upper.get(this.ageProperty) != reset) {
                        // Keep the upper half on the same age, or it would still show the ripe fruit.
                        Location upperLocation = new Location(world, dropPos.x(), dropPos.y() + 1, dropPos.z());
                        CraftEngineBlocks.place(upperLocation, resetState.with(this.halfProperty, DoubleBlockHalf.UPPER), false);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /** Builds the harvested item, falling back to a vanilla material for ids CraftEngine does not own. */
    private ItemStack buildHarvest() {
        try {
            var definition = CraftEngineItems.byId(this.harvestItem);
            if (definition != null) {
                ItemStack stack = definition.buildBukkitItem();
                if (stack != null && !stack.getType().isAir()) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(this.harvestItem.value());
            return material == null || !material.isItem() ? null : new ItemStack(material);
        } catch (Throwable ignored) {
            return null;
        }
    }
    private void advance(Object level, Object blockPos, ImmutableBlockState state, int steps) {
        int before = age(state);
        if (before >= this.maxAge) {
            return;
        }
        int after = Math.min(this.maxAge, before + steps);
        if (after == before) {
            return;
        }
        ImmutableBlockState next = state.with(this.ageProperty, after);
        if (VersionHelper.isOrAbove1_21_5) {
            CraftEventFactoryProxy.INSTANCE.handleBlockGrowEvent(level, blockPos,
                    next.customBlockState().minecraftState(), UpdateFlags.UPDATE_CLIENTS);
        } else {
            CraftEventFactoryProxy.INSTANCE.handleBlockGrowEvent(level, blockPos,
                    next.customBlockState().minecraftState());
        }
        updateUpperHalf(level, blockPos, next, after);
    }

    /**
     * Grows or removes the second block of a two-block plant.
     *
     * <p>The lemon tree only becomes a two-block plant from age 2 upwards. CraftEngine's
     * {@code double_high_block} behavior pairs the two halves when a block is placed by a player,
     * but a plant grown by a random tick is written directly, so the upper half is placed here.</p>
     */
    private void updateUpperHalf(Object level, Object blockPos, ImmutableBlockState lower, int age) {
        if (this.doubleHighFrom == null || this.halfProperty == null) {
            return;
        }
        Object abovePos = LocationUtils.above(blockPos);
        ImmutableBlockState current = BlockStateUtils
                .getOptionalCustomBlockState(BlockGetterProxy.INSTANCE.getBlockState(level, abovePos)).orElse(null);
        boolean ours = current != null && !current.isEmpty()
                && current.behavior().getFirst(SeasonalCropBlockBehavior.class) == this;
        if (age < this.doubleHighFrom) {
            if (ours && current.getNullable(this.halfProperty) == DoubleBlockHalf.UPPER) {
                LevelWriterProxy.INSTANCE.setBlock(level, abovePos, BlocksProxy.AIR$defaultState, UpdateFlags.UPDATE_ALL);
            }
            return;
        }
        if (ours) {
            if (current.getNullable(this.halfProperty) == DoubleBlockHalf.UPPER
                    && current.get(this.ageProperty) != age) {
                ImmutableBlockState upper = lower.with(this.halfProperty, DoubleBlockHalf.UPPER);
                LevelWriterProxy.INSTANCE.setBlock(level, abovePos, upper.customBlockState().minecraftState(), UpdateFlags.UPDATE_ALL);
            }
            return;
        }
        if (BlockStateUtils.isReplaceable(BlockGetterProxy.INSTANCE.getBlockState(level, abovePos))) {
            ImmutableBlockState upper = lower.with(this.halfProperty, DoubleBlockHalf.UPPER);
            LevelWriterProxy.INSTANCE.setBlock(level, abovePos, upper.customBlockState().minecraftState(), UpdateFlags.UPDATE_ALL);
        }
    }

    /**
     * A two-block plant is always planted as its lower half. CraftEngine's own {@code double_high_block}
     * cannot be used here: it pairs the halves the moment the block is placed, which would give a
     * sapling (age 0/1) a full block on top, and its shape update tears the plant down when the space
     * above changes. The pairing is therefore driven entirely by the growth code below.
     */
    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        if (this.halfProperty == null) {
            return state;
        }
        if (this.doubleHighFrom != null) {
            BlockPos pos = context.getClickedPos();
            net.momirealms.craftengine.core.world.WorldAccessor world = context.getLevel();
            if (pos.y() >= world.worldHeight().getMaxBuildHeight() - 1) {
                return null;
            }
            if (!world.getBlockState(pos.x(), pos.y() + 1, pos.z()).replaceable()) {
                return null;
            }
        }
        return state.with(this.halfProperty, DoubleBlockHalf.LOWER);
    }

    /**
     * Restores the second block when a fully grown plant is written straight into the world (world
     * generation, {@code /setblock}), where no placement context exists to pair the halves.
     */
    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        if (this.doubleHighFrom == null || this.halfProperty == null) {
            return;
        }
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (state == null || state.isEmpty()) {
            return;
        }
        if (state.getNullable(this.halfProperty) != DoubleBlockHalf.LOWER) {
            return;
        }
        int age = age(state);
        if (age >= this.doubleHighFrom) {
            updateUpperHalf(args[1], args[2], state, age);
        }
    }

    /**
     * Keeps the two halves glued together. The upper half dies when its support is gone, exactly like
     * a vanilla double plant; the lower half is validated against its soil by the {@code bush_block}
     * behavior that is attached alongside this one.
     */
    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        if (this.doubleHighFrom == null || this.halfProperty == null) {
            return args[0];
        }
        Object direction = args[updateShape$direction];
        if (direction == null || DirectionProxy.INSTANCE.getAxis(direction) != AxisProxy.Y) {
            return args[0];
        }
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[0]).orElse(null);
        if (state == null || state.isEmpty()) {
            return args[0];
        }
        if (state.getNullable(this.halfProperty) == DoubleBlockHalf.UPPER && direction == DirectionProxy.DOWN) {
            ImmutableBlockState neighbor = BlockStateUtils
                    .getOptionalCustomBlockState(args[updateShape$neighborState]).orElse(null);
            if (neighbor == null || neighbor.isEmpty()
                    || neighbor.behavior().getFirst(SeasonalCropBlockBehavior.class) != this
                    || neighbor.getNullable(this.halfProperty) != DoubleBlockHalf.LOWER) {
                return BlocksProxy.AIR$defaultState;
            }
        }
        return args[0];
    }

    /** Breaking either half takes the whole plant with it, like the mod's double bush. */
    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {
        if (this.doubleHighFrom == null || this.halfProperty == null) {
            return args[2];
        }
        ImmutableBlockState state = BlockStateUtils.getOptionalCustomBlockState(args[2]).orElse(null);
        if (state == null || state.isEmpty()) {
            return args[2];
        }
        World world = BehaviorSupport.worldOf(args[0]);
        if (world == null) {
            return args[2];
        }
        BlockPos pos = LocationUtils.fromBlockPos(args[1]);
        BlockPos other = state.getNullable(this.halfProperty) == DoubleBlockHalf.UPPER ? pos.below() : pos.above();
        ImmutableBlockState otherState = CraftEngineBlocksState.of(world, other);
        if (otherState != null && otherState.behavior().getFirst(SeasonalCropBlockBehavior.class) == this) {
            CraftEngineBlocks.remove(world.getBlockAt(other.x(), other.y(), other.z()));
        }
        return args[2];
    }
    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        if (!this.boneMealTarget) {
            return false;
        }
        return BlockStateUtils.getOptionalCustomBlockState(args[2]).filter(state -> !mature(state)).isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[2];
        java.util.Optional<ImmutableBlockState> optional = BlockStateUtils.getOptionalCustomBlockState(args[3]);
        if (optional.isEmpty()) {
            return;
        }
        ImmutableBlockState state = optional.get();
        // Bone meal applied to the top half of a two-block plant has to grow the plant through
        // its lower half, or the growth is written at the upper position and the pair breaks.
        if (this.halfProperty != null && state.getNullable(this.halfProperty) == DoubleBlockHalf.UPPER) {
            World world = BehaviorSupport.worldOf(level);
            if (world == null) {
                return;
            }
            BlockPos pos = LocationUtils.fromBlockPos(blockPos);
            ImmutableBlockState lower = CraftEngineBlocksState.of(world, pos.below());
            if (lower == null || lower.behavior().getFirst(SeasonalCropBlockBehavior.class) != this) {
                return;
            }
            state = lower;
            blockPos = LocationUtils.below(blockPos);
        }
        int bonus = Math.max(1, (int) this.boneMealBonus);
        advance(level, blockPos, state, bonus);
    }
}
