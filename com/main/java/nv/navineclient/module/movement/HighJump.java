package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class HighJump extends Module {
    private final NumberSetting height = new NumberSetting("Height", "Jump height multiplier", 2.0, 1.1, 5.0);
    private final BooleanSetting preserveHorizontal = new BooleanSetting("Preserve", "Keep horizontal motion", true);
    private final NumberSetting cooldown = new NumberSetting("Cooldown", "Ticks between jumps", 0.0, 0.0, 20.0);

    private int jumpCooldown = 0;

    public HighJump() {
        super("HighJump", "Jump higher than normal", Category.MOVEMENT);
        addSetting(height);
        addSetting(preserveHorizontal);
        addSetting(cooldown);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (jumpCooldown > 0) {
            jumpCooldown--;
            return;
        }

        if (mc.options.keyJump.consumeClick() && mc.player.onGround()) {
            double motionX = preserveHorizontal.getValue() ? mc.player.getDeltaMovement().x : 0;
            double motionZ = preserveHorizontal.getValue() ? mc.player.getDeltaMovement().z : 0;
            mc.player.setDeltaMovement(motionX, 0.42 * height.getValue(), motionZ);
            jumpCooldown = cooldown.getValue().intValue();
        }
    }
}
