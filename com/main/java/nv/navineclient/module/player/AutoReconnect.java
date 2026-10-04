package nv.navineclient.module.player;

import nv.navineclient.util.ClientScreens;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AutoReconnect extends Module {
    private final NumberSetting delay = new NumberSetting("Delay", "Reconnect delay (seconds)", 5.0, 1.0, 30.0);
    private final NumberSetting maxAttempts = new NumberSetting("MaxAttempts", "Max reconnect attempts", 3.0, 1.0, 10.0);
    private final BooleanSetting onlyMultiplayer = new BooleanSetting("Multiplayer", "Only for multiplayer", true);

    private int ticksWaited = 0;
    private int attempts = 0;
    private ServerData lastServer = null;

    public AutoReconnect() {
        super("AutoReconnect", "Automatically reconnect when disconnected", Category.PLAYER);
        addSetting(delay);
        addSetting(maxAttempts);
        addSetting(onlyMultiplayer);
    }

    @Override
    public void onTick() {
        if (mc == null) return;

        if (ClientScreens.get(mc) instanceof DisconnectedScreen) {
            if (onlyMultiplayer.getValue() && lastServer == null) return;
            if (attempts >= maxAttempts.getValue().intValue()) return;

            ticksWaited++;
            if (ticksWaited >= delay.getValue() * 20) {
                ticksWaited = 0;
                if (lastServer != null) {
                    attempts++;
                    ConnectScreen.startConnecting(ClientScreens.get(mc), mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
                }
            }
        } else {
            ticksWaited = 0;
            if (mc.getCurrentServer() != null) {
                lastServer = mc.getCurrentServer();
                attempts = 0;
            }
        }
    }
}
