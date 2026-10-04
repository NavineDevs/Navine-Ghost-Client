package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientScreens {
    private ClientScreens() {
    }

    public static Screen get(Minecraft client) {
        if (client == null || client.gui == null) {
            return null;
        }
        return client.gui.screen();
    }

    public static void set(Minecraft client, Screen screen) {
        if (client == null) {
            return;
        }
        try {
            if (client.gui != null) {
                client.gui.setScreen(screen);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            client.setScreenAndShow(screen);
        } catch (Throwable ignored) {
        }
    }
}
