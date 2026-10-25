package dev.yuan.yuanguo.effect;

import dev.yuan.yuanguo.YuanGuoDelight;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies and drives the status effects the mod adds.
 *
 * <p>Eating is handled from {@link PlayerItemConsumeEvent}: the item is matched to its
 * {@code yuanguo:} id through CraftEngine, the effect table is looked up, and each entry is either
 * handed to Bukkit or recorded as one of the plugin's own effects. Everything the mod did from a
 * mixin is driven from the matching Bukkit event or from the ten tick loop.</p>
 */
public final class EffectService implements Listener {

    private static final int SUSPICIOUS_RADIUS = 8;
    private static final int SUSPICIOUS_VERTICAL_RADIUS = 6;
    private static final UUID SHRINKING_SCALE_ID =
            UUID.fromString("8a3b4f70-5e22-4bc6-a1d9-4d7d8db7a1c2");

    private final YuanGuoDelight plugin;
    private final EffectRegistry registry;
    private final EffectState state;
    private final EffectTimerBar bars;
    private final Set<UUID> cycledThisSecond = ConcurrentHashMap.newKeySet();
    /** Players whose current eat/drink is already being shortened, so one use is only cut once. */
    private final Map<UUID, Integer> shortening = new ConcurrentHashMap<>();
    /** The players the tick loop put at a fake hunger level, so it can be undone. */
    private final Set<UUID> fakedHunger = ConcurrentHashMap.newKeySet();
    /** Players who drank milk while wearing the durian helmet, so its aura stays cleared. */
    private final Set<UUID> milkSuppressedDurian = ConcurrentHashMap.newKeySet();
    /** Disgusted entities and the real-time expiry of their breeding lock. */
    private final Map<UUID, Long> disgustedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> auraPulse = new ConcurrentHashMap<>();
    private BukkitTask ticker;
    /** The Folia equivalent of {@link #ticker}; only one of the two is ever set. */
    private ScheduledTask regionTicker;
    private boolean running;

    public EffectService(YuanGuoDelight plugin, EffectRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.state = new EffectState(plugin);
        this.bars = new EffectTimerBar(registry, plugin.settings().effectBossBar());
    }

    public EffectRegistry registry() {
        return this.registry;
    }

