package nv.navineclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import nv.navineclient.util.NavineButtonRenderer;
import nv.navineclient.util.NavineSoundManager;
import nv.navineclient.util.UiAnim;

public class CoolButton extends Button {
    private int textColor = -1;
    private boolean menuStyle;
    private boolean wasHovered;
    private float hoverAnim;
    private float revealAnim;
    private float revealTime;
    private int revealOrder;

    public CoolButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, true);
    }

    public CoolButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean menuStyle) {
        super(x, y, width, height, message, wrapPress(onPress), DEFAULT_NARRATION);
        this.menuStyle = menuStyle;
    }

    private static OnPress wrapPress(OnPress onPress) {
        return button -> {
            NavineSoundManager.playClick();
            onPress.onPress(button);
        };
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }

    public void setMenuStyle(boolean menuStyle) {
        this.menuStyle = menuStyle;
    }

    public void setRevealOrder(int order) {
        this.revealOrder = order;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered();
        hoverAnim = UiAnim.approach(hoverAnim, hovered ? 1f : 0f, 14f, delta);
        revealTime += delta;
        revealAnim = UiAnim.easeOutCubic(UiAnim.delayedProgress(revealTime, revealOrder * 0.045f, 7f));

        if (menuStyle && hovered && !wasHovered) {
            NavineSoundManager.playHover();
        }
        wasHovered = hovered;

        int color = textColor != -1 ? textColor : -1;
        if (menuStyle) {
            NavineButtonRenderer.renderMenuStyle(context, this, color, hoverAnim, revealAnim);
            return;
        }

        NavineButtonRenderer.renderMenuStyle(context, this, color, hoverAnim, revealAnim);
    }
}
