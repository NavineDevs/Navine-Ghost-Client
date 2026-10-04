package nv.navineclient.util;

public final class ToggleNotifier {
    private static String moduleName = null;
    private static String state = null;
    private static long showUntil = 0;
    private static long showFrom = 0;
    private static final long DURATION_MS = 2000;

    private ToggleNotifier() {
    }

    public static void show(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (text.endsWith(" Enabled")) {
            moduleName = text.substring(0, text.length() - 8);
            state = "Enabled";
        } else if (text.endsWith(" Disabled")) {
            moduleName = text.substring(0, text.length() - 9);
            state = "Disabled";
        } else {
            int lastSpace = text.lastIndexOf(' ');
            if (lastSpace > 0) {
                moduleName = text.substring(0, lastSpace);
                state = text.substring(lastSpace + 1);
            } else {
                moduleName = text;
                state = "";
            }
        }
        showFrom = System.currentTimeMillis();
        showUntil = showFrom + DURATION_MS;
    }

    public static String getModuleName() {
        return moduleName;
    }

    public static String getState() {
        return state;
    }

    public static boolean isEnabledState() {
        return "Enabled".equalsIgnoreCase(state);
    }

    public static float getAlpha() {
        if (moduleName == null) {
            return 0f;
        }
        long now = System.currentTimeMillis();
        if (now >= showUntil) {
            return 0f;
        }
        long elapsed = now - showFrom;
        long remaining = showUntil - now;
        float fadeIn = Math.min(1f, elapsed / 150f);
        float fadeOut = Math.min(1f, remaining / 300f);
        return Math.min(fadeIn, fadeOut);
    }

    public static float getSlideOffset() {
        float alpha = getAlpha();
        return (1f - UiAnim.easeOutCubic(alpha)) * 8f;
    }

    public static boolean isVisible() {
        return getAlpha() > 0f;
    }
}