    public EffectState state() {
        return this.state;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        if (!this.plugin.settings().effectsEnabled()) {
            this.plugin.getLogger().info("The food effects are disabled in config.yml; no effect will be granted.");
            return;
        }
        this.running = true;
        // The effects tick every ten ticks, which matches the mod's aura period and keeps the cost
        // of the per-player scan low.
        if (isFolia()) {
            this.regionTicker = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(this.plugin, task -> tickAll(), 10L, 10L);
        } else {
            this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickAll, 10L, 10L);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.state.restore(player);
        }
    }

    public void stop() {
        this.running = false;
        if (this.ticker != null) {
            this.ticker.cancel();
            this.ticker = null;
        }
        if (this.regionTicker != null) {
            this.regionTicker.cancel();
            this.regionTicker = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.state.persist(player);
            this.bars.removeAll(player);
            restoreHunger(player);
            removeShrinkingScale(player);
            restorePose(player);
        }
        this.fakedHunger.clear();
        this.cycledThisSecond.clear();
        this.milkSuppressedDurian.clear();
        this.disgustedUntil.clear();
        this.auraPulse.clear();
    }

    // ------------------------------------------------------------------ eating

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!this.running) {
            return;
        }
        Player player = event.getPlayer();
        // Creative is allowed on purpose: the pack is tested there, and vanilla creative players
        // still receive potion effects from food.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            clearCustomEffects(player);
            return;
        }
        List<FoodEffect> effects = effectsOf(event.getItem());
        if (effects.isEmpty()) {
            return;
        }
        apply(player, effects);
    }

    private void clearCustomEffects(Player player) {
        this.state.clear(player);
        this.bars.removeAll(player);
        this.auraPulse.remove(player.getUniqueId());
        this.cycledThisSecond.remove(player.getUniqueId());
        if (hasDurianHelmet(player)) {
            this.milkSuppressedDurian.add(player.getUniqueId());
        }
        restoreHunger(player);
        removeShrinkingScale(player);
        restorePose(player);
    }

    /**
     * Grants the effects of a {@code yuanguo:} item id straight away.
     *
     * <p>A meal block is eaten in place, so there is no item to consume and no
     * {@code PlayerItemConsumeEvent} to hook; the serving-block behavior calls this instead.</p>
     */
    public void grant(org.bukkit.entity.Player player, String itemId) {
        if (!this.running || player == null) {
            return;
        }
        int colon = itemId.indexOf(':');
        String value = colon < 0 ? itemId : itemId.substring(colon + 1);
        List<FoodEffect> effects = this.registry.effectsOf(value);
        if (!effects.isEmpty()) {
            apply(player, effects);
        }
    }

    private void apply(Player player, List<FoodEffect> effects) {
        for (FoodEffect effect : effects) {
            if (effect.chance() < 1.0D && plugin.random().nextDouble() >= effect.chance()) {
                continue;
            }
            if (effect.isVanilla()) {
                player.addPotionEffect(new PotionEffect(effect.type(), effect.duration(),
                        effect.amplifier(), false, true, true));
                continue;
            }
            CustomEffect custom = effect.custom();
            if (!this.registry.enabled(custom)) {
                continue;
            }
            if (custom == CustomEffect.CHORUS) {
                teleport(player);
            }
            this.state.apply(player, custom, effect.duration(), effect.amplifier());
        }
        clearBlockedEffects(player);
        this.bars.update(player, this.state.of(player));
    }

    private List<FoodEffect> effectsOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return List.of();
        }
        Key id = CraftEngineItems.getCustomItemId(item);
        if (id == null || !"yuanguo".equals(id.namespace())) {
            return List.of();
        }
        return this.registry.effectsOf(id.value());
    }

    /** The effects an item would grant, for the {@code /yuanguo effects} command. */
    public List<FoodEffect> effectsOfItem(ItemStack item) {
        return effectsOf(item);
    }

    // ------------------------------------------------------------------ tick loop

    private void tickAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Map<CustomEffect, EffectState.Active> expired = this.state.tick(player, 10);
            applyDurianHelmet(player);
            clearBlockedEffects(player);
            applyAuras(player);
            applyCycling(player);
            applyHungerTricks(player);
            applySuspiciousSmell(player);
            applyShrinkingScale(player);
            if (expired.containsKey(CustomEffect.SHRINKING)) {
                removeShrinkingScale(player);
                restorePose(player);
            }
            this.bars.update(player, this.state.of(player));
        }
    }

    /** The durian helmet refreshes alienating while it is equipped, like the mod's armor tick. */
    private void applyDurianHelmet(Player player) {
        if (!hasDurianHelmet(player)) {
            this.milkSuppressedDurian.remove(player.getUniqueId());
            return;
        }
        if (!this.registry.enabled(CustomEffect.ALIENATING)
                || this.milkSuppressedDurian.contains(player.getUniqueId())) {
            return;
        }
        this.state.apply(player, CustomEffect.ALIENATING, 40, 0);
    }

    private static boolean hasDurianHelmet(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        Key id = helmet == null ? null : CraftEngineItems.getCustomItemId(helmet);
        return id != null && id.equals(Key.of("yuanguo:durian_helmet"));
    }

    /** Rage, heal and alienating pulse on everything within range. */
    private void applyAuras(Player player) {
        aura(player, CustomEffect.HEAL_AURA, 6, 60, (source, target) -> {
            EffectState.Active active = this.state.get(source, CustomEffect.HEAL_AURA);
            double amount = active == null ? 0.5D : 0.5D * (active.amplifier() + 1);
            if (target.getHealth() < maxHealth(target)) {
                heal(target, amount);
                target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0.0D, 1.2D, 0.0D),
                        1, 0.4D, 0.4D, 0.4D, 0.0D);
            }
        });
        aura(player, CustomEffect.RAGE_AURA, 24, 10, (source, target) -> {
            if (target instanceof Mob mob && mob.getTarget() == null) {
                mob.setTarget(source);
            }
        });
        aura(player, CustomEffect.ALIENATING, 12, 10, (source, target) -> {
            if (hasAlienating(target)) {
                return;
            }
            applyDisgusted(target);
            if (!target.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                addEffect(target, PotionEffectType.WEAKNESS, 40, 0);
            }
            if (target instanceof Animals animal && animal.getLoveModeTicks() > 0) {
                animal.setLoveModeTicks(0);
            }
        });
    }

    private boolean hasAlienating(LivingEntity entity) {
        return entity instanceof Player player && this.state.has(player, CustomEffect.ALIENATING);
    }

    private boolean isDisgusted(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        Long until = this.disgustedUntil.get(uuid);
        if (until == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (until > now) {
            return true;
        }
        this.disgustedUntil.remove(uuid, until);
        return false;
    }

    private void applyDisgusted(LivingEntity entity) {
        this.disgustedUntil.merge(entity.getUniqueId(),
                System.currentTimeMillis() + 10_000L,
                Math::max);
    }

    private void aura(Player player, CustomEffect effect, int fallbackRange, int period, AuraAction action) {
        if (this.state.get(player, effect) == null) {
            if (period > 10) {
                this.auraPulse.remove(player.getUniqueId());
            }
            return;
        }
        if (!shouldPulse(player, effect, period)) {
            return;
        }
        int range = this.registry.range(effect, fallbackRange);
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                action.apply(player, living);
            }
        }
        if (effect == CustomEffect.HEAL_AURA) {
            action.apply(player, player);
        }
        // The mod renders a particle for every entity in range, which is also the cheapest way to
        // show that the aura is running.
        Particle particle = switch (effect) {
            case HEAL_AURA -> Particle.HEART;
            case ALIENATING -> Particle.CLOUD;
            default -> Particle.ANGRY_VILLAGER;
        };
        player.getWorld().spawnParticle(particle, player.getLocation().add(0.0D, 1.0D, 0.0D),
                1, 0.6D, 0.6D, 0.6D, 0.0D);
    }

    private boolean shouldPulse(Player player, CustomEffect effect, int period) {
        if (period <= 10) {
            return true;
        }
        int remaining = this.auraPulse.getOrDefault(player.getUniqueId(), 0) - 10;
        if (remaining > 0) {
            this.auraPulse.put(player.getUniqueId(), remaining);
            return false;
        }
        this.auraPulse.put(player.getUniqueId(), period);
        return true;
    }

    @FunctionalInterface
    private interface AuraAction {
        void apply(Player source, LivingEntity target);
    }

    /** Cycling drops one experience level as orbs every second. */
    private void applyCycling(Player player) {
        EffectState.Active active = this.state.get(player, CustomEffect.CYCLING);
        if (active == null) {
            this.cycledThisSecond.remove(player.getUniqueId());
            return;
        }
        int period = 20 << Math.min(3, active.amplifier());
        if (player.getTicksLived() % period >= 10) {
            return;
        }
        if (!this.cycledThisSecond.add(player.getUniqueId())) {
            return;
        }
        // The flag is cleared again after one period, so the orb is only spawned once per period.
        runLater(player, () -> this.cycledThisSecond.remove(player.getUniqueId()), period);
        if (player.getLevel() <= 1) {
            return;
        }
        int xp = experienceForLevel(player);
        if (xp <= 0) {
            return;
        }
        player.giveExp(-xp);
        ExperienceOrb orb = player.getWorld().spawn(player.getLocation(), ExperienceOrb.class);
        orb.setExperience(xp);
    }

    private static int experienceForLevel(Player player) {
        int level = player.getLevel();
        if (level <= 16) {
            return 2 * level + 7;
        }
        if (level <= 31) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /** Suspicious smell highlights nearby brushable blocks, matching the mod's client particles. */
    private void applySuspiciousSmell(Player player) {
        if (!this.state.has(player, CustomEffect.SUSPICIOUS_SMELL)) {
            return;
        }
        int baseX = player.getLocation().getBlockX();
        int baseY = player.getLocation().getBlockY();
        int baseZ = player.getLocation().getBlockZ();
        for (int x = -SUSPICIOUS_RADIUS; x <= SUSPICIOUS_RADIUS; x++) {
            for (int z = -SUSPICIOUS_RADIUS; z <= SUSPICIOUS_RADIUS; z++) {
                int blockX = baseX + x;
                int blockZ = baseZ + z;
                if (!player.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                    continue;
                }
                for (int y = -SUSPICIOUS_VERTICAL_RADIUS; y <= SUSPICIOUS_VERTICAL_RADIUS; y++) {
                    Block block = player.getWorld().getBlockAt(blockX, baseY + y, blockZ);
                    if (block.getType() != Material.SUSPICIOUS_SAND
                            && block.getType() != Material.SUSPICIOUS_GRAVEL) {
                        continue;
                    }
                    player.spawnParticle(Particle.HAPPY_VILLAGER,
                            block.getX() + 0.5D, block.getY() + 1.0D, block.getZ() + 0.5D,
                            2, 0.2D, 0.0D, 0.2D, 0.0D);
                }
            }
        }
    }

    // ------------------------------------------------------------------ hunger effects

    /**
     * Appetizing lets the player eat with a full hunger bar. Bukkit only exposes the food level, so
     * the bar is lowered by one point while a food is held; the client then lets the player start
     * eating, and the level is put back afterwards. This is the same trick PapersDelight uses for
     * its Nourishment effect.
     */
    private void applyHungerTricks(Player player) {
        if (!this.state.has(player, CustomEffect.APPETIZING)) {
            restoreHunger(player);
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean holding = isFood(player.getInventory().getItemInMainHand())
                || isFood(player.getInventory().getItemInOffHand());
        if (holding && player.getFoodLevel() >= 20) {
            player.setFoodLevel(19);
            this.fakedHunger.add(player.getUniqueId());
        } else if (!holding) {
            restoreHunger(player);
        }
    }

    /** Immunity effects also remove effects that were already active when they were granted. */
    private void clearBlockedEffects(Player player) {
        if (this.state.has(player, CustomEffect.REFRESHING)) {
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        }
        if (this.state.has(player, CustomEffect.SWEETENING)) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        if (this.state.has(player, CustomEffect.BRIGHTENING)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
        if (this.state.has(player, CustomEffect.RECOVERING)) {
            player.removePotionEffect(PotionEffectType.POISON);
            player.removePotionEffect(PotionEffectType.WITHER);
        }
    }

    private void restoreHunger(Player player) {
        if (!this.fakedHunger.remove(player.getUniqueId())) {
            return;
        }
        if (player.isOnline() && player.getFoodLevel() < 20) {
            player.setFoodLevel(20);
        }
    }

    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (stack.getType().isEdible()) {
            return true;
        }
        var meta = stack.getItemMeta();
        return meta != null && meta.hasFood();
    }

    /**
     * Digesting converts the nutrition a full hunger bar would drop into saturation.
     *
     * <p>The event fires before the food is applied, so its level is what the bar would reach; the
     * nutrition that does not fit becomes saturation, at half rate, which is what the mod's
     * {@code FoodData} mixin does.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!this.state.has(player, CustomEffect.DIGESTING)) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (eaten == null || eaten.getType().isAir()) {
            return;
        }
        int nutrition = nutritionOf(eaten);
        if (nutrition <= 0) {
            return;
        }
        // The bar can never go past twenty, so anything above that is the overflow.
        int overflow = Math.min(event.getFoodLevel(), 20 + nutrition) - 20;
        if (overflow <= 0) {
            return;
        }
        player.setSaturation(player.getSaturation() + overflow / 2.0F);
    }

    private static int nutritionOf(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta != null && meta.hasFood()) {
            return meta.getFood().getNutrition();
        }
        return stack.getType().isEdible() ? 1 : 0;
    }

    // ------------------------------------------------------------------ use speed

    /** Lozenge speeds up eating and drinking, exactly like the mod halves the use duration. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.state.has(player, CustomEffect.LOZENGE)) {
            return;
        }
        // Any item may start an eat or drink, so nothing is filtered here. The active item is
        // inspected a tick later, once the server has actually begun using it.
        if (this.shortening.putIfAbsent(player.getUniqueId(), 0) != null) {
            return;
        }
        runLater(player, () -> shortenUse(player, 0), 1L);
    }

    /**
     * The server only starts using the item after the interact event, and CraftEngine can refresh
     * the held stack between ticks, which clears the active item for a tick. So the remaining time
     * is read a tick later, retrying a few ticks when the use has not started yet.
     */
    private void shortenUse(Player player, int attempt) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            this.shortening.remove(id);
            return;
        }
        if (!player.hasActiveItem()) {
            if (attempt < 3) {
                this.shortening.put(id, attempt + 1);
                runLater(player, () -> shortenUse(player, attempt + 1), 1L);
            } else {
                this.shortening.remove(id);
            }
            return;
        }
        // The active item is what matters, not whatever the click event happened to carry.
        if (!isEatOrDrink(player.getActiveItem())) {
            this.shortening.remove(id);
            return;
        }
        this.shortening.remove(id);
        // Only a fresh use is shortened; clicking again mid-eat must not halve it a second time.
        if (player.getActiveItemUsedTime() > 2) {
            return;
        }
        EffectState.Active active = this.state.get(player, CustomEffect.LOZENGE);
        if (active == null) {
            return;
        }
        int remaining = player.getActiveItemRemainingTime();
        if (remaining <= 1) {
            return;
        }
        // The mod does useDuration >> (1 + amplifier): half at level I, a quarter at level II.
        int shifted = (remaining + 1) >> (1 + active.amplifier());
        player.setActiveItemRemainingTime(Math.max(1, shifted));
    }

    private static boolean isEatOrDrink(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (isFood(stack)) {
            return true;
        }
        // The mod also speeds up potions and milk, which are drinks rather than food.
        return switch (stack.getType()) {
            case POTION, SPLASH_POTION, LINGERING_POTION, MILK_BUCKET, HONEY_BOTTLE -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ vanilla immunity

    /** Immune effects cancel the listed vanilla effects before they land. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PotionEffectType type = event.getModifiedType();
        if (type == null) {
            return;
        }
        if (blocked(player, type)) {
            event.setCancelled(true);
        }
    }

    private boolean blocked(Player player, PotionEffectType type) {
        if (type.equals(PotionEffectType.MINING_FATIGUE)
                && this.state.has(player, CustomEffect.REFRESHING)) {
            return true;
        }
        if (type.equals(PotionEffectType.SLOWNESS)
                && this.state.has(player, CustomEffect.SWEETENING)) {
            return true;
        }
        if (this.state.has(player, CustomEffect.BRIGHTENING)
                && (type.equals(PotionEffectType.BLINDNESS) || type.equals(PotionEffectType.DARKNESS))) {
            return true;
        }
        return this.state.has(player, CustomEffect.RECOVERING)
                && (type.equals(PotionEffectType.POISON) || type.equals(PotionEffectType.WITHER));
    }

    /** Disgusted animals cannot enter love mode or breed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (!(event.getEntity() instanceof Animals animal) || !isDisgusted(animal)) {
            return;
        }
        event.setCancelled(true);
        if (animal.getLoveModeTicks() > 0) {
            animal.setLoveModeTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (isDisgusted(event.getMother()) || isDisgusted(event.getFather())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ shaping

    /** Scales a sneaking player down while shrinking is active, without fighting client poses. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        applyShrinkingScale(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        applyShrinkingScale(player);
    }

    private void applyShrinkingScale(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }
        if (this.state.has(player, CustomEffect.SHRINKING) && player.isSneaking()) {
            if (scale.getModifier(SHRINKING_SCALE_ID) == null) {
                scale.addTransientModifier(new AttributeModifier(
                        SHRINKING_SCALE_ID,
                        "YuanGuoDelight shrinking",
                        -0.5D,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
            return;
        }
        removeShrinkingScale(player);
        restorePose(player);
    }

    private void removeShrinkingScale(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.removeModifier(SHRINKING_SCALE_ID);
        }
    }

    private void restorePose(Player player) {
        if (player.getPose() == Pose.SWIMMING && !player.isSwimming() && !player.isSneaking()) {
            player.setPose(Pose.STANDING);
        }
    }

    // ------------------------------------------------------------------ chorus

    /** Teleports the player the way a chorus fruit does. */
    private void teleport(Player player) {
        World world = player.getWorld();
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Location origin = player.getLocation();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = origin.getX() + (plugin.random().nextDouble() - 0.5D) * 16.0D;
            double z = origin.getZ() + (plugin.random().nextDouble() - 0.5D) * 16.0D;
            int y = origin.getBlockY() + plugin.random().nextInt(16) - 8;
            y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, y));
            Location target = findSafeSpot(world, x, y, z);
            if (target == null) {
                continue;
            }
            world.playSound(origin, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
            if (player.teleport(target)) {
                player.setFallDistance(0.0F);
                world.playSound(target, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                return;
            }
        }
    }

    private static Location findSafeSpot(World world, double x, int y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int startY = Math.min(y, world.getMaxHeight() - 2);
        int limit = Math.max(world.getMinHeight(), startY - 16);
        for (int blockY = startY; blockY > limit; blockY--) {
            Block floor = world.getBlockAt(blockX, blockY, blockZ);
            Block feet = world.getBlockAt(blockX, blockY + 1, blockZ);
            Block head = world.getBlockAt(blockX, blockY + 2, blockZ);
            if (!floor.isPassable()
                    && feet.isPassable()
                    && head.isPassable()
                    && !floor.isLiquid()
                    && !feet.isLiquid()
                    && !head.isLiquid()) {
                return new Location(world, x, blockY + 1.2D, z);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ lifecycle events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.state.restore(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.state.persist(player);
        this.state.forget(player.getUniqueId());
        this.bars.removeAll(player);
        this.auraPulse.remove(player.getUniqueId());
        restoreHunger(player);
        removeShrinkingScale(player);
        restorePose(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        this.state.clear(player);
        this.bars.removeAll(player);
        restoreHunger(player);
        removeShrinkingScale(player);
        restorePose(player);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Folia has no global Bukkit scheduler: the server refuses {@code Bukkit.getScheduler()} with
     * an {@link UnsupportedOperationException}. The class is only referenced here, so Paper builds
     * keep working.
     */
    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false,
                    EffectService.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Runs {@code action} on the entity's own region thread, now or after {@code delayTicks}. */
    private void runLater(LivingEntity entity, Runnable action, long delayTicks) {
        if (isFolia()) {
            entity.getScheduler().runDelayed(this.plugin, task -> action.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(this.plugin, action, delayTicks);
        }
    }

    /** Runs {@code action} on the player's region thread as soon as possible. */
    private void runNow(Player player, Runnable action) {
        if (isFolia()) {
            player.getScheduler().run(this.plugin, task -> action.run(), null);
        } else {
            Bukkit.getScheduler().runTask(this.plugin, action);
        }
    }

    private void addEffect(LivingEntity entity, PotionEffectType type, int duration, int amplifier) {
        entity.addPotionEffect(new PotionEffect(type, duration, amplifier, false, true, true));
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    private void heal(LivingEntity entity, double amount) {
        entity.setHealth(Math.min(maxHealth(entity), entity.getHealth() + amount));
    }

    /** The custom effects a player currently carries, for the {@code /yuanguo} command. */
    public List<String> describe(Player player) {
        List<String> out = new ArrayList<>();
        this.state.of(player).forEach((effect, active) ->
                out.add(effect.id() + " x" + (active.amplifier() + 1)
                        + " (" + active.remainingTicks() + "t)"));
        return out;
    }

    /** The set of effects the plugin knows, for diagnostics. */
    public static Set<CustomEffect> all() {
        return EnumSet.allOf(CustomEffect.class);
    }

    /** Reports the effects a food grants, for the {@code /yuanguo} command. */
    public List<String> describeItem(ItemStack item) {
        List<String> out = new ArrayList<>();
        for (FoodEffect effect : effectsOf(item)) {
            out.add(String.format(Locale.ROOT, "%s %dt x%d %d%%", effect.id(), effect.duration(),
                    effect.amplifier() + 1, Math.round(effect.chance() * 100.0D)));
        }
        return out;
    }
}
