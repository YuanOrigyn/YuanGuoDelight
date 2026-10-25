package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.PrioritizedFallOnHandler;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.proxy.minecraft.world.damagesource.DamageSourcesProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Honey-like physics for the mod's jelly block. */
public final class JellyBlockBehavior extends BukkitBlockBehavior implements PrioritizedFallOnHandler {

    public static final Key KEY = Key.of("yuanguo", "jelly_surface");
    public static final BlockBehaviorFactory<JellyBlockBehavior> FACTORY = JellyBlockBehavior::new;

    private final double fallDamageMultiplier;
    private final double slideMultiplier;
    private final int effectDuration;

    private JellyBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.fallDamageMultiplier = BehaviorConfig.doubleValue(section, 0.2D, "fall_damage_multiplier");
        this.slideMultiplier = BehaviorConfig.doubleValue(section, 0.4D, "slide_multiplier");
        this.effectDuration = BehaviorConfig.intValue(section, 10, "effect_duration");
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    @Override
    public void stepOn(Object thisBlock, Object[] args) {
        applySlowness(args[3]);
    }

    @Override
    public void entityInside(Object thisBlock, Object[] args) {
        applySlowness(args[3]);
        slideDownSide(args[2], args[3]);
    }

    @Override
    public void fallOn(Object thisBlock, Object[] args) {
        Object entity = args[3];
        Number fallDistance = (Number) args[4];
        Object damageSource = DamageSourcesProxy.INSTANCE.fall(EntityProxy.INSTANCE.damageSources(entity));
        if (net.momirealms.craftengine.core.util.VersionHelper.isOrAbove1_21_5) {
            EntityProxy.INSTANCE.causeFallDamage(
                    entity, fallDistance.doubleValue() * this.fallDamageMultiplier, 1.0F, damageSource);
        } else {
            EntityProxy.INSTANCE.causeFallDamage(
                    entity, fallDistance.floatValue() * (float) this.fallDamageMultiplier, 1.0F, damageSource);
        }
    }

    @Override
    public void updateEntityMovementAfterFallOn(Object thisBlock, Object[] args) {
        Object entity = args[1];
        Object delta = EntityProxy.INSTANCE.getDeltaMovement(entity);
        double y = Vec3Proxy.INSTANCE.getY(delta);
        if (y < 0.0D) {
            EntityProxy.INSTANCE.setDeltaMovement(
                    entity,
                    Vec3Proxy.INSTANCE.getX(delta) * this.slideMultiplier,
                    y,
                    Vec3Proxy.INSTANCE.getZ(delta) * this.slideMultiplier);
        }
    }

    private void applySlowness(Object entity) {
        if (!(EntityProxy.INSTANCE.getBukkitEntity(entity) instanceof LivingEntity livingEntity)) {
            return;
        }
        livingEntity.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, Math.max(1, this.effectDuration), 0, false, false, false));
    }

    private void slideDownSide(Object blockPos, Object entity) {
        org.bukkit.entity.Entity bukkitEntity = EntityProxy.INSTANCE.getBukkitEntity(entity);
        if (bukkitEntity == null || bukkitEntity.isOnGround()) {
            return;
        }
        BlockPos pos = LocationUtils.fromBlockPos(blockPos);
        Vector velocity = bukkitEntity.getVelocity();
        if (bukkitEntity.getLocation().getY() > pos.y() + 0.9375D - 1.0E-7D
                || velocity.getY() >= -0.08D) {
            return;
        }
        double edgeDistance = 0.4375D + bukkitEntity.getWidth() / 2.0D;
        double dx = Math.abs(pos.x() + 0.5D - bukkitEntity.getLocation().getX());
        double dz = Math.abs(pos.z() + 0.5D - bukkitEntity.getLocation().getZ());
        if (dx + 1.0E-7D <= edgeDistance && dz + 1.0E-7D <= edgeDistance) {
            return;
        }
        double scale = velocity.getY() < -0.13D ? -0.05D / velocity.getY() : 1.0D;
        bukkitEntity.setVelocity(new Vector(velocity.getX() * scale, -0.05D, velocity.getZ() * scale));
        bukkitEntity.setFallDistance(0.0F);
    }
}
