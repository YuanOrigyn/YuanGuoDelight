package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import dev.yuan.yuanguo.behavior.util.CraftEngineBlocksState;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitExistingBlock;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fruit bearing leaves.
 *
 * <p>Mirrors the original mod's three states: {@code leaves -> flowers -> fruits}. Flowers only appear
 * during the plant's season, fruit only ripens while that season runs, and once ripe the fruit can be
 * picked by hand (or forced with bone meal).</p>
 *
 * <p>Decay is handled here rather than by CraftEngine's built in {@code leaves_block} behaviour: that
 * behaviour requires {@code distance}/{@code persistent} block properties, which would multiply the
 * server side block states of every leaf by fourteen. Instead a random tick walks the connected
 * leaves looking for a trunk, so chopping the log of a tree makes its canopy fall exactly like the
 * vanilla leaves do.</p>
 */
public final class SeasonalFruitLeavesBlockBehavior extends BukkitBlockBehavior
        implements BonemealableBlock, RandomTickBlock {

    public static final Key KEY = Key.of("yuanguo", "seasonal_fruit_leaves");
    public static final BlockBehaviorFactory<SeasonalFruitLeavesBlockBehavior> FACTORY = SeasonalFruitLeavesBlockBehavior::new;

    private static final String STATE_LEAVES = "leaves";
    private static final String STATE_FLOWERS = "flowers";
    private static final String STATE_BUDDING = "budding";
    private static final String STATE_FRUITS = "fruits";

    private final String season;
    private final Key fruit;
    private final Property<String> typeProperty;
    private final int fruitMin;
    private final int fruitMax;
    private final double ripenChance;
    private final double bloomChance;
    private final double dropChance;
    private final boolean synchronizedBloom;
    private final double fruitChance;
    private final boolean fruitBelow;
    private final Key hangingBlock;

    private static final int VANILLA_DISTANCE = 7;
    private static final int MAX_SEARCH_NODES = 512;
    private static final int MAX_TREE_NODES = 1024;

    private SeasonalFruitLeavesBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.season = BehaviorConfig.season(section);
        this.fruit = BehaviorConfig.optionalKey(section, "fruit", "fruit_item");
        this.typeProperty = BlockBehaviorFactory.getProperty(section.path(), block, "type", String.class);
        this.fruitMin = BehaviorConfig.intValue(section, 1, "fruit_min", "min_fruit");
        this.fruitMax = Math.max(this.fruitMin, BehaviorConfig.intValue(section, 2, "fruit_max", "max_fruit"));
        this.ripenChance = BehaviorConfig.doubleValue(section, 0.3D, "ripen_chance");
        this.bloomChance = section.containsKey("bloom_chance")
                ? BehaviorConfig.doubleValue(section, this.ripenChance, "bloom_chance")
                : this.ripenChance;
        this.dropChance = BehaviorConfig.doubleValue(section, 0.05D, "drop_chance");
        // The peach tree blooms as a whole: one leaf of the canopy turns every leaf into flowers at
        // once, and a flower later splits the canopy into fruits and plain leaves. Every other tree
        // keeps ripening one leaf at a time.
        this.synchronizedBloom = BehaviorConfig.bool(section, false, "synchronized", "sync_bloom");
        this.fruitChance = BehaviorConfig.doubleValue(section, 0.3D, "fruit_chance");
        // The durian hangs its fruit: the leaf stays a leaf and writes a flower into the block
        // below it, which then grows on its own. Every other tree carries the fruit on the leaf.
        this.fruitBelow = BehaviorConfig.bool(section, false, "fruit_below", "hanging");
        this.hangingBlock = BehaviorConfig.optionalKey(section, "hanging_block", "fruit_block");
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    public String type(ImmutableBlockState state) {
        return state.get(this.typeProperty);
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
            // A leaf that can no longer reach a trunk falls off, just like a vanilla leaf after the
            // tree it belonged to has been chopped down.
            if (!isSupported(world, pos)) {
                decay(world, pos, state);
                return;
            }
            String type = type(state);
            boolean allowed = BehaviorSupport.growthAllowed(level, this.season);
            double factor = allowed ? BehaviorSupport.growthFactor(level, this.season) : 0.0D;
            if (this.fruitBelow) {
                tickHanging(world, pos, state, type, allowed, factor);
                return;
            }
            if (STATE_FRUITS.equals(type)) {
                if (RandomUtils.generateRandomFloat(0, 1) < this.dropChance) {
                    harvest(world, pos, state);
                }
                return;
            }
            double transitionChance = STATE_LEAVES.equals(type) ? this.bloomChance : this.ripenChance;
            if (!allowed || RandomUtils.generateRandomFloat(0, 1) >= transitionChance * factor) {
                return;
            }
            if (this.synchronizedBloom) {
                if (STATE_FLOWERS.equals(type)) {
                    ripenTree(world, pos);
                } else if (STATE_LEAVES.equals(type) && isAdjacentToLog(world, pos)) {
                    bloomTree(world, pos);
                }
            } else if (STATE_LEAVES.equals(type)) {
                place(world, pos, state.with(this.typeProperty, STATE_FLOWERS));
            } else if (STATE_FLOWERS.equals(type)) {
                place(world, pos, state.with(this.typeProperty, STATE_FRUITS));
                spread(world, pos, STATE_LEAVES, STATE_FLOWERS);
            }
        });
    }

    /**
     * Durian growth: the fruit hangs below the canopy instead of sitting on the leaf.
     *
     * <p>The original {@code DurianLeavesBlock} keeps a single leaf marked as <em>budding</em>. That
     * leaf writes a flower into the block below it, turns back into a plain leaf and then hands the
     * budding mark on to a random neighbouring leaf, so the canopy keeps producing one flower after
     * another. The flower ripens into the fruit on its own and, once ripe, the durian falls out of
     * the tree. CraftEngine cannot stack a second model onto the leaf state, so the flower and the
     * fruit live in {@code hanging_block} below the leaf.</p>
     */
    private void tickHanging(World world, BlockPos pos, ImmutableBlockState state,
                             String type, boolean allowed, double factor) {
        if (!STATE_BUDDING.equals(type)) {
            return;
        }
        if (!allowed || RandomUtils.generateRandomFloat(0, 1) >= this.bloomChance * factor) {
            return;
        }
        if (!createHangingFruit(world, pos)) {
            return;
        }
        // Back to a plain leaf, then pass the budding mark to a neighbouring leaf. When no
        // neighbour can take it the mark simply disappears, exactly like the original mod.
        place(world, pos, state.with(this.typeProperty, STATE_LEAVES));
        spread(world, pos, STATE_LEAVES, STATE_BUDDING);
    }

    /**
     * Hangs a flower under the leaf. The original mod only inspects the single block below and gives
     * up when that spot is taken, which is why a canopy only carries a few durians at a time.
     */
    private boolean createHangingFruit(World world, BlockPos pos) {
        if (this.hangingBlock == null) {
            return false;
        }
        BlockPos target = pos.below();
        org.bukkit.block.Block block = world.getBlockAt(target.x(), target.y(), target.z());
        if (!block.getType().isAir() && !block.isReplaceable()) {
            return false;
        }
        try {
            net.momirealms.craftengine.core.block.BlockDefinition definition =
                    CraftEngineBlocks.byId(this.hangingBlock);
            if (definition == null) {
                return false;
            }
            return CraftEngineBlocks.place(new Location(world, target.x(), target.y(), target.z()),
                    definition.defaultState(), false);
        } catch (Throwable ignored) {
            return false;
        }
    }
    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        if (!STATE_FRUITS.equals(type(state))) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel().platformWorld() instanceof World world)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        dropFruit(world, pos, RandomUtils.generateRandomInt(this.fruitMin, this.fruitMax + 1));
        place(world, pos, state.with(this.typeProperty, STATE_LEAVES));
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /**
     * Turns the whole canopy of this tree into flowers. The original {@code PeachLeavesBlock} scans
     * every connected leaf when one leaf next to the trunk ticks, so an entire peach tree bursts into
     * bloom at once instead of flowering leaf by leaf.
     */
    private void bloomTree(World world, BlockPos origin) {
        List<BlockPos> canopy = scanTree(world, origin);
        for (BlockPos leaf : canopy) {
            ImmutableBlockState other = CraftEngineBlocksState.of(world, leaf);
            SeasonalFruitLeavesBlockBehavior behavior = behaviorOf(other);
            if (behavior != null && STATE_FRUITS.equals(behavior.type(other))) {
                return;
            }
        }
        for (BlockPos leaf : canopy) {
            ImmutableBlockState other = CraftEngineBlocksState.of(world, leaf);
            SeasonalFruitLeavesBlockBehavior behavior = behaviorOf(other);
            if (behavior == null || !STATE_LEAVES.equals(behavior.type(other))) {
                continue;
            }
            place(world, leaf, other.with(this.typeProperty, STATE_FLOWERS));
        }
    }

    /**
     * Splits the flowered canopy into ripe fruit and plain leaves. Leaves that already carry fruit
     * drop it first, mirroring the original mod's flower -> fruit transition.
     */
    private void ripenTree(World world, BlockPos origin) {
        for (BlockPos leaf : scanTree(world, origin)) {
            ImmutableBlockState other = CraftEngineBlocksState.of(world, leaf);
            SeasonalFruitLeavesBlockBehavior behavior = behaviorOf(other);
            if (behavior == null) {
                continue;
            }
            if (STATE_FRUITS.equals(behavior.type(other))) {
                dropFruit(world, leaf, RandomUtils.generateRandomInt(this.fruitMin, this.fruitMax + 1));
            }
            if (RandomUtils.generateRandomFloat(0, 1) < this.fruitChance) {
                place(world, leaf, other.with(this.typeProperty, STATE_FRUITS));
            } else {
                place(world, leaf, other.with(this.typeProperty, STATE_LEAVES));
            }
        }
    }

    /** Collects every connected leaf that belongs to the same custom block, i.e. one whole tree. */
    private List<BlockPos> scanTree(World world, BlockPos origin) {
        List<BlockPos> canopy = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> frontier = new ArrayList<>();
        visited.add(origin);
        frontier.add(origin);
        canopy.add(origin);
        while (!frontier.isEmpty()) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos pos : frontier) {
                for (BlockPos neighbour : neighbours(pos)) {
                    if (!visited.add(neighbour)) {
                        continue;
                    }
                    ImmutableBlockState state = CraftEngineBlocksState.of(world, neighbour);
                    if (behaviorOf(state) == null) {
                        continue;
                    }
                    canopy.add(neighbour);
                    next.add(neighbour);
                    if (canopy.size() >= MAX_TREE_NODES) {
                        return canopy;
                    }
                }
            }
            frontier = next;
        }
        return canopy;
    }

    private SeasonalFruitLeavesBlockBehavior behaviorOf(ImmutableBlockState state) {
        if (state == null || state.behavior() == null) {
            return null;
        }
        SeasonalFruitLeavesBlockBehavior behavior =
                state.behavior().getFirst(SeasonalFruitLeavesBlockBehavior.class);
        if (behavior == null || !behavior.block().id().equals(this.block().id())) {
            return null;
        }
        return behavior;
    }

    private boolean isAdjacentToLog(World world, BlockPos pos) {
        for (BlockPos neighbour : neighbours(pos)) {
            if (isLog(world.getBlockAt(neighbour.x(), neighbour.y(), neighbour.z()))) {
                return true;
            }
        }
        return false;
    }

    private void harvest(World world, BlockPos pos, ImmutableBlockState state) {
        dropFruit(world, pos, RandomUtils.generateRandomInt(this.fruitMin, this.fruitMax + 1));
        place(world, pos, state.with(this.typeProperty, STATE_LEAVES));
    }

    /**
     * Walks the connected leaves looking for a log, the same range the vanilla {@code distance}
     * property covers before a leaf saturates and starts decaying. Chopping the trunk therefore makes
     * the canopy fall exactly like vanilla leaves do.
     */
    private boolean isSupported(World world, BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> frontier = new ArrayList<>();
        visited.add(origin);
        frontier.add(origin);
        // Vanilla leaves carry a distance value that saturates at seven; a leaf further than six
        // leaf steps from every log is the one that decays. Mirroring that bound keeps this walk
        // short even for the widest canopy.
        for (int step = 0; step < VANILLA_DISTANCE - 1; step++) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos pos : frontier) {
                for (BlockPos neighbour : neighbours(pos)) {
                    if (!visited.add(neighbour)) {
                        continue;
                    }
                    org.bukkit.block.Block block = world.getBlockAt(neighbour.x(), neighbour.y(), neighbour.z());
                    if (isLog(block)) {
                        return true;
                    }
                    if (isLeaves(block)) {
                        next.add(neighbour);
                    }
                    if (visited.size() >= MAX_SEARCH_NODES) {
                        // A cluster this large is always anchored to a trunk; bailing out keeps a
                        // pathological build from turning every random tick into a long walk.
                        return true;
                    }
                }
            }
            if (next.isEmpty()) {
                return false;
            }
            frontier = next;
        }
        return false;
    }

    private boolean isLog(org.bukkit.block.Block block) {
        if (Tag.LOGS.isTagged(block.getType())) {
            return true;
        }
        ImmutableBlockState state = customState(block);
        if (state == null) {
            return false;
        }
        Key id = state.behavior().block().id();
        String path = id == null ? "" : id.value();
        return path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private boolean isLeaves(org.bukkit.block.Block block) {
        if (Tag.LEAVES.isTagged(block.getType())) {
            return true;
        }
        return CraftEngineBlocksState.isSeasonalFruitLeaves(customState(block));
    }

    private ImmutableBlockState customState(org.bukkit.block.Block block) {
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            return state == null || state.isEmpty() || state.behavior() == null ? null : state;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void decay(World world, BlockPos pos, ImmutableBlockState state) {
        org.bukkit.block.Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        LeavesDecayEvent event = new LeavesDecayEvent(block);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Location location = new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D);
        List<Item> drops = rollDrops(world, pos, state, block);
        CraftEngineBlocks.remove(block);
        for (Item item : drops) {
            ItemStack stack = toStack(item);
            if (stack != null && !stack.getType().isAir()) {
                world.dropItemNaturally(location, stack);
            }
        }
    }

    private List<Item> rollDrops(World world, BlockPos pos, ImmutableBlockState state, org.bukkit.block.Block block) {
        try {
            Object adapted = BukkitAdaptor.adapt(world);
            if (adapted instanceof net.momirealms.craftengine.core.world.World ceWorld) {
                WorldPosition position = new WorldPosition(ceWorld, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D);
                ContextHolder holder = ContextHolder.builder()
                        .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, state)
                        .withParameter(DirectContextParameters.POSITION, position)
                        .withParameter(DirectContextParameters.BLOCK, new BukkitExistingBlock(block))
                        .build();
                List<Item> drops = state.getDrops(holder, ceWorld, null);
                if (drops != null && !drops.isEmpty()) {
                    return drops;
                }
            }
        } catch (Throwable ignored) {
        }
        // The loot table cannot always be evaluated without a player, so a ripe leaf keeps dropping
        // its fruit as a fallback.
        List<Item> fallback = new ArrayList<>();
        if (STATE_FRUITS.equals(type(state)) && this.fruit != null) {
            try {
                Item item = Item.byId(this.fruit);
                if (item != null && !item.isEmpty()) {
                    fallback.add(item.copyWithCount(RandomUtils.generateRandomInt(this.fruitMin, this.fruitMax + 1)));
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private ItemStack toStack(Item item) {
        if (item == null) {
            return null;
        }
        try {
            if (item instanceof BukkitItem bukkitItem) {
                return bukkitItem.getBukkitItem();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object platform = item.platformItem();
            if (platform instanceof ItemStack stack) {
                return stack;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<BlockPos> neighbours(BlockPos pos) {
        return List.of(pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below());
    }

    private void dropFruit(World world, BlockPos pos, int count) {
        if (this.fruit == null || count <= 0) {
            return;
        }
        ItemStack stack = buildFruit();
        if (stack == null) {
            return;
        }
        stack.setAmount(Math.min(count, stack.getMaxStackSize()));
        world.dropItemNaturally(new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D), stack);
    }

    /**
     * Builds the fruit item, falling back to a vanilla item when the configured id is not a
     * CraftEngine item. The apple tree fruits into {@code minecraft:apple}, which CraftEngine does not
     * own, so {@code CraftEngineItems.byId} returns null for it.
     */
    private ItemStack buildFruit() {
        try {
            var definition = CraftEngineItems.byId(this.fruit);
            if (definition != null) {
                ItemStack stack = definition.buildBukkitItem();
                if (stack != null && !stack.getType().isAir()) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(this.fruit.value());
            return material == null || !material.isItem() ? null : new ItemStack(material);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void place(World world, BlockPos pos, ImmutableBlockState state) {
        try {
            CraftEngineBlocks.place(new Location(world, pos.x(), pos.y(), pos.z()), state, false);
        } catch (Throwable ignored) {
        }
    }

    private void spread(World world, BlockPos origin, String from, String to) {
        BlockPos target = pickNeighbour(world, origin, from);
        if (target == null) {
            return;
        }
        ImmutableBlockState other = CraftEngineBlocksState.of(world, target);
        if (other == null) {
            return;
        }
        place(world, target, other.with(this.typeProperty, to));
    }

    private BlockPos pickNeighbour(World world, BlockPos origin, String expected) {
        List<BlockPos> candidates = neighbours(origin);
        List<BlockPos> matches = new ArrayList<>(candidates.size());
        for (BlockPos candidate : candidates) {
            ImmutableBlockState state = CraftEngineBlocksState.of(world, candidate);
            SeasonalFruitLeavesBlockBehavior behavior =
                    state == null ? null : state.behavior().getFirst(SeasonalFruitLeavesBlockBehavior.class);
            if (behavior != null && behavior.block().id().equals(this.block().id())
                    && expected.equals(behavior.type(state))) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(RandomUtils.generateRandomInt(0, matches.size()));
    }

    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        // Bone meal only ripens a flower into fruit. Plain leaves stay plain, so a tree can never
        // be forced to fruit everywhere: this mirrors the original mod's isValidBonemealTarget.
        return BlockStateUtils.getOptionalCustomBlockState(args[2])
                .filter(state -> STATE_FLOWERS.equals(type(state)))
                .isPresent();
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {
        Object level = args[0];
        Object blockPos = args[2];
        BlockStateUtils.getOptionalCustomBlockState(args[3]).ifPresent(state -> {
            World world = BehaviorSupport.worldOf(level);
            if (world == null) {
                return;
            }
            BlockPos pos = LocationUtils.fromBlockPos(blockPos);
            if (STATE_FLOWERS.equals(type(state))) {
                place(world, pos, state.with(this.typeProperty, STATE_FRUITS));
            }
        });
    }
}
