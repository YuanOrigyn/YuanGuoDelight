package dev.yuan.yuanguo.cauldron;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The parts of the mod's cauldron chain that are not a right-click on a custom cauldron.
 *
 * <p>The custom cauldrons handle their own clicks through {@link CauldronBlockBehavior}. What is left
 * is the vanilla water cauldron, an item entity that lands in the liquid, and a dispenser.</p>
 */
public final class CauldronListener implements Listener {

    /**
     * The mod lets a lemon slice or a jam turn a full vanilla water cauldron into a custom one.
     *
     * <p>Jam uses {@code yuanguo:sneak_place_block_item}, so it only places its block while the
     * player is sneaking; a normal click therefore falls through to this listener and the vanilla
     * cauldron keeps its own interaction (a bucket still fills and empties it).</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != Material.WATER_CAULDRON
                || !(block.getBlockData() instanceof Levelled levelled)
                || levelled.getLevel() != levelled.getMaximumLevel()) {
            return;
        }
        if (CauldronInteractions.apply(event.getPlayer(), block, null, hand,
                event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND)) {
            event.setCancelled(true);
            // The client predicted a block placement for the jam in hand; resync so it is not
            // left rendering a ghost block.
            event.getPlayer().updateInventory();
        }
    }

    /**
     * The mod also lets a dropped item react with the cauldron it falls into. CraftEngine does not
     * surface {@code entityInside} for custom blocks, so the vanilla event is used instead.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemInside(io.papermc.paper.event.entity.EntityInsideBlockEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        Block block = event.getBlock();
        ImmutableBlockState state = customState(block);
        if (state == null) {
            return;
        }
        ItemStack stack = item.getItemStack();
        if (CauldronInteractions.applyToItem(block, state, stack)) {
            if (stack.getAmount() <= 0) {
                item.remove();
            } else {
                item.setItemStack(stack);
            }
        }
    }

    /** A dispenser pointed at a cauldron runs the same recipe. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block target = event.getBlock().getRelative(dispenseFace(event.getBlock()));
        ImmutableBlockState state = customState(target);
        if (state == null) {
            return;
        }
        // The event item is a snapshot: shrinking it does not touch the dispenser. Keep a copy of
        // the single item to consume, run the recipe on another copy, then take the item out of the
        // container - otherwise one bottle of jam could be poured forever.
        ItemStack consumed = event.getItem().clone();
        consumed.setAmount(1);
        ItemStack input = event.getItem().clone();
        if (!CauldronInteractions.applyToItem(target, state, input)) {
            return;
        }
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            container.getInventory().removeItem(consumed);
        }
        event.setCancelled(true);
    }

    private static ImmutableBlockState customState(Block block) {
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            return state == null || state.isEmpty() ? null : state;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BlockFace dispenseFace(Block dispenser) {
        if (dispenser.getBlockData() instanceof Directional directional) {
            return directional.getFacing();
        }
        return BlockFace.DOWN;
    }
}