package dev.yuan.yuanguo.behavior;

import dev.yuan.yuanguo.YuanGuoDelight;
import dev.yuan.yuanguo.behavior.util.BehaviorConfig;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.PrioritizedFallOnHandler;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.proxy.minecraft.world.damagesource.DamageSourcesProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.bukkit.entity.LivingEntity;

/**
 * The mod's jello block, which is a vanilla slime block in everything but the look.
 *
 * <p>The original {@code JelloBlock} extends {@code SlimeBlock}, marks itself as a slime block and
 * overrides {@code stepOn} to do nothing, so standing on it neither bounces the player nor slows
 * them down. CraftEngine's built in {@code bouncing_block} behaviour is close, but it syncs the
 * bounce one tick late (the client keeps the landing position for a tick before the velocity packet
 * arrives) and its {@code bounce_restitution} setting does nothing below 26.2. This behaviour
 * mirrors the vanilla method instead: the vertical velocity is inverted and marked dirty in the
 * same tick, with no {@code stepOn} at all.</p>
 */
public final class JelloBlockBehavior extends BukkitBlockBehavior implements PrioritizedFallOnHandler {

    public static final Key KEY = Key.of("yuanguo", "jello_bounce");
    public static final BlockBehaviorFactory<JelloBlockBehavior> FACTORY = JelloBlockBehavior::new;

    /** Vanilla uses 1.0 for living entities and 0.8 for everything else. */
    private static final double NON_LIVING_SCALE = 0.8D;

    private final double bounceHeight;
    private final double fallDamageMultiplier;

    private JelloBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
        this.bounceHeight = Math.max(0.0D, BehaviorConfig.doubleValue(section, 1.0D, "bounce_height"));
        this.fallDamageMultiplier = Math.max(0.0D,
                BehaviorConfig.doubleValue(section, 0.0D, "fall_damage_multiplier"));
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    /**
     * Slime blocks do not hurt: the vanilla method calls {@code causeFallDamage(distance, 0)}. The
     * multiplier stays configurable, but the default of zero means no damage at all.
     */
    @Override
    public void fallOn(Object thisBlock, Object[] args) {
        if (this.fallDamageMultiplier <= 0.0D) {
            return;
        }
        Object entity = args[3];
        Number fallDistance = (Number) args[4];
        Object damageSource = DamageSourcesProxy.INSTANCE.fall(EntityProxy.INSTANCE.damageSources(entity));
        if (VersionHelper.isOrAbove1_21_5) {
            EntityProxy.INSTANCE.causeFallDamage(
                    entity, fallDistance.doubleValue() * this.fallDamageMultiplier, 1.0F, damageSource);
        } else {
            EntityProxy.INSTANCE.causeFallDamage(
                    entity, fallDistance.floatValue() * (float) this.fallDamageMultiplier, 1.0F, damageSource);
        }
    }

    /**
     * The bounce itself. The vanilla slime block only reacts while falling, flips the vertical
     * velocity and leaves the horizontal one untouched.
     *
     * <p>Marking the entity dirty in this very call is what makes the bounce start on the landing
     * tick: a custom block has no client-side physics, so without {@code hurtMarked} the client keeps
     * the stopped position until the next velocity update.</p>
     */
    @Override
    public void updateEntityMovementAfterFallOn(Object thisBlock, Object[] args) {
        if (!bounceEnabled()) {
            return;
        }
        Object entity = args[1];
        // Sneaking suppresses the bounce, exactly like the vanilla isSuppressingBounce check.
        if (EntityProxy.INSTANCE.getSharedFlag(entity, 1)) {
            return;
        }
        Object delta = EntityProxy.INSTANCE.getDeltaMovement(entity);
        double y = Vec3Proxy.INSTANCE.getY(delta);
        if (y >= 0.0D) {
            return;
        }
        double scale = EntityProxy.INSTANCE.getBukkitEntity(entity) instanceof LivingEntity
                ? 1.0D : NON_LIVING_SCALE;
        EntityProxy.INSTANCE.setDeltaMovement(
                entity,
                Vec3Proxy.INSTANCE.getX(delta),
                -y * this.bounceHeight * scale,
                Vec3Proxy.INSTANCE.getZ(delta));
        EntityProxy.INSTANCE.setHurtMarked(entity, true);
    }

    /**
     * The bounce can be switched off in config.yml ({@code pack.jello-bounce}) because the same
     * block can feel different on different server cores or game versions. The value is read on
     * every landing, so {@code /yuanguo reload} applies it without restarting the server.
     */
    private static boolean bounceEnabled() {
        YuanGuoDelight plugin = YuanGuoDelight.instance();
        return plugin != null && plugin.settings().jelloBounce();
    }
}
