package dev.yuan.yuanguo.cauldron;

import dev.tako.papersdelight.api.heat.HeatSourceGate;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.StringProperty;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The whole cauldron chain from the mod, expressed against CraftEngine block states.
 *
 * <p>The mod drives this from {@code CauldronInteraction} maps keyed by the item in hand. A data pack
 * cannot express a recipe that mixes a block state with an item, so the same recipes are reproduced
 * here. The result of a recipe is returned rather than applied, so the same code serves a player
 * right-click, a dropped item and a dispenser.</p>
 */
public final class CauldronInteractions {

    /** One cauldron level is one bottle, the unit the mod's cauldron recipes are built on. */
    static final int LEVEL_VOLUME = 250;

    private CauldronInteractions() {
    }

    /**
     * @param handled whether a recipe matched, in which case the block was already changed
     * @param hand    what is left of the item that was used
     * @param outputs items the recipe produced
     */
    public record Result(boolean handled, ItemStack hand, List<ItemStack> outputs) {

        static Result miss(ItemStack hand) {
            return new Result(false, hand, List.of());
        }
    }

    /**
     * Applies one right-click to a cauldron and applies the outcome to the player.
     *
     * @param current the cauldron's CraftEngine state, or {@code null} for a vanilla water cauldron
     * @return {@code true} when the click was consumed
     */
    public static boolean apply(Player player, Block block, @Nullable ImmutableBlockState current,
                                ItemStack hand) {
        Result result = recipe(player, block, current, hand);
        if (!result.handled()) {
            return false;
        }
        if (player != null) {
            setHand(player, result.hand());
            for (ItemStack output : result.outputs()) {
                give(player, output);
            }
        }
        return true;
    }

