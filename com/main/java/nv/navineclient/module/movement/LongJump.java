package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class LongJump extends Module {
    private final NumberSetting boost = new NumberSetting("Boost", "Jump boost power", 1.5, 1.0, 5.0);
    private final NumberSetting verticalBoost = new NumberSetting("Vertical", "Vertical boost", 0.42, 0.2, 1.5);
    private final BooleanSetting onlySprint = new BooleanSetting("Sprint", "Only while sprinting", false);

    private boolean jumped = false;

    public LongJump() {
        super("LongJump", "Jump further than normal", Category.MOVEMENT);
        addSetting(boost);
        addSetting(verticalBoost);
        addSetting(onlySprint);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mc.player.onGround()) {
            jumped = false;
        }

        if (onlySprint.getValue() && !mc.player.isSprinting()) return;

        if (mc.options.keyJump.consumeClick() && mc.player.onGround() && !jumped) {
            double b = boost.getValue();
            float yaw = mc.player.getYRot();
            double motionX = -Math.sin(Math.toRadians(yaw)) * b * 0.5;
            double motionZ = Math.cos(Math.toRadians(yaw)) * b * 0.5;
            mc.player.setDeltaMovement(motionX, verticalBoost.getValue(), motionZ);
            jumped = true;
        }
    }
}
