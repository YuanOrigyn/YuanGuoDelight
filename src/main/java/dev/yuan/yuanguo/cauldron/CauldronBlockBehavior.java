package dev.yuan.yuanguo.cauldron;

import dev.yuan.yuanguo.YuanGuoDelight;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehavior;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.StringProperty;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
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

    private final CauldronKind kind;
    private final String fruit;
    private final int fruitIndex;
    private final StringProperty kindProperty;
    private final IntegerProperty levelProperty;

    private CauldronBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        String liquid = section.getString("liquid", "lemonade");
        this.kind = "lemonade".equals(liquid) ? CauldronKind.LEMONADE : CauldronKind.FRUIT;
        this.fruit = this.kind == CauldronKind.LEMONADE ? null : liquid;
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
        YuanGuoDelight plugin = YuanGuoDelight.instance();
        if (plugin == null || !plugin.settings().cauldron()) {
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
        boolean offHand = context.getHand() == InteractionHand.OFF_HAND;
        ItemStack hand = handOf(context, player, offHand);
        if (CauldronInteractions.apply(player, block, state, hand, offHand)) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // Not a recipe: let CraftEngine continue with the normal interaction (placing a block
        // against the cauldron, using the held item, ...).
        return InteractionResult.PASS;
    }

    /**
     * The stack the click was made with. The context carries the item the server used, which is not
     * necessarily the main hand (off-hand clicks would otherwise consume the wrong item).
     */
    private static ItemStack handOf(UseOnContext context, org.bukkit.entity.Player player, boolean offHand) {
        try {
            net.momirealms.craftengine.core.item.Item item = context.getItem();
            if (item != null) {
                Object platform = item.platformItem();
                if (platform instanceof ItemStack stack) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the Bukkit inventory below.
        }
        return offHand ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }
}
