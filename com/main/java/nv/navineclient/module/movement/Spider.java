package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Spider extends Module {
    private final NumberSetting speed = new NumberSetting("Speed", "Climb speed", 0.3, 0.1, 1.0);
    private final BooleanSetting requireJump = new BooleanSetting("RequireJump", "Require jump key", false);
    private final NumberSetting maxHeight = new NumberSetting("MaxHeight", "Max climb height", 256.0, 1.0, 320.0);

    public Spider() {
        super("Spider", "Climb walls like a spider", Category.MOVEMENT);
        addSetting(speed);
        addSetting(requireJump);
        addSetting(maxHeight);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!mc.player.horizontalCollision) return;
        if (requireJump.getValue() && !mc.options.keyJump.isDown()) return;
        if (mc.player.getY() > maxHeight.getValue()) return;

        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, speed.getValue(), mc.player.getDeltaMovement().z);
    }
}
