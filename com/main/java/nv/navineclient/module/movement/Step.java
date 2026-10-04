package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;

public class Step extends Module {
    public static Step INSTANCE;

    private final NumberSetting height = new NumberSetting("Height", "Step height in blocks", 1.0, 0.6, 5.0);
    private final BooleanSetting onlyForward = new BooleanSetting("Forward", "Only when moving forward", true);

    public Step() {
        super("Step", "Step up blocks instantly", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(height);
        addSetting(onlyForward);
    }

    public float getStepHeight() {
        if (!isEnabled() || mc.player == null) {
            return 0.6f;
        }
        if (onlyForward.getValue() && !mc.options.keyUp.isDown()) {
            return 0.6f;
        }
        return AcBypassUtil.clampStepHeight(height.getValue().floatValue());
    }
}
