package dev.yuan.yuanguo.cauldron;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehavior;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.StringProperty;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the cauldron chain when a player clicks a custom cauldron.
 *
 * <p>The state machine itself lives in {@link CauldronInteractions}; this class only translates a
 * CraftEngine interaction into the Bukkit objects that code works with.</p>
 */
public final class CauldronBlockBehavior extends BukkitBlockBehavior {

    public static final Key KEY = Key.of("yuanguo", "cauldron");
    public static final BlockBehaviorFactory<CauldronBlockBehavior> FACTORY = CauldronBlockBehavior::new;

    private final String liquid;
    private final CauldronKind kind;
    private final String fruit;
    private final int fruitIndex;
    private final StringProperty kindProperty;
    private final IntegerProperty levelProperty;

    private CauldronBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.liquid = section.getString("liquid", "lemonade");
        this.kind = "lemonade".equals(this.liquid) ? CauldronKind.LEMONADE : CauldronKind.FRUIT;
        this.fruit = this.kind == CauldronKind.LEMONADE ? null : this.liquid;
        this.fruitIndex = this.fruit == null ? -1 : CauldronFruits.indexOf(this.fruit);
        this.kindProperty = block.hasProperty("kind")
                ? (StringProperty) block.getProperty("kind") : null;
        this.levelProperty = block.hasProperty("level")
                ? (IntegerProperty) block.getProperty("level") : null;
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    /** The behavior behind a cauldron state, or {@code null} when the block is not one of ours. */
    @Nullable
    public static CauldronBlockBehavior of(@Nullable ImmutableBlockState state) {
        if (state == null || state.behavior() == null) {
            return null;
        }
        BlockBehavior behavior = state.behavior();
        return behavior.getFirst(CauldronBlockBehavior.class);
    }

    public String liquid() {
        return this.liquid;
    }

    public CauldronKind kind() {
        return this.kind;
    }

    /** The shape this particular state holds, read from the {@code kind} property. */
    public CauldronKind kind(ImmutableBlockState state) {
        if (this.kind == CauldronKind.LEMONADE) {
            return CauldronKind.LEMONADE;
        }
        String value = this.kindProperty == null ? null : state.get(this.kindProperty);
        return value == null ? this.kind : CauldronKind.fromState(value);
    }

    public String fruit() {
        return this.fruit;
    }

    public int fruitIndex() {
        return this.fruitIndex;
    }

    @Nullable
    public StringProperty kindProperty() {
        return this.kindProperty;
    }

    @Nullable
    public IntegerProperty levelProperty() {
        return this.levelProperty;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        return interact(context, state);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        return InteractionResult.PASS;
    }

    private InteractionResult interact(UseOnContext context, ImmutableBlockState state) {
        if (!dev.yuan.yuanguo.YuanGuoDelight.instance().settings().cauldron()) {
            return InteractionResult.PASS;
        }
        Player cePlayer = context.getPlayer();
        if (cePlayer == null
                || !(cePlayer.platformPlayer() instanceof org.bukkit.entity.Player player)) {
            return InteractionResult.PASS;
        }
        Object platformWorld = context.getLevel().platformWorld();
        if (!(platformWorld instanceof World world)) {
            return InteractionResult.PASS;
        }
        BlockPos position = context.getClickedPos();
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (CauldronInteractions.apply(player, block, state, hand)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // Not a recipe, but the block still must not fall through to vanilla placement.
        return InteractionResult.PASS;
    }
}
