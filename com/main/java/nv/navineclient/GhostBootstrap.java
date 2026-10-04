package nv.navineclient;

import nv.navineclient.commands.CommandManager;
import nv.navineclient.config.ConfigManager;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.BackgroundManager;
import nv.navineclient.util.DiscordNativeLoader;
import nv.navineclient.util.MenuMusicManager;

import java.util.concurrent.atomic.AtomicBoolean;

public final class GhostBootstrap {
    private static final AtomicBoolean started = new AtomicBoolean(false);

    private GhostBootstrap() {
    }

    public static boolean isStarted() {
        return started.get();
    }

    public static void init() {
        if (!started.compareAndSet(false, true)) {
            ChatCommandBridge.ensureRegistered();
            AuthManager.tryRestoreSavedSession();
            if (AuthManager.isVerified()) {
                ConfigManager.reloadEnabledModules();
            }
            NavineClient.LOGGER.info("alreadyBootstrapped skipConfigReload verified=" + AuthManager.isVerified());
            return;
        }
        NavineClient.LOGGER.info("{} v{} Initializing (ghost bootstrap)...", NavineClient.displayName(), NavineClient.VERSION);
        if (!NavineClient.GHOST) {
            BackgroundManager.loadExternalBackground();
            MenuMusicManager.ensureLoaded();
            DiscordNativeLoader.load();
        }
        AuthManager.fetchUsers();
        AddonManager.init();
        ModuleManager.init();
        CommandManager.init();
        ConfigManager.restoreSavedSessionIfNeeded();
        ConfigManager.load();
        if (AuthManager.isVerified()) {
            ConfigManager.reloadEnabledModules();
        }
        AuthManager.tryRestoreSavedSession();
        if (AuthManager.isVerified()) {
            ConfigManager.reloadEnabledModules();
        }
        enforceDevModAccess();
        ChatCommandBridge.ensureRegistered();
        NavineClient.LOGGER.info("Ghost session ready — ClickGUI=` — .login once (autologin keeps session)");
    }

    private static void enforceDevModAccess() {
        Module devMod = ModuleManager.getModuleByName("DevMod");
        if (devMod != null && devMod.isEnabled() && !devMod.isVisibleInClickGui()) {
            devMod.setEnabled(false);
        }
    }
}
