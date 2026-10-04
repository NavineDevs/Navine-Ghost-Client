package nv.navineclient;

import nv.navineclient.config.ConfigManager;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.ui.ClickGUI;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.ClientScreens;
import nv.navineclient.util.MenuMusicManager;
import nv.navineclient.util.ToggleNotifier;
import net.minecraft.client.Minecraft;
import nv.navineclient.util.NavineInput;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TickPipeline {
    private static final AtomicBoolean fabricEvents = new AtomicBoolean(false);
    private static final AtomicInteger lastModuleTick = new AtomicInteger(Integer.MIN_VALUE);
    private static boolean clickGuiKeyHeld = false;
    private static boolean bigRadarKeyHeld = false;
    private static boolean windowTitleApplied = false;

    private TickPipeline() {
    }

    public static void setFabricEventsActive(boolean active) {
        fabricEvents.set(active);
    }

    public static boolean isFabricEventsActive() {
        return fabricEvents.get();
    }

    public static void onStartTick(Minecraft client) {
        nv.navineclient.util.PlaceConflictGuard.reset();
    }

    public static void onEndTick(Minecraft client) {
        if (client == null) {
            return;
        }
        System.setProperty("ghost.tick.fired", "true");

        if (!windowTitleApplied && client.getWindow() != null) {
            client.getWindow().setTitle(NavineClient.buildWindowTitle());
            windowTitleApplied = true;
        }
        if (client.getWindow() == null) {
            return;
        }

        if (!NavineClient.GHOST) {
            MenuMusicManager.tick();
        }

        long handle = client.getWindow().handle();
        int clickGuiKey = NavineClient.getClickGuiKey();

        boolean guiPressed = NavineInput.isKeyDown(clickGuiKey);
        if (!(ClientScreens.get(client) instanceof ClickGUI)) {
            if (guiPressed && !clickGuiKeyHeld) {
                clickGuiKeyHeld = true;
                if (ClientScreens.get(client) == null && NavineClient.isValidClickGuiKey(clickGuiKey)) {
                    try {
                        ClickGUI gui = new ClickGUI();
                        gui.prepareForOpen();
                        ClientScreens.set(client, gui);
                    } catch (Throwable error) {
                        NavineClient.LOGGER.error("ClickGUI open failed", error);
                    }
                }
            } else if (!guiPressed) {
                clickGuiKeyHeld = false;
            }
        } else if (!guiPressed) {
            clickGuiKeyHeld = false;
        }

        ChatCommandBridge.tick(client);
        boolean wasVerified = AuthManager.isVerified();
        AuthManager.tryRestoreSavedSession();
        if (!wasVerified && AuthManager.isVerified()) {
            ConfigManager.reloadEnabledModules();
        }

        if (!AuthManager.isVerified()) {
            for (Module module : ModuleManager.getModules()) {
                if (module.isEnabled()) {
                    module.setEnabledSilent(false);
                }
            }
            return;
        }

        GhostHitOverride.apply(client);

        if (!NavineClient.GHOST) {
            int gameTick = gameTick(client);
            if (gameTick == lastModuleTick.get()) {
                return;
            }
            lastModuleTick.set(gameTick);
        }

        nv.navineclient.module.render.Radar radar =
                (nv.navineclient.module.render.Radar) ModuleManager.getModuleByName("Radar");
        if (radar != null && radar.isEnabled() && radar.isBigRadarEnabled()) {
            boolean mPressed = NavineInput.isKeyDown(NavineInput.KEY_M);
            if (mPressed && !bigRadarKeyHeld && ClientScreens.get(client) == null) {
                bigRadarKeyHeld = true;
                ClientScreens.set(client, new nv.navineclient.ui.BigRadarScreen());
            } else if (!mPressed) {
                bigRadarKeyHeld = false;
            }
        }

        for (Module module : ModuleManager.getModules()) {
            module.refreshClient();
            if (module.isEnabled()) {
                if (ModuleManager.isModuleAccessible(module)) {
                    module.onTick();
                } else {
                    module.setEnabled(false);
                }
            }
        }

        nv.navineclient.module.render.Zoom zoom = nv.navineclient.module.render.Zoom.INSTANCE;
        if (zoom != null && !zoom.isEnabled() && zoom.needsTick()) {
            zoom.onTick();
        }

        boolean clickGuiOpen = ClientScreens.get(client) instanceof ClickGUI;
        for (Module module : ModuleManager.getModules()) {
            if (clickGuiOpen) {
                continue;
            }
            if (module.getKey() != 0 && ClientScreens.get(client) == null) {
                if (module.isKeybindSuppressed()) {
                    continue;
                }
                if (!ModuleManager.canToggleModule(module)) {
                    continue;
                }
                boolean pressed = NavineInput.isKeyDown(module.getKey());
                if (module instanceof nv.navineclient.module.render.Zoom zoomModule && zoomModule.useHoldKey()) {
                    zoomModule.setEnabled(pressed);
                    zoomModule.setKeyHeld(pressed);
                    module.keyHeld = pressed;
                    if (zoomModule.needsTick()) {
                        zoomModule.onTick();
                    }
                    continue;
                }
                if (pressed && !module.keyHeld) {
                    module.keyHeld = true;
                    module.toggle();
                    if (!NavineClient.GHOST && !module.getName().equalsIgnoreCase("Flashstep")) {
                        String state = module.isEnabled() ? "Enabled" : "Disabled";
                        ToggleNotifier.show(module.getName() + " " + state);
                    }
                    ConfigManager.save();
                } else if (!pressed) {
                    module.keyHeld = false;
                }
            }
        }

        nv.navineclient.commands.SpectateCommand.tick();
        nv.navineclient.module.movement.Blink.tickRelease();
        if (!NavineClient.GHOST) {
            nv.navineclient.util.NavinePathfinder.tick();
        }
    }

    private static int gameTick(Minecraft client) {
        try {
            if (client.player != null) {
                return client.player.tickCount;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (client.level != null) {
                return (int) client.level.getGameTime();
            }
        } catch (Throwable ignored) {
        }
        return (int) (System.nanoTime() / 50_000_000L);
    }
}
