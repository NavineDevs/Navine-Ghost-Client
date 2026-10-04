package nv.navineclient.ui;

import nv.navineclient.util.ClientScreens;
import nv.navineclient.NavineClient;
import nv.navineclient.config.ConfigManager;
import nv.navineclient.util.NavineTheme;
import nv.navineclient.util.RenderUtil;
import nv.navineclient.util.ScreenBackgroundHelper;
import nv.navineclient.util.UiAnim;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import nv.navineclient.util.NavineInput;

public class NavineSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int PANEL_WIDTH = 360;
    private static final int VISIBLE_ROWS = 7;
    private static final int SLIDER_WIDTH = 140;
    private static final int TOGGLE_WIDTH = 80;
    private static final int TOGGLE_HEIGHT = 16;
    private static final int TOTAL_ROWS = 8;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private int scrollOffset = 0;
    private boolean leftMouseDown = false;
    private boolean draggingSound = false;
    private boolean draggingMusic = false;
    private boolean listeningKey = false;
    private float screenAnim = 0f;
    private float uiDelta = 0f;
    private final java.util.Map<Integer, Float> toggleHoverAnim = new java.util.HashMap<>();

    public NavineSettingsScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    @Override
    protected void init() {
        screenAnim = 0f;
        toggleHoverAnim.clear();
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = 40;
        panelHeight = ROW_HEIGHT * VISIBLE_ROWS + 16;

        addRenderableWidget(ScreenBackgroundHelper.createTopBackButton(parent));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int maxScroll = Math.max(0, TOTAL_ROWS * ROW_HEIGHT - (VISIBLE_ROWS * ROW_HEIGHT));
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * ROW_HEIGHT)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningKey) {
            int keyCode = event.key();
            if (keyCode == NavineInput.KEY_ESCAPE) {
                listeningKey = false;
            } else {
                NavineClient.setClickGuiKey(keyCode, true);
                listeningKey = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long window = this.minecraft.getWindow().handle();
        boolean leftNow = NavineInput.isMouseDown(NavineInput.MOUSE_BUTTON_LEFT);

        if (leftNow && !leftMouseDown) {
            handleClick(mouseX, mouseY);
        }
        if (!leftNow) {
            draggingSound = false;
            draggingMusic = false;
        }
        if (leftNow && draggingSound) {
            updateVolumeSlider(mouseX, SoundSource.MASTER);
        }
        if (leftNow && draggingMusic) {
            updateVolumeSlider(mouseX, SoundSource.MUSIC);
        }
        leftMouseDown = leftNow;
        uiDelta = delta;

        screenAnim = UiAnim.approach(screenAnim, 1f, 8f, delta);
        float ease = UiAnim.easeOutCubic(screenAnim);
        int panelDrawY = panelY + (int) ((1f - ease) * 14f);

        ScreenBackgroundHelper.renderNavineBackground(context, this.width, this.height);
        ScreenBackgroundHelper.renderTopBar(context, this.width);

        String title = "Navine Settings";
        int titleW = this.font.width(title);
        context.text(this.font, title, (this.width - titleW) / 2, 8, UiAnim.scaleAlpha(NavineTheme.ROYAL_BLUE, ease), true);

        RenderUtil.drawRoundedRect(context, panelX, panelDrawY, PANEL_WIDTH, panelHeight, 4, UiAnim.scaleAlpha(NavineTheme.PANEL_BG, ease));
        RenderUtil.drawRoundedOutline(context, panelX, panelDrawY, PANEL_WIDTH, panelHeight, 4, 1, UiAnim.scaleAlpha(NavineTheme.BORDER, ease));

        int contentTop = panelDrawY + 8;
        int contentBottom = panelDrawY + panelHeight - 8;
        int maxScroll = Math.max(0, TOTAL_ROWS * ROW_HEIGHT - (VISIBLE_ROWS * ROW_HEIGHT));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        context.enableScissor(panelX + 4, contentTop, panelX + PANEL_WIDTH - 4, contentBottom);
        int rowY = contentTop - scrollOffset;
        renderKeyRow(context, panelX + 12, rowY, mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderAutoLoginRow(context, panelX + 12, rowY, mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderToggleRow(context, panelX + 12, rowY, "HUD Top-Left", ConfigManager.isHudTopLeft(), mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderToggleRow(context, panelX + 12, rowY, "Notify Above Hotbar", ConfigManager.isNotificationCenterHotbar(), mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderModulesVisibleRow(context, panelX + 12, rowY, mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderVolumeRow(context, panelX + 12, rowY, "Sound", SoundSource.MASTER, mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderVolumeRow(context, panelX + 12, rowY, "Music", SoundSource.MUSIC, mouseX, mouseY);
        rowY += ROW_HEIGHT;
        renderAddonManagerRow(context, panelX + 12, rowY, mouseX, mouseY);
        context.disableScissor();

        int totalContentH = TOTAL_ROWS * ROW_HEIGHT;
        int visibleH = VISIBLE_ROWS * ROW_HEIGHT;
        if (totalContentH > visibleH) {
            int sbX = panelX + PANEL_WIDTH - 5;
            int sbY = contentTop;
            int sbH = contentBottom - contentTop;
            context.fill(sbX, sbY, sbX + 3, sbY + sbH, 0xFF1A1A2E);
            double pct = maxScroll > 0 ? (double) scrollOffset / maxScroll : 0;
            int thumbH = Math.max(12, (int) ((double) sbH / totalContentH * sbH));
            int thumbY = sbY + (int) (pct * (sbH - thumbH));
            context.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, NavineTheme.ACCENT);
        }

        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
    }

    private void renderKeyRow(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < panelY + 8 || y > panelY + panelHeight - 8) {
            return;
        }
        context.text(this.font, "ClickGUI Key", x, y + 4, NavineTheme.TEXT_PRIMARY, false);
        String keyName = listeningKey ? "..." : getKeyName(NavineClient.getClickGuiKey());
        int valueX = panelX + PANEL_WIDTH - 16 - this.font.width(keyName);
        boolean hover = mouseX >= valueX - 4 && mouseX <= valueX + this.font.width(keyName) + 4
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        int color = listeningKey ? 0xFFFF5555 : (hover ? NavineTheme.ACCENT2 : NavineTheme.ACCENT);
        context.text(this.font, keyName, valueX, y + 4, color, false);
    }

    private void renderAutoLoginRow(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < panelY + 8 || y > panelY + panelHeight - 8) {
            return;
        }
        context.text(this.font, "Auto-Login", x, y + 4, NavineTheme.TEXT_PRIMARY, false);
        int toggleX = panelX + PANEL_WIDTH - 16 - TOGGLE_WIDTH;
        renderToggle(context, toggleX, y + 2, ConfigManager.isAutoLoginEnabled(), mouseX, mouseY, 1);
    }

    private void renderToggleRow(GuiGraphicsExtractor context, int x, int y, String label, boolean enabled, int mouseX, int mouseY, int rowId) {
        if (y + ROW_HEIGHT < panelY + 8 || y > panelY + panelHeight - 8) {
            return;
        }
        context.text(this.font, label, x, y + 4, NavineTheme.TEXT_PRIMARY, false);
        int toggleX = panelX + PANEL_WIDTH - 16 - TOGGLE_WIDTH;
        renderToggle(context, toggleX, y + 2, enabled, mouseX, mouseY, rowId);
    }

    private void renderToggleRow(GuiGraphicsExtractor context, int x, int y, String label, boolean enabled, int mouseX, int mouseY) {
        renderToggleRow(context, x, y, label, enabled, mouseX, mouseY, y);
    }

    private void renderModulesVisibleRow(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < panelY + 8 || y > panelY + panelHeight - 8) {
            return;
        }
        context.text(this.font, "Modules Shown", x, y + 4, NavineTheme.TEXT_PRIMARY, false);
        String val = String.valueOf(ConfigManager.getModulesVisible());
        int valW = this.font.width(val);
        int valX = panelX + PANEL_WIDTH - 16 - valW;
        context.text(this.font, val, valX, y + 4, NavineTheme.ACCENT, false);
        int minusX = valX - 20;
        int plusX = valX - 10;
        boolean minusHover = mouseX >= minusX && mouseX <= minusX + 8 && mouseY >= y + 2 && mouseY <= y + 14;
        boolean plusHover = mouseX >= plusX && mouseX <= plusX + 8 && mouseY >= y + 2 && mouseY <= y + 14;
        context.text(this.font, "-", minusX, y + 4, minusHover ? NavineTheme.ACCENT2 : NavineTheme.TEXT_SECONDARY, false);
        context.text(this.font, "+", plusX, y + 4, plusHover ? NavineTheme.ACCENT2 : NavineTheme.TEXT_SECONDARY, false);
    }

    private void renderVolumeRow(GuiGraphicsExtractor context, int x, int y, String label, SoundSource source, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < panelY + 8 || y > panelY + panelHeight - 8) {
            return;
        }
        context.text(this.font, label, x, y + 4, NavineTheme.TEXT_PRIMARY, false);
        OptionInstance<Double> option = this.minecraft.options.getSoundSourceOptionInstance(source);
        double value = option.get();
        String valueText = Math.round(value * 100) + "%";
        int sliderX = panelX + PANEL_WIDTH - 16 - SLIDER_WIDTH;
        int sliderY = y + 10;
        int barH = 8;
        RenderUtil.drawRoundedRect(context, sliderX, sliderY, SLIDER_WIDTH, barH, 2f, 0xFF2A2A44);
        RenderUtil.drawRoundedRect(context, sliderX, sliderY, (int) (SLIDER_WIDTH * value), barH, 2f, NavineTheme.ACCENT);
        int valueW = this.font.width(valueText);
        context.text(this.font, valueText, sliderX - valueW - 8, y + 4, NavineTheme.TEXT_SECONDARY, false);
    }

    private void renderAddonManagerRow(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT < panelY + 8 || y > panelY + panelHeight - 8) {
            return;
        }
        context.text(this.font, "Addon Manager", x, y + 4, NavineTheme.TEXT_PRIMARY, false);
        String action = "Open >";
        int actionW = this.font.width(action);
        int actionX = panelX + PANEL_WIDTH - 16 - actionW;
        boolean hover = mouseX >= actionX - 4 && mouseX <= actionX + actionW + 4
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        int color = hover ? NavineTheme.ACCENT2 : NavineTheme.ACCENT;
        context.text(this.font, action, actionX, y + 4, color, false);
    }

    private void renderToggle(GuiGraphicsExtractor context, int x, int y, boolean enabled, int mouseX, int mouseY, int rowId) {
        boolean hover = mouseX >= x && mouseX <= x + TOGGLE_WIDTH && mouseY >= y && mouseY <= y + TOGGLE_HEIGHT;
        float hoverAnim = toggleHoverAnim.getOrDefault(rowId, 0f);
        hoverAnim = UiAnim.approach(hoverAnim, hover ? 1f : 0f, 14f, uiDelta);
        toggleHoverAnim.put(rowId, hoverAnim);
        int bg = UiAnim.lerpColor(NavineTheme.HEADER_BG, NavineTheme.ENABLED_BG, hoverAnim);
        context.fill(x, y, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT, bg);
        context.fill(x, y, x + TOGGLE_WIDTH, y + 1, UiAnim.lerpColor(NavineTheme.ROYAL_BLUE, NavineTheme.PURPLE_BRIGHT, hoverAnim));
        context.fill(x, y + TOGGLE_HEIGHT - 1, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT, UiAnim.lerpColor(NavineTheme.PURPLE, NavineTheme.ROYAL_BLUE, hoverAnim));
        String label = enabled ? "On" : "Off";
        int labelW = this.font.width(label);
        int color = enabled ? NavineTheme.TEXT_PRIMARY : NavineTheme.TEXT_DISABLED;
        context.text(this.font, label, x + (TOGGLE_WIDTH - labelW) / 2, y + 3, color, false);
    }

    private void handleClick(int mouseX, int mouseY) {
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < panelY || mouseY > panelY + panelHeight) {
            return;
        }
        int contentTop = panelY + 8;
        int row = (mouseY - contentTop + scrollOffset) / ROW_HEIGHT;
        int rowY = contentTop + row * ROW_HEIGHT - scrollOffset;
        int toggleX = panelX + PANEL_WIDTH - 16 - TOGGLE_WIDTH;

        if (row == 0) {
            listeningKey = !listeningKey;
            return;
        }
        if (row == 1 && mouseX >= toggleX && mouseX <= toggleX + TOGGLE_WIDTH
                && mouseY >= rowY + 2 && mouseY <= rowY + 2 + TOGGLE_HEIGHT) {
            ConfigManager.setAutoLoginEnabled(!ConfigManager.isAutoLoginEnabled());
            return;
        }
        if (row == 2 && mouseX >= toggleX && mouseX <= toggleX + TOGGLE_WIDTH
                && mouseY >= rowY + 2 && mouseY <= rowY + 2 + TOGGLE_HEIGHT) {
            ConfigManager.setHudTopLeft(!ConfigManager.isHudTopLeft());
            return;
        }
        if (row == 3 && mouseX >= toggleX && mouseX <= toggleX + TOGGLE_WIDTH
                && mouseY >= rowY + 2 && mouseY <= rowY + 2 + TOGGLE_HEIGHT) {
            ConfigManager.setNotificationCenterHotbar(!ConfigManager.isNotificationCenterHotbar());
            return;
        }
        if (row == 4) {
            int valX = panelX + PANEL_WIDTH - 16 - this.font.width(String.valueOf(ConfigManager.getModulesVisible()));
            int minusX = valX - 20;
            int plusX = valX - 10;
            if (mouseX >= minusX && mouseX <= minusX + 8) {
                ConfigManager.setModulesVisible(ConfigManager.getModulesVisible() - 1);
            } else if (mouseX >= plusX && mouseX <= plusX + 8) {
                ConfigManager.setModulesVisible(ConfigManager.getModulesVisible() + 1);
            }
            return;
        }
        if (row == 5) {
            int sliderX = panelX + PANEL_WIDTH - 16 - SLIDER_WIDTH;
            int sliderY = rowY + 10;
            if (mouseX >= sliderX && mouseX <= sliderX + SLIDER_WIDTH && mouseY >= sliderY - 6 && mouseY <= sliderY + 14) {
                draggingSound = true;
                updateVolumeSlider(mouseX, SoundSource.MASTER);
            }
            return;
        }
        if (row == 6) {
            int sliderX = panelX + PANEL_WIDTH - 16 - SLIDER_WIDTH;
            int sliderY = rowY + 10;
            if (mouseX >= sliderX && mouseX <= sliderX + SLIDER_WIDTH && mouseY >= sliderY - 6 && mouseY <= sliderY + 14) {
                draggingMusic = true;
                updateVolumeSlider(mouseX, SoundSource.MUSIC);
            }
            return;
        }
        if (row == 7) {
            if (this.minecraft != null) {
                ClientScreens.set(this.minecraft, new AddonManagerScreen(this));
            }
        }
    }

    private void updateVolumeSlider(int mouseX, SoundSource source) {
        int sliderX = panelX + PANEL_WIDTH - 16 - SLIDER_WIDTH;
        double pct = Math.max(0, Math.min(1, (mouseX - sliderX) / (double) SLIDER_WIDTH));
        OptionInstance<Double> option = this.minecraft.options.getSoundSourceOptionInstance(source);
        option.set(pct);
        this.minecraft.options.save();
    }

    private String getKeyName(int key) {
        String name = NavineInput.getKeyName(key);
        if (name != null) {
            return name.toUpperCase();
        }
        return switch (key) {
            case NavineInput.KEY_LEFT_SHIFT -> "LSHIFT";
            case NavineInput.KEY_RIGHT_SHIFT -> "RSHIFT";
            case NavineInput.KEY_LEFT_CONTROL -> "LCTRL";
            case NavineInput.KEY_LEFT_ALT -> "LALT";
            case NavineInput.KEY_SPACE -> "SPACE";
            case NavineInput.KEY_TAB -> "TAB";
            case NavineInput.KEY_CAPS_LOCK -> "CAPS";
            default -> key >= NavineInput.KEY_F1 && key <= NavineInput.KEY_F12
                    ? "F" + (key - NavineInput.KEY_F1 + 1) : "KEY" + key;
        };
    }
}
