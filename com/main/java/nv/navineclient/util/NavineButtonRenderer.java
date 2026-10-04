package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import nv.navineclient.NavineClient;

public final class NavineButtonRenderer {
    private NavineButtonRenderer() {
    }

    public static void render(GuiGraphicsExtractor context, AbstractWidget widget, int textColorOverride) {
        renderMenuStyle(context, widget, textColorOverride, widget.isHoveredOrFocused() ? 1f : 0f, 1f);
    }

    public static void renderMenuStyle(GuiGraphicsExtractor context, AbstractWidget widget, int textColorOverride) {
        renderMenuStyle(context, widget, textColorOverride, widget.isHoveredOrFocused() ? 1f : 0f, 1f);
    }

    public static void renderMenuStyle(GuiGraphicsExtractor context, AbstractWidget widget, int textColorOverride, float hoverFactor) {
        renderMenuStyle(context, widget, textColorOverride, hoverFactor, 1f);
    }

    public static void renderMenuStyle(GuiGraphicsExtractor context, AbstractWidget widget, int textColorOverride, float hoverFactor, float revealAnim) {
        hoverFactor = Math.max(0f, Math.min(1f, hoverFactor));
        revealAnim = Math.max(0f, Math.min(1f, revealAnim));

        int x = widget.getX();
        int y = widget.getY() + (int) ((1f - revealAnim) * 8f);
        int w = widget.getWidth();
        int h = widget.getHeight();
        boolean active = widget.active;

        int shadow = UiAnim.scaleAlpha(0x66000000, revealAnim);
        RenderUtil.drawRoundedRect(context, x + 1, y + 2, w, h, 5, shadow);

        int normalBg = NavineClient.isGhost() ? 0xCC101216 : 0xCC0C0C18;
        int bg = !active
                ? UiAnim.scaleAlpha(NavineTheme.panelBg(), revealAnim)
                : UiAnim.scaleAlpha(UiAnim.lerpColor(normalBg, NavineTheme.enabledBg(), hoverFactor), revealAnim);
        RenderUtil.drawRoundedRect(context, x, y, w, h, 5, bg);

        if (active && hoverFactor > 0.01f) {
            int hoverFill = UiAnim.scaleAlpha(UiAnim.lerpColor(0x00000000, NavineTheme.hover(), hoverFactor), revealAnim);
            RenderUtil.drawRoundedRect(context, x + 1, y + 1, w - 2, h - 2, 4, hoverFill);
        }

        int topNormal = NavineClient.isGhost() ? NavineTheme.accent() : NavineTheme.ROYAL_BLUE;
        int topHover = NavineClient.isGhost() ? NavineTheme.accent2() : NavineTheme.PURPLE_BRIGHT;
        int bottomNormal = NavineClient.isGhost() ? NavineTheme.border() : NavineTheme.PURPLE;
        int bottomHover = NavineClient.isGhost() ? NavineTheme.accent() : NavineTheme.ROYAL_BLUE;
        int top = !active
                ? UiAnim.scaleAlpha(NavineClient.isGhost() ? NavineTheme.border() : NavineTheme.ROYAL_BLUE_DIM, revealAnim)
                : UiAnim.scaleAlpha(UiAnim.lerpColor(topNormal, topHover, hoverFactor), revealAnim);
        int bottom = !active
                ? UiAnim.scaleAlpha(NavineTheme.border(), revealAnim)
                : UiAnim.scaleAlpha(UiAnim.lerpColor(bottomNormal, bottomHover, hoverFactor), revealAnim);
        RenderUtil.drawRoundedOutline(context, x, y, w, h, 5, 1, top);
        context.fill(x + 2, y + h - 1, x + w - 2, y + h, bottom);

        if (active && hoverFactor > 0.01f) {
            int shine = UiAnim.scaleAlpha(UiAnim.lerpColor(0x00000000, 0x55FFFFFF, hoverFactor), revealAnim);
            context.fill(x, y, x + w, y + 1, shine);
            int glow = UiAnim.scaleAlpha(UiAnim.lerpColor(0x00000000, NavineTheme.accent2(), hoverFactor * 0.5f), revealAnim);
            context.fill(x - 1, y + 2, x, y + h - 2, glow);
            context.fill(x + w, y + 2, x + w + 1, y + h - 2, glow);
        }

        int color;
        if (textColorOverride != -1) {
            color = UiAnim.scaleAlpha(textColorOverride, revealAnim);
        } else if (!active) {
            color = UiAnim.scaleAlpha(NavineTheme.TEXT_DISABLED, revealAnim);
        } else {
            color = UiAnim.scaleAlpha(
                    UiAnim.lerpColor(NavineTheme.TEXT_SECONDARY, NavineTheme.TEXT_PRIMARY, hoverFactor),
                    revealAnim
            );
        }

        Component message = widget.getMessage();
        context.centeredText(Minecraft.getInstance().font, message, x + w / 2, y + (h - 8) / 2, color);
    }

    public static void renderPanelButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, float hoverFactor, int alpha) {
        hoverFactor = Math.max(0f, Math.min(1f, hoverFactor));
        int bg = UiAnim.lerpColor(NavineTheme.headerBg(), NavineTheme.enabledBg(), hoverFactor);
        context.fill(x, y, x + w, y + h, applyAlpha(bg, alpha));
        int border = UiAnim.lerpColor(NavineTheme.border(), NavineTheme.accent(), hoverFactor);
        RenderUtil.drawOutline(context, x, y, w, h, 1, applyAlpha(border, alpha));
        int textBase = UiAnim.lerpColor(NavineTheme.TEXT_SECONDARY, NavineTheme.TEXT_PRIMARY, hoverFactor);
        int textColor = applyAlpha(textBase, alpha);
        int textW = Minecraft.getInstance().font.width(label);
        context.text(Minecraft.getInstance().font, label, x + (w - textW) / 2, y + (h - 8) / 2, textColor, false);
    }

    private static int applyAlpha(int color, int alpha) {
        int a = Math.min(255, Math.max(0, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
