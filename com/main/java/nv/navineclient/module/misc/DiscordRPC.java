package nv.navineclient.module.misc;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRichPresence;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import nv.navineclient.NavineClient;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AuthManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordRPC extends Module {
    private static final String APPLICATION_ID = "1459528682849833124";
    private final BooleanSetting showServer = new BooleanSetting("ShowServer", "Show server IP", true);
    private final BooleanSetting showUsername = new BooleanSetting("ShowUsername", "Show username", true);
    private final NumberSetting updateInterval = new NumberSetting("Interval", "Update interval ticks", 40.0, 10.0, 200.0);

    private DiscordRichPresence presence;
    private ScheduledExecutorService executor;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private long startTime;
    private int updateCounter;

    public DiscordRPC() {
        super("DiscordRPC", "Shows Discord rich presence", Category.MISC);
        addSetting(showServer);
        addSetting(showUsername);
        addSetting(updateInterval);
    }

    @Override
    public void onEnable() {
        startTime = System.currentTimeMillis() / 1000L;
        updateCounter = 0;
        initializeDiscordRPC();
        updateDiscordRPC();
    }

    @Override
    public void onDisable() {
        shutdownDiscordRPC();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || !initialized.get()) {
            return;
        }
        updateCounter++;
        if (updateCounter >= updateInterval.getValue().intValue()) {
            updateCounter = 0;
            updateDiscordRPC();
        }
    }

    private void initializeDiscordRPC() {
        try {
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = user -> NavineClient.LOGGER.info("Discord RPC ready for {}", user.username);
            handlers.disconnected = (errorCode, message) -> NavineClient.LOGGER.warn("Discord RPC disconnected: {} {}", errorCode, message);
            handlers.errored = (errorCode, message) -> NavineClient.LOGGER.warn("Discord RPC error: {} {}", errorCode, message);
            club.minnced.discord.rpc.DiscordRPC.INSTANCE.Discord_Initialize(APPLICATION_ID, handlers, false, null);
            presence = new DiscordRichPresence();
            presence.startTimestamp = startTime;
            presence.largeImageKey = "navine_logo";
            presence.largeImageText = "Navine Client v" + NavineClient.VERSION;
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "Discord-RPC-Callback-Handler");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleAtFixedRate(() -> {
                try {
                    club.minnced.discord.rpc.DiscordRPC.INSTANCE.Discord_RunCallbacks();
                } catch (Exception e) {
                    NavineClient.LOGGER.debug("Discord RPC callback failure: {}", e.getMessage());
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
            initialized.set(true);
            NavineClient.LOGGER.info("Discord RPC initialized");
        } catch (UnsatisfiedLinkError e) {
            initialized.set(false);
            NavineClient.LOGGER.error("Discord RPC native library failed to load", e);
        } catch (Exception e) {
            initialized.set(false);
            NavineClient.LOGGER.error("Discord RPC initialization failed", e);
        }
    }

    private void updateDiscordRPC() {
        if (!initialized.get() || presence == null) {
            return;
        }
        try {
            String state;
            String details;
            if (mc.player != null) {
                LocalPlayer player = mc.player;
                String username = player.getName().getString();
                String tag = AuthManager.getTagForIRC(username);
                if (showUsername.getValue()) {
                    state = tag + " " + username;
                } else {
                    state = tag.trim();
                }
            } else {
                state = "Navine Client";
            }

            if (mc.isLocalServer()) {
                details = "Singleplayer";
            } else if (mc.getCurrentServer() != null) {
                ServerData serverInfo = mc.getCurrentServer();
                if (showServer.getValue() && serverInfo.ip != null && !serverInfo.ip.isEmpty()) {
                    details = serverInfo.ip;
                } else {
                    details = "Multiplayer";
                }
            } else {
                details = "Main Menu";
            }

            presence.state = truncate(state, 128);
            presence.details = truncate(details, 128);
            presence.startTimestamp = startTime;
            club.minnced.discord.rpc.DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);
        } catch (Exception e) {
            NavineClient.LOGGER.debug("Discord RPC presence update failed: {}", e.getMessage());
        }
    }

    private void shutdownDiscordRPC() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (initialized.compareAndSet(true, false)) {
            try {
                club.minnced.discord.rpc.DiscordRPC.INSTANCE.Discord_ClearPresence();
                club.minnced.discord.rpc.DiscordRPC.INSTANCE.Discord_Shutdown();
            } catch (Exception e) {
                NavineClient.LOGGER.debug("Discord RPC shutdown failure: {}", e.getMessage());
            }
        }
        presence = null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public String getApplicationId() {
        return APPLICATION_ID;
    }
}
