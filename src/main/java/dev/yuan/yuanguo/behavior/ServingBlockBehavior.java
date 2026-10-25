package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.YuanGuoDelight;
import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import dev.yuan.yuanguo.behavior.util.BehaviorSupport;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A dish that is placed as a block and eaten from, one serving at a time.
 *
 * <p>The mod builds these from two vanilla classes: a cake-like block whose {@code bites} counts what
 * is left, and Farmer's Delight's feast block whose {@code servings} does the same. Emptying the last
 * serving takes the block down and leaves the bowl behind. The mod's fig pudding can also be cut with
 * a knife, which drops a slice without feeding anyone.</p>
 *
 * <p>The block cannot express any of that on its own, so the whole state machine lives here and the
 * generated block only declares which item feeds the player and which one a knife yields. The same
 * {@code interact} core serves both the CraftEngine behavior callbacks and the
 * {@link FeastInteractListener}, which runs at CraftEngine's earliest interaction hook.</p>
 */
public final class ServingBlockBehavior extends BukkitBlockBehavior {

    public static final Key KEY = Key.of("yuanguo", "serving_block");
    public static final BlockBehaviorFactory<ServingBlockBehavior> FACTORY = ServingBlockBehavior::new;

    private final IntegerProperty servings;
    private final int full;
    private final int empty;
    private final Key sliceItem;
    private final boolean slice;
    private final boolean takeWithBowl;
    private final Key servingItem;
    private final boolean keepEmpty;
    private final int food;
    private final float saturation;
    private final Key effectItem;

    private ServingBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        String property = BehaviorConfig.string(section, "bites", "property");
        this.servings = (IntegerProperty) BlockBehaviorFactory.getProperty(
                section.path(), block, property, Integer.class);
        this.full = this.servings.max;
        this.empty = this.servings.min;
        this.sliceItem = BehaviorConfig.optionalKey(section, "slice_item");
        this.slice = BehaviorConfig.bool(section, false, "slice");
        this.takeWithBowl = BehaviorConfig.bool(section, false, "take_with_bowl");
        this.servingItem = BehaviorConfig.optionalKey(section, "serving_item");
        this.keepEmpty = BehaviorConfig.bool(section, false, "keep_empty");
        this.food = BehaviorConfig.intValue(section, 0, "eat_food", "food");
        this.saturation = BehaviorConfig.floatValue(section, 0.0F, "eat_saturation", "saturation");
        this.effectItem = BehaviorConfig.optionalKey(section, "effect_item");
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    private int left(ImmutableBlockState state) {
        return state.get(this.servings);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        Player player = bukkitPlayer(context);
        if (player == null) {
            return InteractionResult.PASS;
        }
        return interact(player, BehaviorSupport.worldOf(context.getLevel()), context.getClickedPos(),
                state, player.getInventory().getItemInMainHand());
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = bukkitPlayer(context);
        if (player == null) {
            return InteractionResult.PASS;
        }
        return interact(player, BehaviorSupport.worldOf(context.getLevel()), context.getClickedPos(),
                state, player.getInventory().getItemInMainHand());
    }

