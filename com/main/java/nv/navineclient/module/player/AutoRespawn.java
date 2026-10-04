package nv.navineclient.module.player;

import nv.navineclient.util.ClientScreens;
import net.minecraft.client.gui.screens.DeathScreen;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.ChatUtils;

public class AutoRespawn extends Module {
    private final NumberSetting delay = new NumberSetting("Delay", "Respawn delay (ticks)", 5.0, 0.0, 40.0);
    private final BooleanSetting logCoords = new BooleanSetting("LogCoords", "Log death coordinates", true);
    private final BooleanSetting keepScreen = new BooleanSetting("KeepScreen", "Keep death screen visible", false);

    private int ticks = 0;

    public AutoRespawn() {
        super("AutoRespawn", "Automatically respawns when dead", Category.PLAYER);
        addSetting(delay);
        addSetting(logCoords);
        addSetting(keepScreen);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (ClientScreens.get(mc) instanceof DeathScreen) {
            if (ticks == 0 && logCoords.getValue()) {
                ChatUtils.message("Died at " + mc.player.blockPosition().toShortString());
            }
            ticks++;
            if (ticks >= delay.getValue()) {
                mc.player.respawn();
                if (!keepScreen.getValue()) {
                    ClientScreens.set(mc, null);
                }
                ticks = 0;
            }
        } else {
            ticks = 0;
        }
    }
}
