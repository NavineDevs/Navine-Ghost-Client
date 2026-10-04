package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class FastLadder extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", "Ladder climb speed", 0.5, 0.2, 1.0);
    private final NumberSetting downSpeed = new NumberSetting("DownSpeed", "Descend speed", 0.5, 0.2, 1.0);
    private final BooleanSetting vanillaCap = new BooleanSetting("SafeCap", "Limit speed to vanilla max", false);

    public FastLadder() {
        super("FastLadder", "Climb ladders faster", Category.MOVEMENT);
        addSetting(speed);
        addSetting(downSpeed);
        addSetting(vanillaCap);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!mc.player.onClimbable()) return;

        double up = speed.getValue();
        double down = downSpeed.getValue();
        if (vanillaCap.getValue()) {
            up = Math.min(up, 0.15);
            down = Math.min(down, 0.15);
        }

        if (mc.options.keyUp.isDown() || mc.options.keyJump.isDown()) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, up, mc.player.getDeltaMovement().z);
        } else if (mc.options.keyShift.isDown()) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -down, mc.player.getDeltaMovement().z);
        }
    }
}
