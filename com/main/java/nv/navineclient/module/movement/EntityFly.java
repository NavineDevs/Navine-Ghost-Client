package nv.navineclient.module.movement;

import net.minecraft.world.entity.Entity;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class EntityFly extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", "Flight speed", 2.0, 0.5, 10.0);
    private final NumberSetting vertSpeed = new NumberSetting("VertSpeed", "Vertical speed", 1.0, 0.5, 5.0);
    private final BooleanSetting antiKick = new BooleanSetting("AntiKick", "Prevent fly kick", true);

    public EntityFly() {
        super("EntityFly", "Fly while riding any entity", Category.MOVEMENT);
        addSetting(speed);
        addSetting(vertSpeed);
        addSetting(antiKick);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.getVehicle() == null) return;
        
        Entity vehicle = mc.player.getVehicle();
        float yaw = mc.player.getYRot();
        double s = speed.getValue();
        
        double motionX = 0;
        double motionY = 0;
        double motionZ = 0;
        
        if (mc.options.keyUp.isDown()) {
            motionX -= Math.sin(Math.toRadians(yaw)) * s;
            motionZ += Math.cos(Math.toRadians(yaw)) * s;
        }
        if (mc.options.keyDown.isDown()) {
            motionX += Math.sin(Math.toRadians(yaw)) * s;
            motionZ -= Math.cos(Math.toRadians(yaw)) * s;
        }
        if (mc.options.keyJump.isDown()) {
            motionY = vertSpeed.getValue();
        }
        if (mc.options.keyShift.isDown()) {
            motionY = -vertSpeed.getValue();
        }
        
        vehicle.setDeltaMovement(motionX, motionY, motionZ);

        if (antiKick.getValue() && motionY >= 0 && !vehicle.onGround()) {
            vehicle.setDeltaMovement(motionX, -0.04, motionZ);
        }
    }
}
