package nv.navineclient;

import nv.navineclient.commands.CommandManager;
import nv.navineclient.util.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import nv.navineclient.util.NavineInput;
import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatCommandBridge {
    private static final AtomicBoolean fabricHooked = new AtomicBoolean(false);
    private static final AtomicBoolean loggedChannel = new AtomicBoolean(false);
    private static final AtomicReference<String> activeChannel = new AtomicReference<>("none");
    private static long lastCommandAt = 0L;
    private static boolean enterHeld = false;

    private ChatCommandBridge() {
    }

    public static String activeChannel() {
        return activeChannel.get();
    }

    public static void markMixinChannel() {
        activeChannel.set("mixin");
        logChannelOnce("mixin");
    }

    public static void ensureRegistered() {
        if (fabricHooked.get()) {
            return;
        }
        if (tryRegisterFabricSendHook()) {
            fabricHooked.set(true);
            if (!"mixin".equals(activeChannel.get())) {
                activeChannel.set("fabric-hook");
                logChannelOnce("fabric-hook");
            }
        }
    }

    public static boolean isChatOpen(Minecraft client) {
        return client != null && isChatScreen(ClientScreens.get(client));
    }

    public static void tick(Minecraft client) {
        ensureRegistered();
        pollEnter(client);
    }

    public static boolean tryHandleClientCommand(String message, boolean addToHistory) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        String prefix = CommandManager.getPrefix();
        if (prefix == null || prefix.isEmpty() || !trimmed.startsWith(prefix)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCommandAt < 100L) {
            return true;
        }
        lastCommandAt = now;
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            mc = null;
        }
        if (mc != null) {
            Runnable dispatch = () -> dispatchClientCommand(trimmed, addToHistory);
            try {
                if (mc.isSameThread()) {
                    dispatch.run();
                } else {
                    mc.execute(dispatch);
                }
            } catch (Throwable ignored) {
                dispatch.run();
            }
            return true;
        }
        dispatchClientCommand(trimmed, addToHistory);
        return true;
    }

    private static void dispatchClientCommand(String trimmed, boolean addToHistory) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (addToHistory && mc != null && mc.gui != null) {
                ChatComponent chat = ClientAccess.getChat(mc); if (chat != null) chat.addRecentChat(trimmed);
            }
        } catch (Throwable ignored) {
        }
        try {
            CommandManager.handleCommand(trimmed);
        } catch (Throwable error) {
            NavineClient.LOGGER.error("Command failed: {}", trimmed, error);
        }
    }

    private static void pollEnter(Minecraft client) {
        if (client == null || client.getWindow() == null || !isChatScreen(ClientScreens.get(client))) {
            enterHeld = false;
            return;
        }
        boolean pressed = NavineInput.isKeyDown(NavineInput.KEY_ENTER)
                || NavineInput.isKeyDown(NavineInput.KEY_KP_ENTER);
        if (pressed && !enterHeld) {
            enterHeld = true;
            interceptEnter();
        } else if (!pressed) {
            enterHeld = false;
        }
    }

    private static boolean interceptEnter() {
        Minecraft client;
        try {
            client = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return false;
        }
        if (client == null || !isChatScreen(ClientScreens.get(client))) {
            return false;
        }
        Screen currentScreen = ClientScreens.get(client);
        String text = readChatInput(currentScreen);
        if (text == null || !tryHandleClientCommand(text, true)) {
            return false;
        }
        clearChatInput(currentScreen);
        try {
            ClientScreens.set(client, null);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static boolean isChatScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof ChatScreen) {
            return true;
        }
        String name = screen.getClass().getName();
        return name.contains("ChatScreen") || name.contains("chat.Chat");
    }

    private static void logChannelOnce(String channel) {
        if (loggedChannel.compareAndSet(false, true)) {
            NavineClient.LOGGER.info("Chat command bridge channel={}", channel);
        }
    }

    private static boolean tryRegisterFabricSendHook() {
        ClassLoader[] loaders = buildLoaders();
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> events = Class.forName(
                        "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents", true, loader);
                if (hookEventField(events, "ALLOW_CHAT", true)
                        || hookEventField(events, "ALLOW_COMMAND", true)
                        || hookEventField(events, "MODIFY_CHAT", false)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                Class<?> events = Class.forName(
                        "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents", true, loader);
                for (Field f : events.getFields()) {
                    String n = f.getName().toUpperCase(Locale.ROOT);
                    if (n.contains("CHAT") || n.contains("COMMAND")) {
                        if (hookEventObject(f.get(null), n.contains("ALLOW") || n.startsWith("ALLOW"))) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static ClassLoader[] buildLoaders() {
        ClassLoader mc = null;
        try {
            Minecraft instance = Minecraft.getInstance();
            if (instance != null) {
                mc = instance.getClass().getClassLoader();
            }
        } catch (Throwable ignored) {
        }
        return new ClassLoader[]{
                mc,
                Thread.currentThread().getContextClassLoader(),
                ChatCommandBridge.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
    }

    private static boolean hookEventField(Class<?> events, String field, boolean denyOnCommand) {
        try {
            return hookEventObject(events.getField(field).get(null), denyOnCommand);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hookEventObject(Object event, boolean denyOnCommand) {
        if (event == null) {
            return false;
        }
        try {
            Method register = null;
            for (Method m : event.getClass().getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 1) {
                    register = m;
                    break;
                }
            }
            if (register == null) {
                return false;
            }
            Class<?> callbackType = register.getParameterTypes()[0];
            Object listener = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        String msg = extractMessage(args);
                        if (msg != null && tryHandleClientCommand(msg, true)) {
                            Class<?> ret = method.getReturnType();
                            if (ret == boolean.class || ret == Boolean.class) {
                                return denyOnCommand ? Boolean.FALSE : Boolean.TRUE;
                            }
                            return msg;
                        }
                        Class<?> ret = method.getReturnType();
                        if (ret == boolean.class || ret == Boolean.class) {
                            return Boolean.TRUE;
                        }
                        if (ret == String.class && args != null && args.length > 0 && args[0] instanceof String s) {
                            return s;
                        }
                        return null;
                    });
            register.invoke(event, listener);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String extractMessage(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String s) {
                return s;
            }
        }
        return null;
    }

    private static String readChatInput(Screen screen) {
        EditBox box = findEditBox(screen);
        return box != null ? box.getValue() : null;
    }

    private static void clearChatInput(Screen screen) {
        EditBox box = findEditBox(screen);
        if (box != null) {
            try {
                box.setValue("");
            } catch (Throwable ignored) {
            }
        }
    }

    private static EditBox findEditBox(Screen screen) {
        if (screen == null) {
            return null;
        }
        try {
            Class<?> c = screen.getClass();
            while (c != null && c != Object.class) {
                for (Field field : c.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof EditBox box) {
                        return box;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
