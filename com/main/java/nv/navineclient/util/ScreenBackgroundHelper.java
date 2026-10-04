package nv.navineclient.util;

import nv.navineclient.util.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import nv.navineclient.NavineClient;
import nv.navineclient.ui.BigRadarScreen;
import nv.navineclient.ui.ClickGUI;
import nv.navineclient.ui.CoolButton;

public final class ScreenBackgroundHelper {

    private ScreenBackgroundHelper() {
    }

    public static boolean shouldUseNavineBackground(Screen screen, Minecraft minecraft) {
        if (screen instanceof ClickGUI || screen instanceof BigRadarScreen) {
            return false;
        }
        if (screen instanceof AbstractContainerScreen) {
            return false;
        }
        if (screen instanceof ChatScreen) {
            return minecraft.level == null;
        }
        if (minecraft.level != null) {
            return screen instanceof DisconnectedScreen
                    || screen instanceof GenericMessageScreen
                    || screen instanceof DeathScreen;
        }
        return true;
    }

    public static void loadBackground() {
        if (NavineClient.isGhost()) {
            return;
        }
        BackgroundManager.loadExternalBackground();
    }

    public static void renderNavineBackground(GuiGraphicsExtractor context, int width, int height) {
        if (NavineClient.isGhost()) {
            if (context != null) {
                context.fillGradient(0, 0, width, height, 0xFF101216, 0xFF181B22);
            }
            return;
        }
        BackgroundManager.loadExternalBackground();
        if (context == null) {
            return;
        }
        if (BackgroundManager.hasBackground()) {
            BackgroundManager.renderBackground(context, width, height);
        } else {
            context.fillGradient(0, 0, width, height, 0xFF0A0A0F, 0xFF12122A);
        }
    }

    public static void renderMenuBackground(GuiGraphicsExtractor context, int width, int height) {
        if (NavineClient.isGhost()) {
            if (context != null) {
                context.fillGradient(0, 0, width, height, 0xFF0A0A0C, 0xFF121418);
            }
            return;
        }
        BackgroundManager.loadExternalBackground();
        if (context == null) {
            return;
        }
        if (BackgroundManager.hasBackground()) {
            BackgroundManager.renderBackground(context, width, height);
        } else {
            context.fillGradient(0, 0, width, height, 0xFF030306, 0xFF050508);
        }
    }

    public static void renderTopBar(GuiGraphicsExtractor context, int width) {
        if (context == null) {
            return;
        }
        int barBg = NavineClient.isGhost() ? 0x90101216 : 0x90080812;
        context.fill(0, 0, width, 28, barBg);
        context.fill(0, 28, width, 29, NavineTheme.border());
    }

    public static CoolButton createTopBackButton(Screen parent) {
        return new CoolButton(8, 6, 72, 18, Component.literal("Back"), button -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                ClientScreens.set(mc, parent);
            }
        }, true);
    }

    public static boolean isMenuMusicScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level != null) {
            return false;
        }
        if (screen instanceof ClickGUI || screen instanceof BigRadarScreen) {
            return false;
        }
        return true;
    }

    public static boolean shouldPlayMenuMusic() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level == null && ClientScreens.get(mc) != null && isMenuMusicScreen(ClientScreens.get(mc));
    }

    public static boolean shouldStyleNavineButtons(Screen screen, Minecraft minecraft) {
        if (screen == null || minecraft == null) {
            return false;
        }
        if (screen instanceof ClickGUI || screen instanceof BigRadarScreen) {
            return false;
        }
        if (screen instanceof AbstractContainerScreen || screen instanceof ChatScreen) {
            return false;
        }
        return true;
    }
}
