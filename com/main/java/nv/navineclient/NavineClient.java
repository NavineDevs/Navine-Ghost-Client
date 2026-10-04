package nv.navineclient;

import nv.navineclient.config.ConfigManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import nv.navineclient.util.NavineInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class NavineClient {
    public static final boolean GHOST = true;
    public static final Logger LOGGER = LoggerFactory.getLogger(GHOST ? "navine-ghost" : "navine-client");
    public static final String MOD_ID = GHOST ? "navine-ghost" : "navine-client";
    public static final String VERSION = "1.1.0";

    private static int clickGuiKey = NavineInput.KEY_RIGHT_SHIFT;

    public static boolean isValidClickGuiKey(int key) {
        return key > 0 && key != NavineInput.KEY_UNKNOWN;
    }

    public static void ensureClientRuntime() {
        GhostBootstrap.init();
        tryRegisterFabricTicks();
        ChatCommandBridge.ensureRegistered();
    }

    public void onInitializeClient() {
        GhostBootstrap.init();
        tryRegisterFabricTicks();
        ChatCommandBridge.ensureRegistered();
    }

    static void tryRegisterFabricTicks() {
        if (GHOST) {
            TickPipeline.setFabricEventsActive(false);
            return;
        }
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
            Object startEvent = events.getField("START_CLIENT_TICK").get(null);
            Object endEvent = events.getField("END_CLIENT_TICK").get(null);
            registerEvent(startEvent, true);
            registerEvent(endEvent, false);
            TickPipeline.setFabricEventsActive(true);
            LOGGER.info("Registered Fabric ClientTickEvents");
        } catch (Throwable error) {
            TickPipeline.setFabricEventsActive(false);
            LOGGER.info("Fabric tick events unavailable — mixin/agent tick drives TickPipeline ({})",
                    error.getClass().getSimpleName());
        }
    }

    private static void registerEvent(Object event, boolean start) throws Exception {
        Method register = null;
        for (Method m : event.getClass().getMethods()) {
            if (m.getName().equals("register") && m.getParameterCount() == 1) {
                register = m;
                break;
            }
        }
        if (register == null) {
            throw new NoSuchMethodException("Event.register");
        }
        Class<?> callbackType = register.getParameterTypes()[0];
        Object listener = Proxy.newProxyInstance(
                callbackType.getClassLoader(),
                new Class<?>[]{callbackType},
                (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof Minecraft mc) {
                        if (start) {
                            TickPipeline.onStartTick(mc);
                        } else {
                            TickPipeline.onEndTick(mc);
                        }
                    }
                    return null;
                });
        register.invoke(event, listener);
    }

    public static boolean isGhost() {
        return GHOST;
    }

    public static String displayName() {
        return GHOST ? "Navine-Ghost-Client" : "Navine Client";
    }

    public static int getClickGuiKey() {
        return clickGuiKey;
    }

    public static void setClickGuiKey(int key) {
        setClickGuiKey(key, true);
    }

    public static void setClickGuiKey(int key, boolean save) {
        if (!isValidClickGuiKey(key)) {
            key = NavineInput.KEY_RIGHT_SHIFT;
        }
        clickGuiKey = key;
        if (save) {
            ConfigManager.save();
        }
    }

    public static String buildWindowTitle() {
        if (GHOST) {
            return "Minecraft " + SharedConstants.getCurrentVersion().name();
        }
        return "Navine Client | v" + VERSION + " | " + SharedConstants.getCurrentVersion().name();
    }
}
