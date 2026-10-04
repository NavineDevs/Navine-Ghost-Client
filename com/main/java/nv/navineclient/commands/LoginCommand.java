package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.NavineClient;
import nv.navineclient.config.ConfigManager;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.ChatUtils;
import nv.navineclient.util.NavineInput;

public class LoginCommand extends Command {
    public LoginCommand() {
        super("login", "Login to Navine (for Lunar/Feather clients)", ".login <username>");
    }

    @Override
    public void onCommand(String[] args) {
        try {
            handleLogin(args);
        } catch (Throwable error) {
            NavineClient.LOGGER.error("Login command failed", error);
            ChatUtils.message("§cLogin failed. Check logs.");
        }
    }

    private void handleLogin(String[] args) {
        if (args.length < 1) {
            ChatUtils.message("§b§l=== Navine Login ===");
            ChatUtils.message("§fUsage: §7.login <username>");
            ChatUtils.message("§fUsage: §7.login autologin <on|off>");
            ChatUtils.message("");
            ChatUtils.message("§7This command is for §eLunar§7/§6Feather§7 client users");
            ChatUtils.message("§7who don't see the normal login screen.");
            ChatUtils.message("");
            if (AuthManager.isVerified()) {
                String tag = AuthManager.getTagForIRC(AuthManager.getLoggedInUser());
                ChatUtils.message("§7Status: §aLogged in as §f" + tag + " " + AuthManager.getLoggedInUser());
            } else {
                ChatUtils.message("§7Status: §cNot logged in");
            }
            ChatUtils.message("§7Auto-login: " + (ConfigManager.isAutoLoginEnabled() ? "§aon" : "§coff"));
            ChatUtils.message("§7Use §f.logout§7 to sign out and clear the saved session.");
            return;
        }

        if (args[0].equalsIgnoreCase("autologin")) {
            if (args.length < 2) {
                ChatUtils.message("§7Auto-login is currently " + (ConfigManager.isAutoLoginEnabled() ? "§aon" : "§coff"));
                ChatUtils.message("§7Usage: §f.login autologin <on|off>");
                return;
            }
            boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
            boolean disable = args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false");
            if (!enable && !disable) {
                ChatUtils.message("§cUse §fon§c or §foff§c.");
                return;
            }
            ConfigManager.setAutoLoginEnabled(enable);
            ChatUtils.message("§aAuto-login " + (enable ? "enabled" : "disabled") + ".");
            if (!enable) {
                ChatUtils.message("§7You will need to log in from the title screen on each launch.");
            }
            return;
        }

        String username = args[0].trim();
        if (username.isEmpty()) {
            ChatUtils.message("§cUsername cannot be empty.");
            return;
        }

        if (AuthManager.isVerified() && username.equalsIgnoreCase(AuthManager.getLoggedInUser())) {
            ChatUtils.message("§aAlready logged in as §f" + AuthManager.getLoggedInUser());
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChatUtils.message("§7Verifying whitelist...");

        Thread worker = new Thread(() -> {
            boolean ok;
            try {
                ok = AuthManager.verifyBlocking(username);
            } catch (Throwable error) {
                NavineClient.LOGGER.error("Login verify thread failed", error);
                ok = false;
            }
            final boolean verified = ok;
            Runnable finish = () -> {
                try {
                    if (verified) {
                        onLoginSuccess(username);
                    } else {
                        onLoginFailed(username);
                    }
                } catch (Throwable error) {
                    NavineClient.LOGGER.error("Login finish handler failed", error);
                    ChatUtils.message("§cLogin failed. Check logs.");
                }
            };
            if (mc != null) {
                try {
                    mc.execute(finish);
                } catch (Throwable ignored) {
                    finish.run();
                }
            } else {
                finish.run();
            }
        }, "navine-login");
        worker.setDaemon(true);
        worker.start();
    }

    private void onLoginSuccess(String username) {
        String tag = AuthManager.getTagForIRC(username);

        ChatUtils.message("§a§l=== Login Successful ===");
        ChatUtils.message("§7Welcome, §f" + tag + " " + username);
        ChatUtils.message("");

        if (AuthManager.isOwner(username)) {
            ChatUtils.message("§d[OWNER] §7You have owner privileges!");
        } else if (AuthManager.isDev(username)) {
            ChatUtils.message("§b[DEV] §7You have developer privileges!");
        } else {
            ChatUtils.message("§f[NAVINE] §7You are a verified Navine user!");
        }

        ChatUtils.message("");
        ChatUtils.message("§7Auto-login is §aon§7 by default — you should not need §f.login§7 every launch.");
        ChatUtils.message("§7Press §f" + getKeyName(NavineClient.getClickGuiKey()) + "§7 to open ClickGUI");

        scheduleModuleReload();
    }

    private void onLoginFailed(String username) {
        ChatUtils.message("§c§l=== Login Failed ===");
        ChatUtils.message("§cUsername '§f" + username + "§c' is not whitelisted.");
        ChatUtils.message("§7Contact an admin to get access.");
    }

    private void scheduleModuleReload() {
        Minecraft mc = Minecraft.getInstance();
        Runnable reload = () -> {
            try {
                ConfigManager.reloadEnabledModules();
            } catch (Throwable error) {
                NavineClient.LOGGER.error("Module reload after login failed", error);
            }
        };
        if (mc != null) {
            try {
                mc.execute(() -> mc.execute(reload));
            } catch (Throwable ignored) {
                reload.run();
            }
        } else {
            reload.run();
        }
    }

    private String getKeyName(int key) {
        String name = NavineInput.getKeyName(key);
        if (name != null) {
            return name.toUpperCase();
        }
        return switch (key) {
            case NavineInput.KEY_LEFT_SHIFT -> "LSHIFT";
            case NavineInput.KEY_RIGHT_SHIFT -> "RSHIFT";
            case NavineInput.KEY_GRAVE_ACCENT -> "`";
            case NavineInput.KEY_LEFT_CONTROL -> "LCTRL";
            case NavineInput.KEY_LEFT_ALT -> "LALT";
            case NavineInput.KEY_SPACE -> "SPACE";
            case NavineInput.KEY_TAB -> "TAB";
            case NavineInput.KEY_CAPS_LOCK -> "CAPS";
            default -> key >= NavineInput.KEY_F1 && key <= NavineInput.KEY_F12
                    ? "F" + (key - NavineInput.KEY_F1 + 1) : "KEY" + key;
        };
    }
}
