package nv.navineclient.util;

import nv.navineclient.NavineClient;

public final class NavineTheme {
    public static final int BLACK = 0xFF050510;
    public static final int SCREEN_BG = 0xFF080812;
    public static final int CLICK_SCREEN_DIM = 0xA0080812;
    public static final int PANEL_BG = 0xFF0C0C18;
    public static final int HEADER_BG = 0xFF14142E;
    public static final int ROYAL_BLUE = 0xFF4169E1;
    public static final int ROYAL_BLUE_DIM = 0xFF2A4494;
    public static final int ELECTRIC_BLUE = 0xFF00BFFF;
    public static final int PURPLE = 0xFF7B2CBF;
    public static final int PURPLE_BRIGHT = 0xFF9D4EDD;
    public static final int ACCENT = 0xFF4169E1;
    public static final int ACCENT2 = 0xFF9D4EDD;
    public static final int BORDER = 0xFF5A3D8C;
    public static final int ENABLED_BG = 0xFF1A1A42;
    public static final int HOVER = 0xFF22224A;
    public static final int SCROLLBAR_BG = 0xFF101024;
    public static final int SCROLLBAR_COLOR = 0xFF4169E1;
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFAAAACC;
    public static final int TEXT_DISABLED = 0xFF777799;
    public static final int SETTINGS_BG = 0xFF0E0E20;
    public static final int OVERLAY_DIM = 0xCC050510;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA_DIM = 0xCCFF00FF;
    public static final int CYAN_DIM = 0xCC00FFFF;
    public static final int TITLE_BAR_BG = 0x30000000;
    public static final int TITLE_BAR_LINE = 0x40FFFFFF;
    public static final int CLICK_TAB_BG = 0xFF8B2FC9;
    public static final int CLICK_TAB_ACTIVE = 0xFFB040E0;
    public static final int CLICK_TAB_HOVER = 0xFF9D4EDD;
    public static final int CLICK_TAB_TEXT = 0xFFFFFFFF;
    public static final int CLICK_MODULE_BG = 0xFF0C0C18;
    public static final int CLICK_MODULE_BORDER = 0xFF5A3D8C;

    private static final int GHOST_SCREEN_DIM = 0x99000000;
    private static final int GHOST_TAB_BG = 0xFF141418;
    private static final int GHOST_TAB_ACTIVE = 0xFF1C1C22;
    private static final int GHOST_TAB_HOVER = 0xFF222228;
    private static final int GHOST_ACCENT = 0xFF5A7ACC;
    private static final int GHOST_ACCENT2 = 0xFF7A9AD4;
    private static final int GHOST_BORDER = 0xFF2A2A30;
    private static final int GHOST_PANEL_BG = 0xFF121216;
    private static final int GHOST_ENABLED_BG = 0xFF18181E;
    private static final int GHOST_HOVER = 0xFF1E1E24;
    private static final int GHOST_SETTINGS_BG = 0xFF101014;
    private static final int GHOST_OVERLAY_DIM = 0x66000000;
    private static final int GHOST_SCROLLBAR = 0xFF5A7ACC;
    private static final int GHOST_HEADER_BG = 0xFF0E0E12;

    private NavineTheme() {
    }

    public static int headerBg() {
        return NavineClient.isGhost() ? GHOST_HEADER_BG : HEADER_BG;
    }

    public static int titleAccent() {
        return NavineClient.isGhost() ? GHOST_ACCENT : MAGENTA;
    }

    public static int titleSecondary() {
        return NavineClient.isGhost() ? GHOST_ACCENT2 : CYAN;
    }

    public static int screenDim() {
        return NavineClient.isGhost() ? GHOST_SCREEN_DIM : CLICK_SCREEN_DIM;
    }

    public static int panelBg() {
        return NavineClient.isGhost() ? GHOST_PANEL_BG : CLICK_MODULE_BG;
    }

    public static int settingsBg() {
        return NavineClient.isGhost() ? GHOST_SETTINGS_BG : SETTINGS_BG;
    }

    public static int overlayDim() {
        return NavineClient.isGhost() ? GHOST_OVERLAY_DIM : OVERLAY_DIM;
    }

    public static int accent() {
        return NavineClient.isGhost() ? GHOST_ACCENT : ACCENT;
    }

    public static int accent2() {
        return NavineClient.isGhost() ? GHOST_ACCENT2 : ACCENT2;
    }

    public static int border() {
        return NavineClient.isGhost() ? GHOST_BORDER : BORDER;
    }

    public static int enabledBg() {
        return NavineClient.isGhost() ? GHOST_ENABLED_BG : ENABLED_BG;
    }

    public static int hover() {
        return NavineClient.isGhost() ? GHOST_HOVER : HOVER;
    }

    public static int scrollbarBg() {
        return NavineClient.isGhost() ? GHOST_PANEL_BG : SCROLLBAR_BG;
    }

    public static int scrollbarColor() {
        return NavineClient.isGhost() ? GHOST_SCROLLBAR : SCROLLBAR_COLOR;
    }

    public static int tabBg() {
        return NavineClient.isGhost() ? GHOST_TAB_BG : CLICK_TAB_BG;
    }

    public static int tabActive() {
        return NavineClient.isGhost() ? GHOST_TAB_ACTIVE : CLICK_TAB_ACTIVE;
    }

    public static int tabHover() {
        return NavineClient.isGhost() ? GHOST_TAB_HOVER : CLICK_TAB_HOVER;
    }

    public static int moduleBorder() {
        return NavineClient.isGhost() ? GHOST_BORDER : CLICK_MODULE_BORDER;
    }

    public static int trackBg() {
        return NavineClient.isGhost() ? 0xFF1A1A1E : 0xFF2A2A44;
    }

    public static int searchHighlight() {
        return NavineClient.isGhost() ? 0x405A7ACC : 0x60B48EFF;
    }

    public static int dragHighlight() {
        return NavineClient.isGhost() ? 0x405A7ACC : 0x404169E1;
    }

    public static int mutedText() {
        return NavineClient.isGhost() ? 0xFF6A6A72 : 0xFF888888;
    }

    public static int controlBg() {
        return NavineClient.isGhost() ? 0xFF24242A : 0xFF444455;
    }

    public static int knobOff() {
        return NavineClient.isGhost() ? 0xFF7A7A82 : 0xFF888899;
    }

    public static int tabTextActive() {
        return NavineClient.isGhost() ? GHOST_ACCENT2 : CLICK_TAB_TEXT;
    }
}
