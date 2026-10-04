package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;

public class Flight extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Flight mode", "Creative", "Creative", "Vanilla", "Glide");
    private final NumberSetting speed = new NumberSetting("Speed", "Flight speed", 1.0, 0.1, 5.0);
    private final BooleanSetting antiKick = new BooleanSetting("AntiKick", "Prevent fly kick", true);
    
    private int antiKickTicks = 0;
    private int flightTickCounter = 0;

    public Flight() {
        super("Flight", "Allows you to fly", Category.MOVEMENT);
        addSetting(mode);
        addSetting(speed);
        addSetting(antiKick);
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().mayfly = false;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        
        flightTickCounter++;
        
        String m = mode.getValue();
        double s = AcBypassUtil.clampSpeedMultiplier(AcBypassUtil.jitter(speed.getValue(), 0.03));

        if (m.equals("Creative") && AcBypassUtil.allowVanillaFlight()) {
            mc.player.getAbilities().mayfly = true;
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlyingSpeed((float) (s * 0.05));
        } else if (m.equals("Vanilla") && AcBypassUtil.allowVanillaFlight()) {
            // True flight - not affected by gravity
            double motionX = 0;
            double motionY = 0;
            double motionZ = 0;

            float yaw = mc.player.getYRot();
            double speed = s * 0.2;

            if (mc.options.keyUp.isDown()) {
                motionX -= Math.sin(Math.toRadians(yaw)) * speed;
                motionZ += Math.cos(Math.toRadians(yaw)) * speed;
            }
            if (mc.options.keyDown.isDown()) {
                motionX += Math.sin(Math.toRadians(yaw)) * speed;
                motionZ -= Math.cos(Math.toRadians(yaw)) * speed;
            }
            if (mc.options.keyLeft.isDown()) {
                motionX += Math.cos(Math.toRadians(yaw)) * speed;
                motionZ += Math.sin(Math.toRadians(yaw)) * speed;
            }
            if (mc.options.keyRight.isDown()) {
                motionX -= Math.cos(Math.toRadians(yaw)) * speed;
                motionZ -= Math.sin(Math.toRadians(yaw)) * speed;
            }
            if (mc.options.keyJump.isDown()) {
                motionY = speed * 1.5;
            } else if (mc.options.keyShift.isDown()) {
                motionY = -speed * 1.5;
            } else {
                // No gravity - true flight
                motionY = 0;
            }

            mc.player.setDeltaMovement(motionX, motionY, motionZ);
            mc.player.setOnGround(false);
        } else if (m.equals("Glide")) {
            if (mc.player.getDeltaMovement().y < 0) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -0.1, mc.player.getDeltaMovement().z);
            }
        }
        
        if (antiKick.getValue()) {
            antiKickTicks++;
            // More subtle anti-kick - only when needed
            if (antiKickTicks >= 10 && !mc.player.onGround()) {
                // Very subtle downward motion to prevent fly kick
                double currentY = mc.player.getY();
                double floorY = Math.floor(currentY);
                double distanceToFloor = currentY - floorY;
                
                // Only apply anti-kick if we're floating
                if (distanceToFloor > 0.1 && distanceToFloor < 0.9) {
                    // Very subtle downward push
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -0.01, mc.player.getDeltaMovement().z);
                }
                
                // Occasionally simulate ground contact
                if (antiKickTicks % 40 == 0 && distanceToFloor < 0.2) {
                    mc.player.setOnGround(true);
                }
                
                antiKickTicks = 0;
            }
        }
    }
}
