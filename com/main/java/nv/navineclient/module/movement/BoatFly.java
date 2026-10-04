package nv.navineclient.module.movement;

import net.minecraft.world.entity.vehicle.boat.Boat;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class BoatFly extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", "Flight speed", 2.0, 0.5, 10.0);
    private final NumberSetting vertSpeed = new NumberSetting("VertSpeed", "Vertical speed", 1.0, 0.5, 5.0);
    private final NumberSetting maxAltitude = new NumberSetting("MaxAlt", "Max altitude", 320.0, 64.0, 512.0);

    public BoatFly() {
        super("BoatFly", "Fly while riding a boat", Category.MOVEMENT);
        addSetting(speed);
        addSetting(vertSpeed);
        addSetting(maxAltitude);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !(mc.player.getVehicle() instanceof Boat boat)) return;
        
        double motionX = 0;
        double motionY = 0;
        double motionZ = 0;
        
        float yaw = mc.player.getYRot();
        double s = speed.getValue();
        
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
        if (mc.options.keyJump.isDown() && boat.getY() < maxAltitude.getValue()) {
            motionY = vertSpeed.getValue();
        }
        if (mc.options.keyShift.isDown()) {
            motionY = -vertSpeed.getValue();
        }
        
        boat.setDeltaMovement(motionX, motionY, motionZ);
    }
}
