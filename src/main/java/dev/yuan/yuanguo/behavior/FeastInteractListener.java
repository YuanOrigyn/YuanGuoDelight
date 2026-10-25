package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.YuanGuoDelight;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * Answers feast clicks at CraftEngine's earliest interaction hook.
 *
 * <p>CraftEngine fires {@link CustomBlockInteractEvent} for every custom block before it reaches the
 * behavior dispatch, which is also how the Farmer's Delight pack's feasts and the mooncake plugin
 * receive their clicks. Handling the feast here means a click can never be swallowed by the later
 * gates (empty-hand routing, interaction-tick bookkeeping or a denied use-item result), and the
 * whole mod behaviour - eating, knife slices and the bowl scoop - shares one code path with the
 * {@link ServingBlockBehavior} fallback.</p>
 */
public final class FeastInteractListener implements Listener {

    @EventHandler
    public void onCustomBlockInteract(CustomBlockInteractEvent event) {
        if (event.action() != CustomBlockInteractEvent.Action.RIGHT_CLICK) {
            return;
        }
        if (event.hand() != InteractionHand.MAIN_HAND) {
            return;
        }
        try {
            var state = event.blockState();
            var behaviors = state.behavior();
            if (behaviors == null) {
                return;
            }
            ServingBlockBehavior feast = behaviors.getFirst(ServingBlockBehavior.class);
            if (feast == null) {
                return;
            }
            Player player = event.player();
            ItemStack hand = player.getInventory().getItemInMainHand();
            InteractionResult result = feast.interact(player, event.bukkitBlock().getWorld(),
                    LocationUtils.toBlockPos(event.bukkitBlock().getLocation()), state, hand);
            if (result.success()) {
                event.setCancelled(true);
            }
        } catch (Throwable throwable) {
            YuanGuoDelight plugin = YuanGuoDelight.instance();
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Feast interaction failed", throwable);
            }
        }
    }
}