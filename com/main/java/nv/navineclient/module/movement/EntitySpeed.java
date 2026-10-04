package nv.navineclient.module.movement;

import net.minecraft.world.entity.Entity;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class EntitySpeed extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", "Entity speed multiplier", 2.0, 1.0, 10.0);
    private final NumberSetting vertSpeed = new NumberSetting("VertSpeed", "Vertical speed", 0.3, 0.1, 2.0);
    private final BooleanSetting horsesOnly = new BooleanSetting("Horses", "Only horses and similar", false);

    public EntitySpeed() {
        super("EntitySpeed", "Control entity speed (horses, pigs, etc)", Category.MOVEMENT);
        addSetting(speed);
        addSetting(vertSpeed);
        addSetting(horsesOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.getVehicle() == null) return;

        Entity vehicle = mc.player.getVehicle();
        if (horsesOnly.getValue() && !(vehicle instanceof net.minecraft.world.entity.animal.equine.AbstractHorse)) {
            return;
        }

        float yaw = mc.player.getYRot();
        double s = speed.getValue() * 0.3;

        double motionX = 0;
        double motionZ = 0;

        if (mc.options.keyUp.isDown()) {
            motionX -= Math.sin(Math.toRadians(yaw)) * s;
            motionZ += Math.cos(Math.toRadians(yaw)) * s;
        }
        if (mc.options.keyDown.isDown()) {
            motionX += Math.sin(Math.toRadians(yaw)) * s;
            motionZ -= Math.cos(Math.toRadians(yaw)) * s;
        }
        if (mc.options.keyLeft.isDown()) {
            motionX += Math.cos(Math.toRadians(yaw)) * s;
            motionZ += Math.sin(Math.toRadians(yaw)) * s;
        }
        if (mc.options.keyRight.isDown()) {
            motionX -= Math.cos(Math.toRadians(yaw)) * s;
            motionZ -= Math.sin(Math.toRadians(yaw)) * s;
        }

        double motionY = vehicle.getDeltaMovement().y;
        if (mc.options.keyJump.isDown()) {
            motionY = vertSpeed.getValue();
        } else if (mc.options.keyShift.isDown()) {
            motionY = -vertSpeed.getValue();
        }

        vehicle.setDeltaMovement(motionX, motionY, motionZ);
    }
}
