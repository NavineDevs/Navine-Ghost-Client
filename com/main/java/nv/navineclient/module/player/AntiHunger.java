package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AntiHunger extends Module {
    public static AntiHunger INSTANCE;

    private final NumberSetting sprintInterval = new NumberSetting("Interval", "Ticks before stopping sprint", 8.0, 3.0, 40.0);
    private final BooleanSetting onlyHungry = new BooleanSetting("OnlyHungry", "Only when food is low", true);
    private final NumberSetting foodThreshold = new NumberSetting("Food", "Food level threshold", 18.0, 1.0, 20.0);

    private int sprintTicks = 0;

    public AntiHunger() {
        super("AntiHunger", "Reduces hunger drain", Category.PLAYER);
        INSTANCE = this;
        addSetting(sprintInterval);
        addSetting(onlyHungry);
        addSetting(foodThreshold);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (onlyHungry.getValue() && mc.player.getFoodData().getFoodLevel() >= foodThreshold.getValue().intValue()) {
            sprintTicks = 0;
            return;
        }

        if (!mc.player.isSprinting() || !mc.player.onGround()) {
            sprintTicks = 0;
            return;
        }

        sprintTicks++;
        if (sprintTicks >= sprintInterval.getValue().intValue()) {
            mc.player.setSprinting(false);
            sprintTicks = 0;
        }
    }

    public static boolean isAntiHunger() {
        Module module = ModuleManager.getModuleByName("AntiHunger");
        return module != null && module.isEnabled();
    }
}
