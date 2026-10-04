package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class FastPlace extends Module {
    private final NumberSetting delay = new NumberSetting("Delay", "Place delay (ticks)", 0.0, 0.0, 5.0);
    private final NumberSetting maxCps = new NumberSetting("MaxCPS", "Max places per second", 20.0, 1.0, 40.0);
    private final BooleanSetting blocksOnly = new BooleanSetting("BlocksOnly", "Only place blocks", true);

    private int placeTicks = 0;
    private long lastPlaceTime = 0;
    private int placesThisSecond = 0;
    private long secondStart = 0;

    public FastPlace() {
        super("FastPlace", "Places blocks faster", Category.PLAYER);
        addSetting(delay);
        addSetting(maxCps);
        addSetting(blocksOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        placeTicks++;

        long now = System.currentTimeMillis();
        if (now - secondStart >= 1000) {
            secondStart = now;
            placesThisSecond = 0;
        }
    }

    public boolean canPlace() {
        if (blocksOnly.getValue() && mc.player != null) {
            if (!(mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem)) {
                return false;
            }
        }
        if (placesThisSecond >= maxCps.getValue().intValue()) {
            return false;
        }
        return placeTicks >= delay.getValue().intValue();
    }

    public void onPlace() {
        placeTicks = 0;
        placesThisSecond++;
        lastPlaceTime = System.currentTimeMillis();
    }
}
