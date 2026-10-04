package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class SpeedMine extends Module {
    public static SpeedMine INSTANCE;
    private final NumberSetting multiplier = new NumberSetting("Speed", "Break speed multiplier", 1.5, 1.0, 5.0);
    private final BooleanSetting onlyOres = new BooleanSetting("OresOnly", "Only speed-mine ores", false);
    private final BooleanSetting hasteCheck = new BooleanSetting("HasteCheck", "Reduce when haste active", true);

    public SpeedMine() {
        super("SpeedMine", "Breaks blocks faster", Category.PLAYER);
        INSTANCE = this;
        addSetting(multiplier);
        addSetting(onlyOres);
        addSetting(hasteCheck);
    }

    public float getMultiplier(net.minecraft.world.level.block.state.BlockState state) {
        if (!isEnabled()) return 1.0f;
        if (onlyOres.getValue() && state != null) {
            String name = state.getBlock().getDescriptionId().toLowerCase();
            if (!name.contains("ore") && !name.contains("ancient_debris")) {
                return 1.0f;
            }
        }
        float mult = multiplier.getValue().floatValue();
        if (hasteCheck.getValue() && mc.player != null && mc.player.hasEffect(net.minecraft.world.effect.MobEffects.HASTE)) {
            mult = Math.max(1.0f, mult * 0.5f);
        }
        return mult;
    }

    public float getMultiplier() {
        return getMultiplier(null);
    }

    public static float getBreakSpeedMultiplier() {
        Module module = ModuleManager.getModuleByName("SpeedMine");
        if (module != null && module.isEnabled() && module instanceof SpeedMine sm) {
            return sm.getMultiplier();
        }
        return 1.0f;
    }
}
