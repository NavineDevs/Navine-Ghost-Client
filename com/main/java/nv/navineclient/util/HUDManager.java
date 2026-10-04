package nv.navineclient.util;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.world.level.GameType;

public final class HUDManager {
    public static final int HUD_MARGIN = 5;
    public static final int LINE_HEIGHT = 12;
    public static final int PANEL_PADDING = 5;
    private static final int HOTBAR_RESERVE = 45;

    private HUDManager() {
    }

    public static boolean shouldHideOverlays(Minecraft client) {
        if (client == null) {
            return true;
        }
        if (ClientScreens.get(client) instanceof ChatScreen || ClientScreens.get(client) instanceof InBedChatScreen) {
            return true;
        }
        return client.gui != null
            && ClientAccess.getChat(client) != null
            && ClientAccess.getChat(client).isChatFocused();
    }

    public static int getHotbarInset() {
        return HOTBAR_RESERVE;
    }

    public static int getHotbarTopY(int guiHeight) {
        return guiHeight - 22;
    }

    public static int getNotificationYAboveHotbar(Minecraft client, int guiHeight, int panelHeight) {
        int hotbarTop = getHotbarTopY(guiHeight);
        int offset = 4;
        if (client != null && client.gameMode != null) {
            GameType mode = client.gameMode.getPlayerMode();
            if (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) {
                offset += 39;
            } else if (mode == GameType.SPECTATOR) {
                offset += 14;
            }
        }
        return hotbarTop - panelHeight - offset;
    }

    public static int getNotificationYAboveHotbar(int guiHeight, int panelHeight) {
        return getNotificationYAboveHotbar(Minecraft.getInstance(), guiHeight, panelHeight);
    }

    public static int getBottomInset(Minecraft client) {
        int inset = HOTBAR_RESERVE;
        if (client == null || client.gui == null || ClientAccess.getChat(client) == null) {
            return inset;
        }
        if (ClientAccess.getChat(client).isChatFocused()) {
            return inset;
        }
        int lines = Math.min(ClientAccess.getChat(client).getLinesPerPage(), 6);
        return inset + lines * 9;
    }

    public static int scaleRadarSize(int configuredSize, int guiWidth, int guiHeight) {
        int maxSize = Math.min(guiWidth, guiHeight) / 3;
        maxSize = Math.max(maxSize, 64);
        return Math.min(configuredSize, maxSize);
    }

    public static int resolveTopLeftHudY(Minecraft client, int guiWidth, int guiHeight, int panelHeight) {
        int y = HUD_MARGIN;
        try {
            nv.navineclient.module.render.Radar radarModule =
                    (nv.navineclient.module.render.Radar) nv.navineclient.module.ModuleManager.getModuleByName("Radar");
            if (radarModule != null && radarModule.isEnabled()) {
                y += scaleRadarSize((int) radarModule.getSize(), guiWidth, guiHeight) + HUD_MARGIN;
            }
        } catch (Exception ignored) {
        }
        return y;
    }

    public static int resolveDevTimingsY(Minecraft client, int guiWidth, int guiHeight, int hudInfoHeight) {
        int y = HUD_MARGIN;
        try {
            nv.navineclient.module.render.Radar radarModule =
                    (nv.navineclient.module.render.Radar) nv.navineclient.module.ModuleManager.getModuleByName("Radar");
            if (radarModule != null && radarModule.isEnabled()) {
                y += scaleRadarSize((int) radarModule.getSize(), guiWidth, guiHeight) + HUD_MARGIN;
            }
        } catch (Exception ignored) {
        }
        if (hudInfoHeight > 0 && nv.navineclient.config.ConfigManager.isHudTopLeft()) {
            y += hudInfoHeight + HUD_MARGIN;
        }
        return y;
    }

    public static int[] resolveTargetHudPosition(Minecraft client, int guiWidth, int guiHeight,
            int configuredX, int configuredY, int panelWidth, int panelHeight,
            int radarSize, boolean autoPosition) {
        int x;
        int y;
        if (autoPosition) {
            x = guiWidth - panelWidth - HUD_MARGIN;
            y = HUD_MARGIN;
        } else {
            x = configuredX;
            y = configuredY;
        }
        x = clamp(x, HUD_MARGIN, Math.max(HUD_MARGIN, guiWidth - panelWidth - HUD_MARGIN));
        int maxY = guiHeight - panelHeight - getBottomInset(client);
        y = clamp(y, HUD_MARGIN, Math.max(HUD_MARGIN, maxY));

        int radarRight = HUD_MARGIN + radarSize + HUD_MARGIN;
        int radarBottom = HUD_MARGIN + radarSize + HUD_MARGIN;
        if (x < radarRight && y < radarBottom) {
            int topRightX = guiWidth - panelWidth - HUD_MARGIN;
            if (topRightX >= radarRight) {
                x = topRightX;
                y = HUD_MARGIN;
            } else {
                y = radarBottom + HUD_MARGIN;
            }
        }
        return new int[]{x, y};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
