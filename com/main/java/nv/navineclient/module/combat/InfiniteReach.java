package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class InfiniteReach extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Attack range", 50.0, 6.0, 100.0);
    private final BooleanSetting playersOnly = new BooleanSetting("Players", "Only target players", true);
    private final BooleanSetting multiTarget = new BooleanSetting("MultiTarget", "Attack multiple targets", false);
    private final NumberSetting maxTargets = new NumberSetting("MaxTargets", "Max targets per attack", 1.0, 1.0, 10.0);
    private final NumberSetting switchDelay = new NumberSetting("SwitchDelay", "Delay between targets (ms)", 50.0, 0.0, 200.0);
    private final BooleanSetting requireLook = new BooleanSetting("RequireLook", "Must be looking at target", true);
    
    private long lastAttack = 0;

    public InfiniteReach() {
        super("InfiniteReach", "Attack entities from far away", Category.COMBAT);
        addSetting(range);
        addSetting(playersOnly);
        addSetting(multiTarget);
        addSetting(maxTargets);
        addSetting(switchDelay);
        addSetting(requireLook);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        
        if (!mc.options.keyAttack.isDown()) return;
        
        long time = System.currentTimeMillis();
        if (time - lastAttack < 500) return;
        
        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) return;
        
        // Multi-target attack
        if (multiTarget.getValue() && targets.size() > 1) {
            int maxTargetsToHit = (int) Math.min(maxTargets.getValue(), targets.size());
            long delay = switchDelay.getValue().longValue();
            if (delay <= 0) {
                for (int i = 0; i < maxTargetsToHit; i++) {
                    mc.gameMode.attack(mc.player, targets.get(i));
                }
                ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
            } else if (time - lastAttack >= delay) {
                int index = (int) ((time / delay) % maxTargetsToHit);
                mc.gameMode.attack(mc.player, targets.get(index));
                ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
                lastAttack = time;
                return;
            } else {
                return;
            }
        } else {
            // Single target attack
            LivingEntity target = targets.get(0);
            mc.gameMode.attack(mc.player, target);
            ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
        }
        
        lastAttack = time;
    }
    
    private List<LivingEntity> findTargets() {
        float[] look = new float[]{mc.player.getYRot(), mc.player.getXRot()};
        
        List<LivingEntity> targets = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
            .filter(e -> e instanceof LivingEntity)
            .map(e -> (LivingEntity) e)
            .filter(e -> e != mc.player)
            .filter(e -> e.isAlive())
            .filter(e -> mc.player.distanceTo(e) <= range.getValue())
            .filter(e -> !playersOnly.getValue() || e instanceof Player)
            .filter(e -> !requireLook.getValue() || isLookingAt(e, look))
            .sorted(Comparator.comparingDouble(e -> mc.player.distanceTo(e)))
            .collect(Collectors.toList());
        
        return targets;
    }
    
    private boolean isLookingAt(Entity entity, float[] look) {
        double dx = entity.getX() - mc.player.getX();
        double dy = (entity.getY() + entity.getBbHeight() / 2) - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = entity.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        
        float yawDiff = Math.abs(wrapDegrees(look[0] - targetYaw));
        float pitchDiff = Math.abs(look[1] - targetPitch);
        
        return yawDiff < 10 && pitchDiff < 10;
    }
    
    private float wrapDegrees(float degrees) {
        degrees = degrees % 360;
        if (degrees >= 180) degrees -= 360;
        if (degrees < -180) degrees += 360;
        return degrees;
    }
}
