package dev.yuan.yuanguo.behavior;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.PrioritizedFallOnHandler;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.proxy.minecraft.world.entity.EntityProxy;
import net.momirealms.craftengine.proxy.minecraft.world.phys.Vec3Proxy;
import org.bukkit.entity.LivingEntity;

public final class JelloBlockBehavior extends BukkitBlockBehavior implements PrioritizedFallOnHandler {

    public static final Key KEY = Key.of("yuanguo", "jello_bounce");
    public static final BlockBehaviorFactory<JelloBlockBehavior> FACTORY = JelloBlockBehavior::new;

    private JelloBlockBehavior(BlockDefinition block, ConfigSection section) {
        super(block);
    }

    public static void register() {
        BlockBehaviors.register(KEY, FACTORY);
    }

    @Override
    public void fallOn(Object thisBlock, Object[] args) {
    }

    @Override
    public void updateEntityMovementAfterFallOn(Object thisBlock, Object[] args) {
        Object entity = args[1];
        if (EntityProxy.INSTANCE.getSharedFlag(entity, 1)) {
            super.updateEntityMovementAfterFallOn(thisBlock, args);
            return;
        }

        Object delta = EntityProxy.INSTANCE.getDeltaMovement(entity);
        double y = Vec3Proxy.INSTANCE.getY(delta);
        if (y >= 0.0D) {
            return;
        }

        double scale = EntityProxy.INSTANCE.getBukkitEntity(entity) instanceof LivingEntity ? 1.0D : 0.8D;
        EntityProxy.INSTANCE.setDeltaMovement(
                entity,
                Vec3Proxy.INSTANCE.getX(delta),
                -y * scale,
                Vec3Proxy.INSTANCE.getZ(delta));
    }

    @Override
    public void stepOn(Object thisBlock, Object[] args) {
        Object entity = args[3];
        Object delta = EntityProxy.INSTANCE.getDeltaMovement(entity);
        double absoluteY = Math.abs(Vec3Proxy.INSTANCE.getY(delta));
        if (absoluteY < 0.1D && !EntityProxy.INSTANCE.isShiftKeyDown(entity)) {
            double scale = 0.4D + absoluteY * 0.2D;
            EntityProxy.INSTANCE.setDeltaMovement(
                    entity,
                    Vec3Proxy.INSTANCE.getX(delta) * scale,
                    Vec3Proxy.INSTANCE.getY(delta),
                    Vec3Proxy.INSTANCE.getZ(delta) * scale);
        }
        super.stepOn(thisBlock, args);
    }
}
