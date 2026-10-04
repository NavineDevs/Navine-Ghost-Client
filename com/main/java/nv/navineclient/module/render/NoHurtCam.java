package nv.navineclient.module.render;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class NoHurtCam extends Module {
    public static NoHurtCam INSTANCE;

    private final BooleanSetting onlyDamage = new BooleanSetting("OnlyDamage", "Only reduce damage shake", true);
    private final NumberSetting reduction = new NumberSetting("Reduction", "Shake reduction percent", 100.0, 0.0, 100.0);
    private final BooleanSetting clearHurtTime = new BooleanSetting("ClearHurt", "Clear hurt time each frame", true);

    public NoHurtCam() {
        super("NoHurtCam", "Disables the hurt camera shake", Category.RENDER);
        INSTANCE = this;
        addSetting(onlyDamage);
        addSetting(reduction);
        addSetting(clearHurtTime);
    }

    public boolean shouldCancelBob() {
        if (!isEnabled() || mc.player == null) return false;
        if (onlyDamage.getValue() && mc.player.hurtTime <= 0) return false;
        return reduction.getValue() >= 100.0;
    }

    public boolean shouldClearHurtTime() {
        return isEnabled() && clearHurtTime.getValue();
    }

    public float getBobMultiplier() {
        if (!isEnabled()) return 1.0f;
        return (float) (1.0 - reduction.getValue() / 100.0);
    }

    public static boolean shouldDisable() {
        NoHurtCam mod = (NoHurtCam) ModuleManager.getModuleByName("NoHurtCam");
        return mod != null && mod.shouldCancelBob();
    }
}
