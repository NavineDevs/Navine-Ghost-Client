package nv.navineclient.util;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Map;
import java.util.WeakHashMap;

public final class UiAnim {
    private static final Map<AbstractWidget, Float> HOVER = new WeakHashMap<>();

    private UiAnim() {
    }

    public static float approach(float current, float target, float speed, float delta) {
        if (delta <= 0f) {
            delta = 1f / 20f;
        }
        float factor = 1f - (float) Math.exp(-speed * delta);
        return current + (target - current) * factor;
    }

    public static float easeOutCubic(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        float inv = 1f - c;
        return 1f - inv * inv * inv;
    }

    public static float easeOutBack(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        float s = 1.70158f;
        float inv = c - 1f;
        return 1f + (s + 1f) * inv * inv * inv + s * inv * inv;
    }

    public static float delayedProgress(float elapsed, float delay, float speed) {
        return Math.min(1f, Math.max(0f, elapsed - delay) * speed);
    }

    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int fa = (from >> 24) & 0xFF;
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int scaleAlpha(int color, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int a = (color >> 24) & 0xFF;
        int scaled = (int) (a * factor);
        return (scaled << 24) | (color & 0x00FFFFFF);
    }

    public static float trackHover(AbstractWidget widget, boolean hovered, float delta) {
        float current = HOVER.getOrDefault(widget, 0f);
        float next = approach(current, hovered ? 1f : 0f, 14f, delta);
        HOVER.put(widget, next);
        return next;
    }

    public static void clearHover(AbstractWidget widget) {
        HOVER.remove(widget);
    }
}
