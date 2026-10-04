package nv.navineclient.module.render;

import net.minecraft.world.effect.MobEffects;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;

public class NoRender extends Module {
    public static NoRender INSTANCE;

    private final BooleanSetting noFire = new BooleanSetting("Fire", "Hide fire overlay", true);
    private final BooleanSetting noWater = new BooleanSetting("Water", "Hide water overlay", true);
    private final BooleanSetting noBlindness = new BooleanSetting("Blindness", "Remove blindness", true);
    private final BooleanSetting noNausea = new BooleanSetting("Nausea", "Remove nausea", true);
    private final BooleanSetting noPumpkin = new BooleanSetting("Pumpkin", "Hide pumpkin overlay", true);
    private final BooleanSetting noBossBar = new BooleanSetting("BossBar", "Hide boss bar", false);
    private final BooleanSetting noFog = new BooleanSetting("Fog", "Remove fog", true);

    public NoRender() {
        super("NoRender", "Hides various visual effects", Category.RENDER);
        INSTANCE = this;
        addSetting(noFire);
        addSetting(noWater);
        addSetting(noBlindness);
        addSetting(noNausea);
        addSetting(noPumpkin);
        addSetting(noBossBar);
        addSetting(noFog);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (removesBlindness() && mc.player.hasEffect(MobEffects.BLINDNESS)) {
            mc.player.removeEffect(MobEffects.BLINDNESS);
        }
        if (removesNausea() && mc.player.hasEffect(MobEffects.NAUSEA)) {
            mc.player.removeEffect(MobEffects.NAUSEA);
        }
    }

    public boolean hidesFire() { return noFire.getValue(); }
    public boolean hidesWater() { return noWater.getValue(); }
    public boolean removesBlindness() { return noBlindness.getValue(); }
    public boolean removesNausea() { return noNausea.getValue(); }
    public boolean hidesPumpkin() { return noPumpkin.getValue(); }
    public boolean hidesBossBar() { return noBossBar.getValue(); }
    public boolean removesFog() { return noFog.getValue(); }

    public static boolean isNoRender() {
        Module module = ModuleManager.getModuleByName("NoRender");
        return module != null && module.isEnabled();
    }
}
