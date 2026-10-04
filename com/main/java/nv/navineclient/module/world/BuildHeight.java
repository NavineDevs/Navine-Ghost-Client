package nv.navineclient.module.world;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class BuildHeight extends Module {
    public static BuildHeight INSTANCE;

    private final NumberSetting extraHeight = new NumberSetting("ExtraHeight", "Blocks above world limit", 64.0, 1.0, 256.0);
    private final BooleanSetting showWarning = new BooleanSetting("Warning", "Warn when near limit", true);
    private final BooleanSetting creativeOnly = new BooleanSetting("CreativeOnly", "Only in creative mode", false);

    public BuildHeight() {
        super("BuildHeight", "Allows you to build above the world height limit (visual)", Category.WORLD);
        INSTANCE = this;
        addSetting(extraHeight);
        addSetting(showWarning);
        addSetting(creativeOnly);
    }

    public int getExtraHeight() {
        return extraHeight.getValue().intValue();
    }

    public boolean shouldShowWarning() {
        return showWarning.getValue();
    }

    public boolean isActive() {
        if (!isEnabled()) return false;
        if (creativeOnly.getValue() && mc.player != null && !mc.player.isCreative()) {
            return false;
        }
        return true;
    }

    public static boolean isBuildHeight() {
        return INSTANCE != null && INSTANCE.isActive();
    }
}
