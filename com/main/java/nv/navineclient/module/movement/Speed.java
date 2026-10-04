package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.BooleanSetting;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.util.AcBypassUtil;

public class Speed extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Speed mode", "Vanilla", "Vanilla", "BHop", "Strafe");
    private final NumberSetting speedMult = new NumberSetting("Speed", "Speed multiplier", 1.5, 1.0, 5.0);
    private final BooleanSetting gradual = new BooleanSetting("Gradual", "Gradual acceleration", true);

    private double currentSpeed = 0.0;

    public Speed() {
        super("Speed", "Move faster", Category.MOVEMENT);
        addSetting(mode);
        addSetting(speedMult);
        addSetting(gradual);
    }

    @Override
    public void onTick() {
        if (mc.player == null) {
            return;
        }

        String m = mode.getValue();
        double s = AcBypassUtil.clampSpeedMultiplier(AcBypassUtil.jitter(speedMult.getValue(), 0.02));

        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        if (!moving) {
            if (gradual.getValue()) {
                currentSpeed *= 0.9;
            } else {
                currentSpeed = 0;
            }
            return;
        }

        float yaw = mc.player.getYRot();
        double baseSpeed = s * 0.3;

        if (gradual.getValue()) {
            double targetSpeed = baseSpeed;
            currentSpeed += (targetSpeed - currentSpeed) * 0.3;
            baseSpeed = currentSpeed;
        }

        double motionX = 0;
        double motionZ = 0;

        if (mc.options.keyUp.isDown()) {
            motionX -= Math.sin(Math.toRadians(yaw)) * baseSpeed;
            motionZ += Math.cos(Math.toRadians(yaw)) * baseSpeed;
        }
        if (mc.options.keyDown.isDown()) {
            motionX += Math.sin(Math.toRadians(yaw)) * baseSpeed;
            motionZ -= Math.cos(Math.toRadians(yaw)) * baseSpeed;
        }
        if (mc.options.keyLeft.isDown()) {
            motionX += Math.cos(Math.toRadians(yaw)) * baseSpeed;
            motionZ += Math.sin(Math.toRadians(yaw)) * baseSpeed;
        }
        if (mc.options.keyRight.isDown()) {
            motionX -= Math.cos(Math.toRadians(yaw)) * baseSpeed;
            motionZ -= Math.sin(Math.toRadians(yaw)) * baseSpeed;
        }

        double maxStep = AcBypassUtil.maxHorizontalBps() / 20.0;

        if (m.equals("Vanilla")) {
            if (mc.player.onGround() || !gradual.getValue()) {
                Vec3 motion = new Vec3(motionX, mc.player.getDeltaMovement().y, motionZ);
                motion = AcBypassUtil.clampHorizontalSpeed(motion, maxStep);
                mc.player.setDeltaMovement(motion);
            } else {
                Vec3 motion = new Vec3(
                        mc.player.getDeltaMovement().x * 0.9 + motionX * 0.1,
                        mc.player.getDeltaMovement().y,
                        mc.player.getDeltaMovement().z * 0.9 + motionZ * 0.1);
                mc.player.setDeltaMovement(AcBypassUtil.clampHorizontalSpeed(motion, maxStep));
            }
        } else if (m.equals("BHop")) {
            if (mc.player.onGround()) {
                Vec3 motion = AcBypassUtil.clampHorizontalSpeed(
                        new Vec3(motionX, mc.player.getDeltaMovement().y, motionZ), maxStep);
                mc.player.setDeltaMovement(motion);
                mc.player.jumpFromGround();
            } else {
                Vec3 motion = new Vec3(
                        mc.player.getDeltaMovement().x * 0.95 + motionX * 0.05,
                        mc.player.getDeltaMovement().y,
                        mc.player.getDeltaMovement().z * 0.95 + motionZ * 0.05);
                mc.player.setDeltaMovement(AcBypassUtil.clampHorizontalSpeed(motion, maxStep));
            }
        } else if (m.equals("Strafe")) {
            double horizontal = Math.sqrt(
                    mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x
                            + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z);
            double targetSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);
            if (targetSpeed > horizontal) {
                double speedIncrease = (targetSpeed - horizontal) * 0.2;
                Vec3 motion = new Vec3(
                        mc.player.getDeltaMovement().x * 0.9 + motionX * speedIncrease,
                        mc.player.getDeltaMovement().y,
                        mc.player.getDeltaMovement().z * 0.9 + motionZ * speedIncrease);
                mc.player.setDeltaMovement(AcBypassUtil.clampHorizontalSpeed(motion, maxStep));
            }
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
        }
    }

    @Override
    public void onDisable() {
        currentSpeed = 0.0;
    }
}
