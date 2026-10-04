package nv.navineclient.module.world;

import net.minecraft.client.Minecraft;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Timer extends Module {
    public static Timer INSTANCE;
    private final NumberSetting speedSetting = new NumberSetting("Speed", "Game speed multiplier", 2.0, 0.1, 10.0);
    private final BooleanSetting onlyMoving = new BooleanSetting("OnlyMoving", "Only speed up while moving", false);
    private final BooleanSetting onlySprinting = new BooleanSetting("OnlySprint", "Only while sprinting", false);

    public Timer() {
        super("Timer", "Changes the game speed client-side", Category.WORLD);
        INSTANCE = this;
        addSetting(speedSetting);
        addSetting(onlyMoving);
        addSetting(onlySprinting);
    }

    public float getTimerSpeed() {
        return speedSetting.getValue().floatValue();
    }

    public static float getMultiplier() {
        Module module = ModuleManager.getModuleByName("Timer");
        if (module == null || !module.isEnabled() || !(module instanceof Timer timer)) {
            return 1.0f;
        }
        if (timer.onlyMoving.getValue()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return 1.0f;
            boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
            if (!moving) return 1.0f;
        }
        if (timer.onlySprinting.getValue()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.player.isSprinting()) {
                return 1.0f;
            }
        }
        return AcBypassUtil.clampTimer(timer.getTimerSpeed());
    }
}