    /**
     * The whole feast state machine, shared by the behavior callbacks and the early event listener.
     *
     * <p>There is deliberately no full-hunger gate: the mod lets you take a bite whenever you click,
     * and a test click at full hunger still has to show the animation. Held block items are left alone
     * so a player can still place blocks against the dish.</p>
     */
    InteractionResult interact(Player player, World world, BlockPos pos, ImmutableBlockState state, ItemStack hand) {
        if (player == null || world == null || pos == null || state == null) {
            return InteractionResult.PASS;
        }
        int current = left(state);
        if (this.slice && isKnife(hand)) {
            return slice(world, pos, state, player, current);
        }
        if (this.takeWithBowl) {
            // The empty dish: clearing it must not eat the player's bowl or hand out another serving.
            if (current == this.empty) {
                return clear(world, pos, player);
            }
            if (hand == null || hand.getType() != Material.BOWL) {
                return InteractionResult.PASS;
            }
            if (!consumeBowl(player)) {
                return InteractionResult.PASS;
            }
            giveServing(player);
            place(world, pos, state.with(this.servings, current - 1));
            playEatSound(world, pos);
            player.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        // A placeable block in hand: let vanilla place it against the dish instead of eating.
        if (hand != null && !hand.getType().isAir() && hand.getType().isBlock()) {
            return InteractionResult.PASS;
        }
        feed(player);
        if (current > this.empty) {
            place(world, pos, state.with(this.servings, current - 1));
            playEatSound(world, pos);
            player.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        return clear(world, pos, player);
    }

    /** The last bite: the dish is gone and its bowl is left behind. */
    private InteractionResult clear(World world, BlockPos pos, Player player) {
        Location center = new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D);
        world.playSound(center, Sound.BLOCK_WOOD_BREAK, 0.8F, 0.8F);
        if (player != null) {
            player.swingMainHand();
        }
        CraftEngineBlocks.remove(world.getBlockAt(pos.x(), pos.y(), pos.z()));
        if (this.keepEmpty) {
            world.dropItemNaturally(center, new ItemStack(Material.BOWL));
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private void giveServing(Player player) {
        ItemStack serving = build(this.servingItem);
        if (serving == null) {
            return;
        }
        var leftover = player.getInventory().addItem(serving);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(
                    player.getLocation(), stack));
        }
    }

    private void feed(Player player) {
        if (this.food <= 0) {
            return;
        }
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + this.food));
        player.setSaturation(Math.min(player.getFoodLevel(),
                player.getSaturation() + this.saturation));
        if (this.effectItem != null) {
            YuanGuoDelight plugin = YuanGuoDelight.instance();
            if (plugin != null && plugin.effects() != null) {
                plugin.effects().grant(player, this.effectItem.asString());
            }
        }
    }

    private static boolean consumeBowl(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.BOWL) {
            return false;
        }
        hand.setAmount(hand.getAmount() - 1);
        return true;
    }

    // ------------------------------------------------------------------ slicing

    private InteractionResult slice(World world, BlockPos pos, ImmutableBlockState state, Player player, int current) {
        Location drop = new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D);
        ItemStack sliceStack = build(this.sliceItem);
        if (sliceStack != null) {
            world.dropItemNaturally(drop, sliceStack);
        }
        if (current > this.empty) {
            place(world, pos, state.with(this.servings, current - 1));
        } else {
            world.playSound(drop, Sound.BLOCK_WOOD_BREAK, 0.8F, 0.8F);
            CraftEngineBlocks.remove(world.getBlockAt(pos.x(), pos.y(), pos.z()));
        }
        player.swingMainHand();
        world.playSound(drop, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 0.8F);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    static boolean isKnife(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        try {
            Key id = CraftEngineItems.getCustomItemId(stack);
            if (id != null && id.value().endsWith("_knife")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return stack.getType().getKey().getKey().endsWith("_knife");
    }

    // ------------------------------------------------------------------ helpers

    private void place(World world, BlockPos pos, ImmutableBlockState state) {
        CraftEngineBlocks.place(new Location(world, pos.x(), pos.y(), pos.z()), state, false);
    }

    private void playEatSound(World world, BlockPos pos) {
        world.playSound(new Location(world, pos.x() + 0.5D, pos.y() + 0.5D, pos.z() + 0.5D),
                Sound.ENTITY_GENERIC_EAT, 0.8F, 1.0F);
    }

    private static Player bukkitPlayer(UseOnContext context) {
        try {
            Object platform = context.getPlayer().platformPlayer();
            return platform instanceof Player player ? player : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static ItemStack build(Key key) {
        if (key == null) {
            return null;
        }
        try {
            var definition = CraftEngineItems.byId(key);
            if (definition != null) {
                ItemStack stack = definition.buildBukkitItem();
                if (stack != null && !stack.getType().isAir()) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Material material = Material.matchMaterial(key.value());
            return material == null || !material.isItem() ? null : new ItemStack(material);
        } catch (Throwable ignored) {
            return null;
        }
    }
}