package nv.navineclient.module.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BowAimbot extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Target range", 50.0, 10.0, 100.0);
    private final BooleanSetting predictMovement = new BooleanSetting("Predict", "Predict target movement", true);
    private final BooleanSetting playersOnly = new BooleanSetting("Players", "Only target players", true);

    public BowAimbot() {
        super("BowAimbot", "Automatically aims bow at targets", Category.COMBAT);
        addSetting(range);
        addSetting(predictMovement);
        addSetting(playersOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        
        boolean holdingBow = mc.player.getMainHandItem().getItem() instanceof BowItem ||
                            mc.player.getMainHandItem().getItem() instanceof CrossbowItem;
        if (!holdingBow) return;
        if (!mc.player.isUsingItem()) return;
        
        LivingEntity target = findTarget();
        if (target == null) return;
        
        float[] rotations = calculateBowRotations(target);
        mc.player.setYRot(rotations[0]);
        mc.player.setXRot(rotations[1]);
    }
    
    private LivingEntity findTarget() {
        List<LivingEntity> targets = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
            .filter(e -> e instanceof LivingEntity)
            .map(e -> (LivingEntity) e)
            .filter(e -> e != mc.player)
            .filter(e -> e.isAlive())
            .filter(e -> mc.player.distanceTo(e) <= range.getValue())
            .filter(e -> !playersOnly.getValue() || e instanceof Player)
            .sorted(Comparator.comparingDouble(e -> mc.player.distanceTo(e)))
            .collect(Collectors.toList());
        
        return targets.isEmpty() ? null : targets.get(0);
    }
    
    private float[] calculateBowRotations(Entity target) {
        double targetX = target.getX();
        double targetY = target.getY() + target.getBbHeight() * 0.5;
        double targetZ = target.getZ();
        
        if (predictMovement.getValue()) {
            double dist = mc.player.distanceTo(target);
            double ticks = dist / 3.0;
            targetX += target.getDeltaMovement().x * ticks;
            targetY += target.getDeltaMovement().y * ticks;
            targetZ += target.getDeltaMovement().z * ticks;
        }
        
        double dx = targetX - mc.player.getX();
        double dy = targetY - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = targetZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        
        int charge = mc.player.getTicksUsingItem();
        double velocity = Math.min(charge / 20.0, 1.0) * 3.0;
        double gravity = 0.05;
        double pitch = Math.toDegrees(Math.atan((velocity * velocity - Math.sqrt(velocity * velocity * velocity * velocity - gravity * (gravity * dist * dist + 2 * dy * velocity * velocity))) / (gravity * dist)));
        
        if (Double.isNaN(pitch)) {
            pitch = -Math.toDegrees(Math.atan2(dy, dist));
        }
        
        return new float[]{yaw, (float) -pitch};
    }
}
