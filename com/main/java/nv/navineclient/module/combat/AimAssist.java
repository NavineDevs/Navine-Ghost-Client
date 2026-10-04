package nv.navineclient.module.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.util.CombatTargeting;

import java.util.Comparator;
import java.util.List;

public class AimAssist extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Target range", 4.5, 1.0, 10.0);
    private final NumberSetting horizontalSpeed = new NumberSetting("HSpeed", "Horizontal aim speed", 45.0, 5.0, 180.0);
    private final NumberSetting verticalSpeed = new NumberSetting("VSpeed", "Vertical aim speed", 35.0, 5.0, 180.0);
    private final NumberSetting maxAngle = new NumberSetting("MaxAngle", "Max FOV angle", 90.0, 15.0, 180.0);
    private final ModeSetting targetMode = new ModeSetting("Target", "Target priority", "Distance", "Distance", "Health", "Angle");
    private final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Target hostile mobs", false);
    private final BooleanSetting animals = new BooleanSetting("Animals", "Target animals", false);
    private final BooleanSetting requireMouseDown = new BooleanSetting("MouseDown", "Require attack key held", true);
    private final BooleanSetting aimVertical = new BooleanSetting("Vertical", "Adjust pitch", true);
    private final BooleanSetting syncRotation = new BooleanSetting("SyncRot", "Send rotation to server", true);

    private float lastSentYaw = Float.NaN;
    private float lastSentPitch = Float.NaN;

    public AimAssist() {
        super("AimAssist", "Smoothly assists aim toward nearby targets", Category.COMBAT);
        addSetting(range);
        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(maxAngle);
        addSetting(targetMode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(requireMouseDown);
        addSetting(aimVertical);
        addSetting(syncRotation);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (requireMouseDown.getValue() && !mc.options.keyAttack.isDown()) {
            return;
        }
        LivingEntity target = findBestTarget();
        if (target == null) {
            return;
        }
        float[] rotations = CombatTargeting.rotationsTo(target, mc);
        float maxYaw = (float) (horizontalSpeed.getValue() / 20.0);
        float maxPitch = (float) (verticalSpeed.getValue() / 20.0);
        float[] rotated = AcBypassUtil.smoothRotation(
                mc.player.getYRot(),
                mc.player.getXRot(),
                rotations[0],
                aimVertical.getValue() ? rotations[1] : mc.player.getXRot(),
                Math.max(maxYaw, maxPitch));
        mc.player.setYRot(rotated[0]);
        if (aimVertical.getValue()) {
            mc.player.setXRot(rotated[1]);
        }
        if (syncRotation.getValue()) {
            float yawDelta = CombatTargeting.rotationDelta(
                    Float.isNaN(lastSentYaw) ? rotated[0] : lastSentYaw, rotated[0]);
            float pitchDelta = CombatTargeting.rotationDelta(
                    Float.isNaN(lastSentPitch) ? rotated[1] : lastSentPitch, rotated[1]);
            if (yawDelta > 0.35f || pitchDelta > 0.35f) {
                AcBypassUtil.sendRotationPacket(rotated[0], rotated[1]);
                lastSentYaw = rotated[0];
                lastSentPitch = rotated[1];
            }
        }
    }

    private LivingEntity findBestTarget() {
        List<LivingEntity> targets = CombatTargeting.collect(
                mc,
                range.getValue(),
                maxAngle.getValue().floatValue(),
                this::isValidTarget,
                getTargetComparator());
        return targets.isEmpty() ? null : targets.get(0);
    }

    private Comparator<LivingEntity> getTargetComparator() {
        String mode = targetMode.getValue();
        if (mode.equals("Health")) {
            return Comparator.comparingDouble(LivingEntity::getHealth);
        }
        if (mode.equals("Angle")) {
            return Comparator.comparingDouble(e -> {
                float[] rot = CombatTargeting.rotationsTo(e, mc);
                float yawDiff = CombatTargeting.rotationDelta(mc.player.getYRot(), rot[0]);
                float pitchDiff = CombatTargeting.rotationDelta(mc.player.getXRot(), rot[1]);
                return yawDiff + pitchDiff;
            });
        }
        return Comparator.comparingDouble(e -> mc.player.distanceTo(e));
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Player && players.getValue()) {
            return true;
        }
        if (entity instanceof Monster && mobs.getValue()) {
            return true;
        }
        return entity instanceof Animal && animals.getValue();
    }
}
