package nv.navineclient.module.render;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;

public class FullBright extends Module {
    public static FullBright INSTANCE;

    private final NumberSetting gamma = new NumberSetting("Gamma", "Brightness level", 1.0, 0.5, 1.0);
    private final ModeSetting mode = new ModeSetting("Mode", "Brightness method", "Gamma", "Gamma", "NightVision");
    private final BooleanSetting smooth = new BooleanSetting("Smooth", "Smooth transition on toggle", true);

    private double originalGamma = 0.5;
    private boolean hasStoredGamma = false;
    private double currentGamma = 0.5;

    public FullBright() {
        super("FullBright", "Makes everything bright", Category.RENDER);
        INSTANCE = this;
        addSetting(gamma);
        addSetting(mode);
        addSetting(smooth);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.options == null) return;

        if (!hasStoredGamma) {
            originalGamma = mc.options.gamma().get();
            hasStoredGamma = true;
        }
        currentGamma = mc.options.gamma().get();

        if (mode.getValue().equals("NightVision")) {
            mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 999999, 0, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null && hasStoredGamma) {
            mc.options.gamma().set(originalGamma);
        }
        if (mc.player != null) {
            mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        }
    }

    @Override
    public void onTick() {
        refreshClient();
        if (mc.options == null || !isEnabled()) return;

        if (mode.getValue().equals("NightVision")) {
            if (mc.player != null && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION, 999999, 0, false, false));
            }
            return;
        }

        double target = gamma.getValue();
        if (smooth.getValue()) {
            currentGamma += (target - currentGamma) * 0.25;
            mc.options.gamma().set(currentGamma);
        } else {
            mc.options.gamma().set(target);
        }
    }

    public static boolean isFullBright() {
        Module module = ModuleManager.getModuleByName("FullBright");
        return module != null && module.isEnabled();
    }
}
