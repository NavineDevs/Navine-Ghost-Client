package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

import java.util.Random;

public class AntiAfk extends Module {
    private final BooleanSetting rotate = new BooleanSetting("Rotate", "Randomly rotate", true);
    private final BooleanSetting jump = new BooleanSetting("Jump", "Randomly jump", true);
    private final BooleanSetting move = new BooleanSetting("Move", "Randomly move", false);
    private final NumberSetting interval = new NumberSetting("Interval", "Action interval (ticks)", 40.0, 10.0, 200.0);
    
    private int ticks = 0;
    private final Random random = new Random();

    public AntiAfk() {
        super("AntiAfk", "Prevents AFK kick", Category.PLAYER);
        addSetting(rotate);
        addSetting(jump);
        addSetting(move);
        addSetting(interval);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        
        ticks++;
        if (ticks < interval.getValue()) return;
        ticks = 0;
        
        if (rotate.getValue()) {
            mc.player.setYRot(mc.player.getYRot() + (random.nextFloat() - 0.5f) * 30f);
        }
        
        if (jump.getValue() && mc.player.onGround() && random.nextFloat() > 0.7f) {
            mc.player.jumpFromGround();
        }
        
        if (move.getValue()) {
            float yaw = mc.player.getYRot() + (random.nextFloat() - 0.5f) * 90f;
            double speed = 0.1;
            double dx = -Math.sin(Math.toRadians(yaw)) * speed;
            double dz = Math.cos(Math.toRadians(yaw)) * speed;
            mc.player.setDeltaMovement(dx, mc.player.getDeltaMovement().y, dz);
        }
    }
}
