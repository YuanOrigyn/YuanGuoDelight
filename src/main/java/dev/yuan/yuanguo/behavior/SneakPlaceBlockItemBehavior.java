package dev.yuan.yuanguo.behavior;

import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.context.UseOnContext;

import java.nio.file.Path;
import java.util.Map;

/**
 * A block item that only places its block while the player is sneaking.
 *
 * <p>This mirrors the mod's {@code FDBlockItem#place}, which returns {@code PASS} unless
 * {@code isShiftKeyDown()}. Jam is the item that needs it: without the guard the click on a water
 * cauldron places a jam block instead of turning the cauldron into the fruit's cauldron.</p>
 */
public final class SneakPlaceBlockItemBehavior extends BlockItemBehavior {
    public static final ItemBehaviorFactory<SneakPlaceBlockItemBehavior> FACTORY = new Factory();

    private SneakPlaceBlockItemBehavior(Key blockId) {
        super(blockId);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return super.useOnBlock(context);
    }

    public static void register() {
        ItemBehaviors.register(Key.of("yuanguo:sneak_place_block_item"), FACTORY);
    }

    private static final class Factory implements ItemBehaviorFactory<SneakPlaceBlockItemBehavior> {
        @Override
        public SneakPlaceBlockItemBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            ConfigValue blockValue = section.getNonNullValue("block", ConfigConstants.ARGUMENT_SECTION);
            if (blockValue.is(Map.class)) {
                BukkitBlockManager.instance().blockParser()
                        .addPendingConfigSection(new PendingConfigSection(pack, path, key, blockValue.getAsSection()));
                return new SneakPlaceBlockItemBehavior(key);
            }
            return new SneakPlaceBlockItemBehavior(blockValue.getAsIdentifier());
        }
    }
}