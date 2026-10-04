package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.render.TargetHUD;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.util.CombatTargeting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class KillAura extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Attack range", 4.0, 1.0, 10.0);
    private final NumberSetting swingRange = new NumberSetting("SwingRange", "Rotate toward targets within this range", 4.5, 1.0, 10.0);
    private final NumberSetting cps = new NumberSetting("CPS", "Clicks per second", 12.0, 1.0, 20.0);
    private final ModeSetting targetMode = new ModeSetting("TargetMode", "Target selection", "Distance", "Distance", "Health", "Angle");
    private final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Target hostile mobs", true);
    private final BooleanSetting animals = new BooleanSetting("Animals", "Target animals", false);
    private final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate to target", true);
    private final BooleanSetting autoSwing = new BooleanSetting("Swing", "Swing arm", true);
    private final NumberSetting rotationSpeed = new NumberSetting("RotSpeed", "Rotation speed", 180.0, 30.0, 360.0);
    private final NumberSetting maxTargets = new NumberSetting("MaxTargets", "Max targets per attack", 1.0, 1.0, 10.0);
    private final BooleanSetting multiTarget = new BooleanSetting("MultiTarget", "Attack multiple targets", false);
    private final BooleanSetting prioritizePlayers = new BooleanSetting("PrioritizePlayers", "Target players first", true);
    private final NumberSetting switchDelay = new NumberSetting("SwitchDelay", "Delay between targets (ms)", 50.0, 0.0, 200.0);
    private final NumberSetting fov = new NumberSetting("FOV", "Max attack FOV", 120.0, 30.0, 180.0);
    private final BooleanSetting guiCheck = new BooleanSetting("GuiCheck", "Pause while GUI is open", true);

    private long lastAttack = 0;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private boolean hasRotationTarget = false;
    private LivingEntity currentTarget = null;
    private long lastTargetSwitch = 0;
    private LivingEntity lastTargetEntity = null;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
        addSetting(range);
        addSetting(swingRange);
        addSetting(cps);
        addSetting(targetMode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(rotate);
        addSetting(autoSwing);
        addSetting(rotationSpeed);
        addSetting(multiTarget);
        addSetting(maxTargets);
        addSetting(prioritizePlayers);
        addSetting(switchDelay);
        addSetting(fov);
        addSetting(guiCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (guiCheck.getValue() && ClientScreens.get(mc) != null) {
            return;
        }

        TargetHUD targetHUD = (TargetHUD) nv.navineclient.module.ModuleManager.getModuleByName("TargetHUD");

        long time = System.currentTimeMillis();
        double baseDelay = 1000.0 / AcBypassUtil.clampCps(AcBypassUtil.jitter(cps.getValue(), 0.03));
        long delay = (long) baseDelay;

        if (time - lastAttack < delay) {
            if (rotate.getValue() && hasRotationTarget) {
                smoothRotate();
            }
            return;
        }

        if (mc.player.getAttackStrengthScale(0.0f) < 0.92f) {
            if (rotate.getValue() && hasRotationTarget) {
                smoothRotate();
            }
            return;
        }

        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            clearRotationTarget();
            currentTarget = null;
            if (targetHUD != null && targetHUD.isEnabled()) {
                targetHUD.setTarget(null);
            }
            return;
        }

        LivingEntity attackTarget = targets.stream()
                .filter(e -> mc.player.distanceTo(e) <= range.getValue())
                .findFirst()
                .orElse(null);
        if (attackTarget == null) {
            LivingEntity rotateTarget = targets.get(0);
            if (rotate.getValue()) {
                setRotationTarget(rotateTarget);
                smoothRotate();
            }
            return;
        }

        targets = targets.stream()
                .filter(e -> mc.player.distanceTo(e) <= range.getValue())
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return;
        }

        if (multiTarget.getValue() && targets.size() > 1) {
            long now = System.currentTimeMillis();
            if (lastTargetEntity != null && !targets.contains(lastTargetEntity)
                    && now - lastTargetSwitch < switchDelay.getValue()) {
                return;
            }
            int maxTargetsToHit = (int) Math.min(maxTargets.getValue(), targets.size());
            for (int i = 0; i < maxTargetsToHit; i++) {
                LivingEntity target = targets.get(i);
                currentTarget = target;
                if (i == 0 && targetHUD != null && targetHUD.isEnabled()) {
                    targetHUD.setTarget(target);
                }
                if (rotate.getValue() && i == 0) {
                    setRotationTarget(target);
                    smoothRotate();
                    AcBypassUtil.sendRotationPacket(mc.player.getYRot(), mc.player.getXRot());
                }
                mc.gameMode.attack(mc.player, target);
                if (autoSwing.getValue() && i == 0) {
                    ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
                }
            }
        } else {
            LivingEntity target = targets.get(0);
            currentTarget = target;
            if (targetHUD != null && targetHUD.isEnabled()) {
                targetHUD.setTarget(target);
            }

            if (rotate.getValue()) {
                setRotationTarget(target);
                smoothRotate();
                AcBypassUtil.sendRotationPacket(mc.player.getYRot(), mc.player.getXRot());
            } else {
                clearRotationTarget();
            }

            mc.gameMode.attack(mc.player, target);
            if (autoSwing.getValue()) {
                ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
            }
        }
        lastTargetEntity = currentTarget;
        lastTargetSwitch = System.currentTimeMillis();
        lastAttack = time;
    }

    private void setRotationTarget(LivingEntity target) {
        float[] rotations = CombatTargeting.rotationsTo(target, mc);
        targetYaw = rotations[0];
        targetPitch = rotations[1];
        hasRotationTarget = true;
    }

    private void clearRotationTarget() {
        hasRotationTarget = false;
        targetYaw = 0;
        targetPitch = 0;
    }

    private void smoothRotate() {
        if (!hasRotationTarget) {
            return;
        }
        float maxRotation = (float) (rotationSpeed.getValue() / 20.0);
        float[] rotated = AcBypassUtil.smoothRotation(
                mc.player.getYRot(), mc.player.getXRot(), targetYaw, targetPitch, maxRotation);
        mc.player.setYRot(rotated[0]);
        mc.player.setXRot(rotated[1]);
    }

    private List<LivingEntity> findTargets() {
        double maxRange = Math.max(range.getValue(), swingRange.getValue());
        List<LivingEntity> allTargets = CombatTargeting.collect(
                mc,
                maxRange,
                fov.getValue().floatValue(),
                this::isValidTarget,
                null);

        if (prioritizePlayers.getValue()) {
            List<LivingEntity> playerTargets = allTargets.stream()
                    .filter(e -> e instanceof Player)
                    .sorted(getTargetComparator())
                    .collect(Collectors.toList());

            List<LivingEntity> otherTargets = allTargets.stream()
                    .filter(e -> !(e instanceof Player))
                    .sorted(getTargetComparator())
                    .collect(Collectors.toList());

            List<LivingEntity> prioritized = new ArrayList<>();
            prioritized.addAll(playerTargets);
            prioritized.addAll(otherTargets);
            return prioritized;
        }

        return allTargets.stream()
                .sorted(getTargetComparator())
                .collect(Collectors.toList());
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