    /** Runs a recipe for a dropped item, dropping the outputs at the cauldron. */
    public static boolean applyToItem(Block block, @Nullable ImmutableBlockState current, ItemStack stack) {
        Result result = recipe(null, block, current, stack);
        if (!result.handled()) {
            return false;
        }
        stack.setAmount(result.hand().getAmount());
        for (ItemStack output : result.outputs()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.9, 0.5), output);
        }
        return true;
    }

    // ------------------------------------------------------------------ recipes

    /**
     * @param player the player behind the interaction, or {@code null} when a dropped item or a
     *               dispenser triggered it
     */
    static Result recipe(@Nullable Player player, Block block, @Nullable ImmutableBlockState current,
                         ItemStack hand) {
        if (hand == null || hand.getType() == Material.AIR) {
            return Result.miss(hand);
        }
        // The mod has no fluid-container recipe on its cauldrons: jam is an item that only turns a
        // full vanilla water cauldron into the fruit's cauldron. NyanaFluid is therefore not consulted
        // here, so a jug or bucket cannot pour into (or out of) a custom cauldron.
        return current != null
                ? customCauldron(block, current, hand)
                : vanillaWaterCauldron(block, hand);
    }

    private static Result customCauldron(Block block, ImmutableBlockState current, ItemStack hand) {
        CauldronBlockBehavior behavior = CauldronBlockBehavior.of(current);
        if (behavior == null) {
            return Result.miss(hand);
        }
        String fruit = behavior.fruit();
        CauldronKind kind = behavior.kind(current);
        int index = behavior.fruitIndex();
        String id = itemId(hand);
        if (id == null) {
            return Result.miss(hand);
        }

        if (kind == CauldronKind.LEMONADE) {
            // Lemonade is the mixing base: a dose of any fruit turns it into that fruit's cauldron
            // at the level one fruit is worth. The fruit comes from the item, not from the cauldron.
            String mixed = CauldronFruits.fruitForItem(id);
            if (mixed == null) {
                return Result.miss(hand);
            }
            // The mod runs this one through withHeat, so a lemonade cauldron only takes fruit when
            // it sits on a heat source.
            return transform(block, hand, mixed + "_cauldron", CauldronKind.FRUIT,
                    CauldronFruits.advance(CauldronFruits.indexOf(mixed)), true);
        }

        if (kind == CauldronKind.FRUIT) {
            int level = level(current, behavior);
            if (id.equals(CauldronFruits.fruitItem(fruit)) && level < 12) {
                return transform(block, hand, fruit + "_cauldron", CauldronKind.FRUIT,
                        Math.min(12, level + CauldronFruits.advance(index)), true);
            }
            if (id.equals("yuanguo:" + fruit + "_jelly") && level < 12) {
                return transform(block, hand, fruit + "_cauldron", CauldronKind.FRUIT,
                        Math.min(12, level + 4), false);
            }
            if (level >= 12 && id.equals("minecraft:sugar")) {
                return transform(block, hand, fruit + "_cauldron", CauldronKind.JELLY, 3, true);
            }
            return Result.miss(hand);
        }

        // Jelly and jello share their shape: level 1..3, slime turns jelly into jello, and a bottle
        // or a bowl takes one level out and hands the jam back.
        int level = level(current, behavior);
        String stem = fruit + (kind == CauldronKind.JELLY ? "_jelly" : "_jello");
        if (kind == CauldronKind.JELLY && id.equals("minecraft:slime_ball")) {
            return transform(block, hand, fruit + "_cauldron", CauldronKind.JELLO, level, true);
        }
        String container = kind == CauldronKind.JELLY ? "minecraft:glass_bottle" : "minecraft:bowl";
        if (!id.equals(container)) {
            return Result.miss(hand);
        }
        ItemStack result = customStack("yuanguo:" + stem);
        if (result == null) {
            return Result.miss(hand);
        }
        if (level <= 1) {
            block.setType(Material.CAULDRON, false);
        } else if (!placeState(block, fruit + "_cauldron", kind, level - 1)) {
            return Result.miss(hand);
        }
        play(block, Sound.ITEM_BOTTLE_FILL);
        return new Result(true, shrink(hand), List.of(result));
    }

    /**
     * What a full vanilla water cauldron accepts: a lemon slice makes lemonade, a jam turns the
     * water into the fruit's cauldron at level 4, and a fruit does the same at the level that one
     * fruit is worth.
     *
     * <p>The mod only has the lemon slice and jam recipes here, so the fruit recipe is this port's
     * addition: it lets a player start a fruit cauldron without bottling jam first.</p>
     */
    private static Result vanillaWaterCauldron(Block block, ItemStack hand) {
        if (!(block.getBlockData() instanceof Levelled levelled)
                || levelled.getLevel() != levelled.getMaximumLevel()) {
            return Result.miss(hand);
        }
        String id = itemId(hand);
        if (id == null) {
            return Result.miss(hand);
        }
        if (id.equals("yuanguo:lemon_slice")) {
            return transform(block, hand, "lemonade_cauldron", CauldronKind.LEMONADE, 0, false);
        }
        if (id.startsWith("yuanguo:") && id.endsWith("_jelly")) {
            String fruit = id.substring("yuanguo:".length(), id.length() - "_jelly".length());
            if (CauldronFruits.isFruit(fruit)) {
                // A full water cauldron already holds three doses of water; the jam is the third
                // bottle of the recipe, so it starts at the level three jams are worth.
                return transform(block, hand, fruit + "_cauldron", CauldronKind.FRUIT, 4, false);
            }
        }
        String fruit = CauldronFruits.fruitForItem(id);
        if (fruit != null) {
            // Water plus one fruit starts the cauldron at exactly what that fruit is worth, which
            // is the same 4 / jellyCost step the mod uses when a fruit is added to a cauldron.
            // Like the jam recipe above this needs no heat source.
            return transform(block, hand, fruit + "_cauldron", CauldronKind.FRUIT,
                    CauldronFruits.advance(CauldronFruits.indexOf(fruit)), false);
        }
        return Result.miss(hand);
    }

    // ------------------------------------------------------------------ helpers

    private static Result transform(Block block, ItemStack hand, String target, CauldronKind kind,
                                    int level, boolean heat) {
        if (heat && !hasHeat(block)) {
            return Result.miss(hand);
        }
        if (!placeState(block, target, kind, level)) {
            return Result.miss(hand);
        }
        play(block, Sound.ENTITY_GENERIC_SPLASH);
        return new Result(true, shrink(hand), List.of());
    }

    private static boolean placeState(Block block, String target, CauldronKind kind, int level) {
        ImmutableBlockState state = stateOf("yuanguo:" + target, kind, level);
        return state != null && CraftEngineBlocks.place(block.getLocation(), state, true);
    }

    /**
     * The default state of a custom block, with its {@code kind} and {@code level} properties set
     * when it has them.
     */
    @Nullable
    private static ImmutableBlockState stateOf(String id, CauldronKind kind, int level) {
        var definition = CraftEngineBlocks.byId(Key.of(id));
        if (definition == null) {
            return null;
        }
        ImmutableBlockState state = definition.defaultState();
        if (kind != CauldronKind.LEMONADE && definition.hasProperty("kind")) {
            state = state.with((StringProperty) definition.getProperty("kind"), kind.propertyValue());
        }
        if (level > 0 && definition.hasProperty("level")) {
            state = state.with((IntegerProperty) definition.getProperty("level"), level);
        }
        return state;
    }

    static int level(ImmutableBlockState state, CauldronBlockBehavior behavior) {
        IntegerProperty property = behavior.levelProperty();
        if (property == null) {
            return 12;
        }
        Integer value = state.get(property);
        return value == null ? 1 : value;
    }

    /**
     * The mod only advances a cauldron that sits on a heat source.
     *
     * <p>PapersDelight owns the authoritative list, so its gate is used whenever it is available.
     * If that plugin did not register a resolver (or the call fails), the mod's own definition is
     * reproduced here: the vanilla heat blocks plus the {@code farmersdelight:heat_sources} block
     * tag that Farmer's Delight adds to its stove and other custom blocks.</p>
     */
    private static boolean hasHeat(Block block) {
        Block below = block.getRelative(0, -1, 0);
        try {
            if (!HeatSourceGate.isUnavailable()) {
                return HeatSourceGate.isActiveHeatSource(below);
            }
        } catch (Throwable ignored) {
            // Fall through to the built-in list.
        }
        return isVanillaHeatSource(below) || isTaggedHeatSource(below);
    }

    private static boolean isVanillaHeatSource(Block block) {
        Material type = block.getType();
        if (type == Material.MAGMA_BLOCK || type == Material.LAVA || type == Material.FIRE
                || type == Material.SOUL_FIRE || type == Material.LAVA_CAULDRON) {
            return true;
        }
        if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
            return block.getBlockData() instanceof Lightable lightable && lightable.isLit();
        }
        return false;
    }

    private static boolean isTaggedHeatSource(Block block) {
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            return state != null && !state.isEmpty() && state.customBlockState() != null
                    && state.customBlockState().hasTag(Key.of("farmersdelight", "heat_sources"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void play(Block block, Sound sound) {
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, 1.0F, 1.0F);
    }

    private static ItemStack shrink(ItemStack stack) {
        ItemStack out = stack.clone();
        int amount = stack.getAmount() - 1;
        if (amount <= 0) {
            return new ItemStack(Material.AIR);
        }
        out.setAmount(amount);
        return out;
    }

    /**
     * The id of an item: its CraftEngine id when it is a custom item, otherwise its vanilla key.
     *
     * <p>Several cauldron recipes use vanilla items (sugar, slime balls, bottles, bowls and the
     * fruits that have no custom item), so this must not stop at the CraftEngine lookup.</p>
     */
    @Nullable
    static String itemId(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        try {
            Key key = net.momirealms.craftengine.bukkit.api.CraftEngineItems.getCustomItemId(stack);
            if (key != null) {
                return key.toString();
            }
        } catch (Throwable ignored) {
            // Fall through to the vanilla key; a missing CraftEngine item is not a recipe miss.
        }
        return stack.getType().getKey().toString();
    }

    /** Builds a custom item stack by id, or {@code null} when the id is unknown. */
    @Nullable
    static ItemStack customStack(String id) {
        try {
            var definition = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(id);
            return definition == null ? null : definition.buildBukkitItem();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void setHand(Player player, ItemStack stack) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        player.getInventory().setItemInMainHand(stack);
    }

    static void give(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        var leftover = player.getInventory().addItem(stack);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }


}
