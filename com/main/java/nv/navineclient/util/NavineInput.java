package nv.navineclient.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.network.chat.Component;

public final class NavineInput {
    public static final int KEY_UNKNOWN = 0;
    public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int KEY_ENTER = InputConstants.KEY_RETURN;
    public static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int KEY_DELETE = InputConstants.KEY_DELETE;
    public static final int KEY_TAB = InputConstants.KEY_TAB;
    public static final int KEY_SPACE = InputConstants.KEY_SPACE;
    public static final int KEY_LEFT_SHIFT = InputConstants.KEY_LSHIFT;
    public static final int KEY_RIGHT_SHIFT = InputConstants.KEY_RSHIFT;
    public static final int KEY_LEFT_CONTROL = InputConstants.KEY_LCONTROL;
    public static final int KEY_LEFT_ALT = InputConstants.KEY_LALT;
    public static final int KEY_CAPS_LOCK = InputConstants.KEY_CAPSLOCK;
    public static final int KEY_GRAVE_ACCENT = InputConstants.KEY_GRAVE;
    public static final int KEY_W = InputConstants.KEY_W;
    public static final int KEY_A = InputConstants.KEY_A;
    public static final int KEY_S = InputConstants.KEY_S;
    public static final int KEY_D = InputConstants.KEY_D;
    public static final int KEY_M = InputConstants.KEY_M;
    public static final int KEY_I = InputConstants.KEY_I;
    public static final int KEY_C = InputConstants.KEY_C;
    public static final int KEY_UP = InputConstants.KEY_UP;
    public static final int KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int KEY_RIGHT_CONTROL = InputConstants.KEY_RCONTROL;
    public static final int KEY_F1 = InputConstants.KEY_F1;
    public static final int KEY_F12 = InputConstants.KEY_F12;
    public static final int MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int MOUSE_BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    public static final int MOUSE_BUTTON_MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    public static final int PRESS = InputConstants.PRESS;

    private NavineInput() {
    }

    public static boolean isKeyDown(int key) {
        return key > 0 && InputConstants.isKeyDown(key);
    }

    public static boolean isMouseDown(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null) {
            return false;
        }
        MouseHandler mouse = mc.mouseHandler;
        if (button == MOUSE_BUTTON_LEFT) {
            return mouse.isLeftPressed();
        }
        if (button == MOUSE_BUTTON_RIGHT) {
            return mouse.isRightPressed();
        }
        if (button == MOUSE_BUTTON_MIDDLE) {
            return mouse.isMiddlePressed();
        }
        return false;
    }

    public static String getKeyName(int key) {
        if (key <= 0) {
            return null;
        }
        try {
            Component name = InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName();
            if (name != null) {
                String plain = name.getString();
                if (plain != null && !plain.isEmpty()) {
                    return plain;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static int migrateLegacyKey(int key) {
        if (key <= 0) {
            return 0;
        }
        return switch (key) {
            case 256 -> KEY_ESCAPE;
            case 257, 335 -> KEY_ENTER;
            case 259 -> KEY_BACKSPACE;
            case 261 -> KEY_DELETE;
            case 258 -> KEY_TAB;
            case 32 -> KEY_SPACE;
            case 340 -> KEY_LEFT_SHIFT;
            case 344 -> KEY_RIGHT_SHIFT;
            case 341 -> KEY_LEFT_CONTROL;
            case 342 -> KEY_LEFT_ALT;
            case 280 -> KEY_CAPS_LOCK;
            case 96 -> KEY_GRAVE_ACCENT;
            case 87 -> KEY_W;
            case 65 -> KEY_A;
            case 83 -> KEY_S;
            case 68 -> KEY_D;
            case 77 -> KEY_M;
            case 73 -> KEY_I;
            default -> {
                if (key >= 290 && key <= 301) {
                    yield KEY_F1 + (key - 290);
                }
                yield key;
            }
        };
    }
}
