package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class CombatTargeting {
    private CombatTargeting() {
    }

    public static List<LivingEntity> collect(
            Minecraft mc,
            double maxRange,
            float maxFov,
            Predicate<LivingEntity> filter,
            Comparator<LivingEntity> sort) {
        if (mc == null || mc.player == null || mc.level == null) {
            return List.of();
        }
        AABB box = mc.player.getBoundingBox().inflate(maxRange + 1.0);
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : mc.level.getEntities(mc.player, box,
                candidate -> candidate instanceof LivingEntity living
                        && living != mc.player
                        && living.isAlive())) {
            LivingEntity living = (LivingEntity) entity;
            if (mc.player.distanceTo(living) > maxRange) {
                continue;
            }
            if (!AcBypassUtil.isInFov(living, maxFov)) {
                continue;
            }
            if (filter != null && !filter.test(living)) {
                continue;
            }
            if (!AcBypassUtil.canReachEntity(living, maxRange)) {
                continue;
            }
            found.add(living);
        }
        if (sort != null) {
            found.sort(sort);
        }
        return found;
    }

    public static float[] rotationsTo(Entity target, Minecraft mc) {
        if (mc == null || mc.player == null || target == null) {
            return new float[]{0.0f, 0.0f};
        }
        double dx = target.getX() - mc.player.getX();
        double dy = (target.getY() + target.getBbHeight() / 2.0)
                - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    public static float rotationDelta(float current, float target) {
        float diff = target - current;
        while (diff > 180.0f) {
            diff -= 360.0f;
        }
        while (diff < -180.0f) {
            diff += 360.0f;
        }
        return Math.abs(diff);
    }
}
