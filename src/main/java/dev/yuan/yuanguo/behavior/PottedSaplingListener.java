package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.YuanGuoDelight;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * Plants a custom sapling into a vanilla flower pot and hands the matching sapling back when the
 * pot is emptied.
 *
 * <p>The mod's saplings are {@code FlowerPotBlock}s in their own right, so right clicking an empty
 * pot with one replaces the pot with the matching {@code yuanguo:potted_*_sapling} block. The
 * CraftEngine pack cannot express that on its own - the {@code block_item} behavior would place the
 * sapling on top of the pot - so the interaction is intercepted here and answered with a direct
 * block placement.</p>
 *
 * <p>A potted sapling is an ordinary CraftEngine custom block whose server side state is a plain
 * {@code minecraft:flower_pot}; the plant on top is drawn by an {@code entity_renderer}. Nothing
 * about the vanilla pot is borrowed or overridden, so the vanilla potted blocks keep their own
 * look and the vanilla {@code FlowerPotBlock} logic never runs on our block. The click on a filled
 * pot is therefore answered through CraftEngine's own interaction hook - the same hook the feast
 * dishes use - in {@link #onCustomBlockInteract}.</p>
 */
public final class PottedSaplingListener implements Listener {

    /**
     * Plants a sapling into an empty flower pot. Clicking a filled pot is answered by
     * {@link #onCustomBlockInteract} through CraftEngine, so it is deliberately left alone here.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (clicked.getType() != Material.FLOWER_POT) {
            return;
        }
        // A filled pot is a CraftEngine custom block; its behaviour answers the click first.
        if (customPottedSapling(clicked) != null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Key potted = PottedSaplingBlockBehavior.pottedBlockOf(hand);
        if (potted == null) {
            return;
        }
        try {
            if (!CraftEngineBlocks.place(clicked.getLocation(), potted, true)) {
                return;
            }
        } catch (Throwable throwable) {
            YuanGuoDelight plugin = YuanGuoDelight.instance();
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Could not plant a sapling into a flower pot", throwable);
            }
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        clicked.getWorld().playSound(clicked.getLocation().add(0.5D, 0.5D, 0.5D),
                Sound.BLOCK_GRASS_PLACE, 1.0F, 1.0F);
        player.swingMainHand();
    }

    /**
     * Answers a click on a filled pot through CraftEngine, exactly like the feast dishes do.
     *
     * <ul>
     *   <li>holding any item at all is a no-op;</li>
     *   <li>a bare main hand takes the sapling back out and leaves an empty pot.</li>
     * </ul>
     */
    @EventHandler
    public void onCustomBlockInteract(CustomBlockInteractEvent event) {
        if (event.action() != CustomBlockInteractEvent.Action.RIGHT_CLICK) {
            return;
        }
        String sapling = PottedSaplingBlockBehavior.saplingOf(event.blockState());
        if (sapling == null) {
            return;
        }
        Player player = event.player();
        // Veto the click for both hands; the client sends one packet per hand.
        event.setCancelled(true);
        // Only a bare main hand takes the plant back; anything else makes the click a no-op.
        if (event.hand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        ItemStack stack = PottedSaplingBlockBehavior.saplingItem(sapling);
        if (stack == null) {
            return;
        }
        PottedSaplingBlockBehavior.emptyPot(event.bukkitBlock());
        PottedSaplingBlockBehavior.give(player, stack);
    }

    /** Reads {@code yuanguo:potted_<sapling>} off a CraftEngine block, or {@code null}. */
    private static String customPottedSapling(Block block) {
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            return PottedSaplingBlockBehavior.saplingOf(state);
        } catch (Throwable throwable) {
            return null;
        }
    }
}