package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AirJump extends Module {
    private final NumberSetting maxJumps = new NumberSetting("MaxJumps", "Max air jumps", 2.0, 1.0, 10.0);
    private final NumberSetting boost = new NumberSetting("Boost", "Jump velocity boost", 1.0, 0.5, 3.0);
    private final BooleanSetting onlyMoving = new BooleanSetting("OnlyMoving", "Require movement input", false);

    private int airJumps = 0;

    public AirJump() {
        super("AirJump", "Jump while in the air", Category.MOVEMENT);
        addSetting(maxJumps);
        addSetting(boost);
        addSetting(onlyMoving);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mc.player.onGround()) {
            airJumps = 0;
            return;
        }

        if (!mc.options.keyJump.consumeClick()) return;

        if (airJumps >= maxJumps.getValue().intValue()) return;

        if (onlyMoving.getValue()) {
            boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
            if (!moving) return;
        }

        mc.player.jumpFromGround();
        double b = boost.getValue();
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.42 * b, mc.player.getDeltaMovement().z);
        airJumps++;
    }

    @Override
    public void onDisable() {
        airJumps = 0;
    }
}
