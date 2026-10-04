package nv.navineclient.module.render;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.util.NavineInput;

public class Zoom extends Module {
    public static Zoom INSTANCE;

    private final NumberSetting zoomFactor = new NumberSetting("Factor", "Zoom multiplier", 4.0, 1.5, 15.0);
    private final BooleanSetting smoothZoom = new BooleanSetting("SmoothZoom", "Smooth zoom transitions", true);
    private final NumberSetting minFov = new NumberSetting("MinFov", "Minimum FOV", 10.0, 5.0, 30.0);
    private final BooleanSetting holdKey = new BooleanSetting("HoldKey", "Zoom only while key held", true);
    private float smoothProgress;
    private boolean keyHeld;

    public Zoom() {
        super("Zoom", "Zoom in with a key", Category.RENDER);
        INSTANCE = this;
        setKey(NavineInput.KEY_C);
        addSetting(zoomFactor);
        addSetting(smoothZoom);
        addSetting(minFov);
        addSetting(holdKey);
    }

    @Override
    public void onEnable() {
        if (!smoothZoom.getValue()) {
            smoothProgress = 1.0f;
        }
    }

    public void setKeyHeld(boolean held) {
        keyHeld = held;
    }

    public boolean useHoldKey() {
        return holdKey.getValue();
    }

    public boolean isZooming() {
        if (!isEnabled()) {
            return false;
        }
        if (holdKey.getValue()) {
            return keyHeld;
        }
        return true;
    }

    public boolean needsTick() {
        return isEnabled() || smoothProgress > 0.001f;
    }

    @Override
    public void onTick() {
        float target = isZooming() ? 1.0f : 0.0f;
        if (smoothZoom.getValue()) {
            smoothProgress += (target - smoothProgress) * 0.25f;
            if (smoothProgress < 0.01f) {
                smoothProgress = 0.0f;
            }
        } else {
            smoothProgress = target;
        }
    }

    public float getZoomMultiplier() {
        if (smoothProgress <= 0.001f) {
            return 1.0f;
        }
        return (float) (1.0 + (zoomFactor.getValue() - 1.0) * smoothProgress);
    }

    public double getZoomFactor() {
        return zoomFactor.getValue();
    }

    public double getMinFov() {
        return minFov.getValue();
    }

    public static boolean isZoom() {
        Module module = ModuleManager.getModuleByName("Zoom");
        return module != null && module.isEnabled() && module instanceof Zoom zoom && zoom.isZooming();
    }
}
