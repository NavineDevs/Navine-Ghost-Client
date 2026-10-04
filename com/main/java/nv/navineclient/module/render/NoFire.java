package nv.navineclient.module.render;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;

public class NoFire extends Module {
    public static NoFire INSTANCE;

    private final BooleanSetting firstPerson = new BooleanSetting("FirstPerson", "Hide only in first person", true);
    private final BooleanSetting lava = new BooleanSetting("Lava", "Also hide lava overlay", false);

    public NoFire() {
        super("NoFire", "Remove fire visual effect when burnt", Category.RENDER);
        INSTANCE = this;
        addSetting(firstPerson);
        addSetting(lava);
    }

    public boolean appliesInCurrentView() {
        if (!isEnabled()) {
            return false;
        }
        if (firstPerson.getValue() && mc.options != null && !mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        return true;
    }

    public boolean shouldHideFireOverlay() {
        return appliesInCurrentView();
    }

    public boolean hideLava() {
        return appliesInCurrentView() && lava.getValue();
    }

    public static boolean shouldHideFire() {
        NoFire noFire = (NoFire) ModuleManager.getModuleByName("NoFire");
        return noFire != null && noFire.shouldHideFireOverlay();
    }
}
