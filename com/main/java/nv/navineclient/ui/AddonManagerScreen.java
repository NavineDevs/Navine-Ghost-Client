package nv.navineclient.ui;

import nv.navineclient.AddonManager;
import nv.navineclient.NavineAddon;
import nv.navineclient.util.NavineTheme;
import nv.navineclient.util.RenderUtil;
import nv.navineclient.util.ScreenBackgroundHelper;
import nv.navineclient.util.UiAnim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nv.navineclient.util.NavineInput;

import java.util.List;

public class AddonManagerScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 36;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private int scroll = 0;
    private int maxScroll = 0;
    private boolean leftMouseDown = false;
    private float screenAnim = 0f;
    private float uiDelta = 0f;
    private final java.util.Map<NavineAddon, Float> toggleHoverAnim = new java.util.HashMap<>();

    public AddonManagerScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        screenAnim = 0f;
        toggleHoverAnim.clear();
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = 40;
        panelHeight = Math.min(this.height - 80, 280);
        addRenderableWidget(ScreenBackgroundHelper.createTopBackButton(parent));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long window = this.minecraft.getWindow().handle();
        boolean leftNow = NavineInput.isMouseDown(NavineInput.MOUSE_BUTTON_LEFT);
        if (leftNow && !leftMouseDown) {
            handleToggleClick(mouseX, mouseY);
        }
        leftMouseDown = leftNow;
        uiDelta = delta;

        screenAnim = UiAnim.approach(screenAnim, 1f, 8f, delta);
        float ease = UiAnim.easeOutCubic(screenAnim);
        int panelDrawY = panelY + (int) ((1f - ease) * 14f);

        ScreenBackgroundHelper.renderNavineBackground(context, this.width, this.height);
        ScreenBackgroundHelper.renderTopBar(context, this.width);

        String title = "Addon Manager";
        int titleW = this.font.width(title);
        context.text(this.font, title, (this.width - titleW) / 2, 8, UiAnim.scaleAlpha(NavineTheme.ROYAL_BLUE, ease), true);

        RenderUtil.drawRoundedRect(context, panelX, panelDrawY, PANEL_WIDTH, panelHeight, 4, UiAnim.scaleAlpha(NavineTheme.PANEL_BG, ease));
        RenderUtil.drawRoundedOutline(context, panelX, panelDrawY, PANEL_WIDTH, panelHeight, 4, 1, UiAnim.scaleAlpha(NavineTheme.BORDER, ease));

        List<NavineAddon> addons = AddonManager.getAddons();
        int contentTop = panelDrawY + 8;
        int contentBottom = panelDrawY + panelHeight - 8;
        int listHeight = contentBottom - contentTop;
        int contentHeight = addons.isEmpty() ? ROW_HEIGHT : addons.size() * ROW_HEIGHT;
        maxScroll = Math.max(0, contentHeight - listHeight);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        context.enableScissor(panelX + 4, contentTop, panelX + PANEL_WIDTH - 4, contentBottom);

        if (addons.isEmpty()) {
            String empty = "No addons loaded";
            int emptyW = this.font.width(empty);
            context.text(this.font, empty, panelX + (PANEL_WIDTH - emptyW) / 2, contentTop + 12, NavineTheme.TEXT_SECONDARY, true);
        } else {
            int y = contentTop - scroll;
            int rowW = PANEL_WIDTH - 24;
            for (NavineAddon addon : addons) {
                if (y + ROW_HEIGHT >= contentTop && y <= contentBottom) {
                    renderAddonRow(context, addon, panelX + 12, y, rowW, ROW_HEIGHT - 4, mouseX, mouseY);
                }
                y += ROW_HEIGHT;
            }
        }

        context.disableScissor();

        if (contentHeight > listHeight) {
            int sbX = panelX + PANEL_WIDTH - 5;
            int sbY = contentTop;
            int sbH = contentBottom - contentTop;
            context.fill(sbX, sbY, sbX + 3, sbY + sbH, 0xFF1A1A2E);
            double pct = maxScroll > 0 ? (double) scroll / maxScroll : 0;
            int thumbH = Math.max(12, (int) ((double) sbH / contentHeight * sbH));
            int thumbY = sbY + (int) (pct * (sbH - thumbH));
            context.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, NavineTheme.ACCENT);
        }

        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
    }

    private void handleToggleClick(int mouseX, int mouseY) {
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < panelY || mouseY > panelY + panelHeight) {
            return;
        }

        List<NavineAddon> addons = AddonManager.getAddons();
        int contentTop = panelY + 8;
        int contentBottom = panelY + panelHeight - 8;
        int y = contentTop - scroll;
        int rowW = PANEL_WIDTH - 24;
        int toggleW = 72;
        int toggleH = 14;

        for (NavineAddon addon : addons) {
            if (y + ROW_HEIGHT < contentTop || y > contentBottom) {
                y += ROW_HEIGHT;
                continue;
            }
            int toggleX = panelX + 12 + rowW - toggleW - 6;
            int toggleY = y + (ROW_HEIGHT - 4 - toggleH) / 2;
            if (mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= toggleY && mouseY <= toggleY + toggleH) {
                AddonManager.setAddonEnabled(addon, !AddonManager.isAddonEnabled(addon));
                return;
            }
            y += ROW_HEIGHT;
        }
    }

    private void renderAddonRow(GuiGraphicsExtractor context, NavineAddon addon, int x, int y, int w, int h, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + h, NavineTheme.PANEL_BG);
        context.fill(x, y, x + w, y + 1, NavineTheme.ROYAL_BLUE);
        context.fill(x, y + h - 1, x + w, y + h, NavineTheme.PURPLE);

        int textMax = w - 90;
        String name = truncate(addon.getName(), textMax);
        context.text(this.font, name, x + 6, y + 4, NavineTheme.TEXT_PRIMARY, true);

        String version = "v" + addon.getVersion();
        context.text(this.font, version, x + 6, y + 16, NavineTheme.PURPLE_BRIGHT, false);

        String authors = truncate(String.join(", ", addon.getAuthors()), textMax / 2);
        int authorsW = this.font.width(authors);
        context.text(this.font, authors, x + w - authorsW - 84, y + 16, NavineTheme.TEXT_SECONDARY, false);

        String pkg = truncate(addon.getPackage(), textMax / 2);
        int pkgW = this.font.width(pkg);
        context.text(this.font, pkg, x + w - pkgW - 84, y + 4, NavineTheme.ROYAL_BLUE_DIM, false);

        boolean enabled = AddonManager.isAddonEnabled(addon);
        int toggleW = 72;
        int toggleH = 14;
        int toggleX = x + w - toggleW - 6;
        int toggleY = y + (h - toggleH) / 2;
        boolean hover = mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= toggleY && mouseY <= toggleY + toggleH;
        float hoverAnim = toggleHoverAnim.getOrDefault(addon, 0f);
        hoverAnim = UiAnim.approach(hoverAnim, hover ? 1f : 0f, 14f, uiDelta);
        toggleHoverAnim.put(addon, hoverAnim);
        int toggleBg = UiAnim.lerpColor(NavineTheme.HEADER_BG, NavineTheme.ENABLED_BG, hoverAnim);
        context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg);
        context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + 1, UiAnim.lerpColor(NavineTheme.ROYAL_BLUE, NavineTheme.PURPLE_BRIGHT, hoverAnim));
        context.fill(toggleX, toggleY + toggleH - 1, toggleX + toggleW, toggleY + toggleH, UiAnim.lerpColor(NavineTheme.PURPLE, NavineTheme.ROYAL_BLUE, hoverAnim));

        String toggleLabel = enabled ? "Enabled" : "Disabled";
        int labelW = this.font.width(toggleLabel);
        int labelColor = enabled ? NavineTheme.TEXT_PRIMARY : NavineTheme.TEXT_DISABLED;
        context.text(this.font, toggleLabel, toggleX + (toggleW - labelW) / 2, toggleY + 3, labelColor, false);
    }

    private String truncate(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        String trimmed = text;
        while (trimmed.length() > 0 && this.font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            scroll -= (int) (scrollY * ROW_HEIGHT);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
