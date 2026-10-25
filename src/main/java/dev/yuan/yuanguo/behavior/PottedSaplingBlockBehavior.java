package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The CraftEngine side of a potted sapling.
 *
 * <p>A potted sapling's server side state is a plain {@code minecraft:flower_pot}, and the plant on
 * top is drawn by an {@code entity_renderer}. No vanilla state is borrowed or overridden, so the
 * vanilla {@code FlowerPotBlock} logic never runs on our block. The click is still answered by
 * {@link PottedSaplingListener#onCustomBlockInteract} through CraftEngine's own interaction hook -
 * the same hook the feast dishes use - and these behavior callbacks are kept as a fallback for the
 * paths where CraftEngine dispatches straight to the block behavior.</p>
 *
 * <ul>
 *   <li>holding any item at all is a no-op;</li>
 *   <li>a bare main hand takes the sapling back out and leaves an empty pot.</li>
 * </ul>
 */
public final class PottedSaplingBlockBehavior extends BukkitBlockBehavior {

    public static final Key KEY = Key.of("yuanguo", "potted_sapling");
    public static final BlockBehaviorFactory<PottedSaplingBlockBehavior> FACTORY = PottedSaplingBlockBehavior::new;

    private static final String NAMESPACE = "yuanguo";
    private static final String SAPLING_SUFFIX = "_sapling";
    private static final String POTTED_PREFIX = "potted_";

    private PottedSaplingBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    /** A held item must never reach the borrowed vanilla pot logic, so it is answered as a no-op. */
    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        if (context.getItem() != null && !context.getItem().isEmpty()) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return InteractionResult.TRY_EMPTY_HAND;
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        Player player = bukkitPlayer(context);
        if (player == null) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        ItemStack stack = saplingItem(saplingOf(state));
        if (stack == null) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        World world = BehaviorSupport.worldOf(context.getLevel());
        if (world == null) {
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        BlockPos pos = context.getClickedPos();
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        emptyPot(block);
        give(player, stack);
        world.playSound(block.getLocation().add(0.5D, 0.5D, 0.5D), Sound.BLOCK_GRASS_BREAK, 1.0F, 1.0F);
        player.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    /** The {@code yuanguo} sapling a potted block stands for, or {@code null}. */
    public static String saplingOf(ImmutableBlockState state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        Key id = state.owner().value().id();
        if (id == null || !NAMESPACE.equals(id.namespace())) {
            return null;
        }
        String value = id.value();
        if (!value.startsWith(POTTED_PREFIX) || !value.endsWith(SAPLING_SUFFIX)) {
            return null;
        }
        return value.substring(POTTED_PREFIX.length());
    }

    /** Replaces a potted block with a plain, empty flower pot. */
    public static void emptyPot(Block block) {
        try {
            CraftEngineBlocks.remove(block);
        } catch (Throwable ignored) {
        }
        block.setType(Material.FLOWER_POT, false);
    }

    /** Puts a stack into the player's main hand, or the inventory when that hand is occupied. */
    public static void give(Player player, ItemStack stack) {
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.getInventory().setItemInMainHand(stack);
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Builds the {@code yuanguo} sapling item, or {@code null} when it is unknown. */
    public static ItemStack saplingItem(String sapling) {
        if (sapling == null) {
            return null;
        }
        try {
            var definition = CraftEngineItems.byId(NAMESPACE + ":" + sapling);
            if (definition == null) {
                return null;
            }
            ItemStack stack = definition.buildBukkitItem();
            return stack == null || stack.getType().isAir() ? null : stack;
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** {@code yuanguo:pear_sapling} to {@code yuanguo:potted_pear_sapling}, or {@code null}. */
    public static Key pottedBlockOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Key id;
        try {
            id = CraftEngineItems.getCustomItemId(stack);
        } catch (Throwable throwable) {
            return null;
        }
        if (id == null || !NAMESPACE.equals(id.namespace()) || !id.value().endsWith(SAPLING_SUFFIX)) {
            return null;
        }
        String potted = NAMESPACE + ":" + POTTED_PREFIX
                + id.value().substring(0, id.value().length() - SAPLING_SUFFIX.length())
                + SAPLING_SUFFIX;
        Key key = Key.of(potted);
        return CraftEngineBlocks.byId(key) == null ? null : key;
    }

    private static Player bukkitPlayer(UseOnContext context) {
        try {
            Object platform = context.getPlayer().platformPlayer();
            return platform instanceof Player player ? player : null;
        } catch (Throwable throwable) {
            return null;
        }
    }
}