package nv.navineclient.module.render;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class TargetHUD extends Module {
    public static TargetHUD INSTANCE;

    private final BooleanSetting autoPosition = new BooleanSetting("AutoPosition", "Place away from radar", true);
    private final NumberSetting x = new NumberSetting("X", "X position", 10.0, 0.0, 500.0);
    private final NumberSetting y = new NumberSetting("Y", "Y position", 8.0, 0.0, 500.0);
    private final BooleanSetting showHealth = new BooleanSetting("Health", "Show target health", true);
    private final BooleanSetting trackCrosshair = new BooleanSetting("Crosshair", "Track entity under crosshair", true);
    private final NumberSetting range = new NumberSetting("Range", "Fallback search range", 8.0, 3.0, 32.0);
    private final NumberSetting timeout = new NumberSetting("Timeout", "Keep target for seconds", 3.0, 1.0, 10.0);

    private LivingEntity target;
    private long lastSeen;

    public TargetHUD() {
        super("TargetHUD", "Shows info about your target", Category.RENDER);
        INSTANCE = this;
        addSetting(autoPosition);
        addSetting(x);
        addSetting(y);
        addSetting(showHealth);
        addSetting(trackCrosshair);
        addSetting(range);
        addSetting(timeout);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long keepMs = (long) (timeout.getValue() * 1000.0);

        if (target != null && (!target.isAlive() || target.isRemoved())) {
            target = null;
        }

        if (trackCrosshair.getValue()) {
            LivingEntity looked = getCrosshairEntity();
            if (looked != null) {
                target = looked;
                lastSeen = now;
                return;
            }
        }

        if (target != null && now - lastSeen <= keepMs) {
            if (mc.player.distanceTo(target) <= range.getValue() * 2.0) {
                return;
            }
        }

        LivingEntity nearest = findNearestTarget();
        if (nearest != null) {
            target = nearest;
            lastSeen = now;
        } else if (now - lastSeen > keepMs) {
            target = null;
        }
    }

    private LivingEntity getCrosshairEntity() {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            if (living != mc.player && living.isAlive() && mc.player.distanceTo(living) <= range.getValue()) {
                return living;
            }
        }
        return null;
    }

    private LivingEntity findNearestTarget() {
        LivingEntity nearest = null;
        double nearestDist = range.getValue();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(range.getValue()))) {
            if (entity == mc.player || !entity.isAlive()) {
                continue;
            }
            double dist = mc.player.distanceTo(entity);
            if (dist <= nearestDist) {
                nearest = entity;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    public void setTarget(LivingEntity entity) {
        this.target = entity;
        this.lastSeen = System.currentTimeMillis();
    }

    public LivingEntity getTarget() {
        return target;
    }

    public int getX() {
        return x.getValue().intValue();
    }

    public int getY() {
        return y.getValue().intValue();
    }

    public boolean autoPosition() {
        return autoPosition.getValue();
    }

    public boolean showHealth() {
        return showHealth.getValue();
    }
}
