package nv.navineclient.commands;

import nv.navineclient.config.ConfigManager;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.ChatUtils;

public class LogoutCommand extends Command {
    public LogoutCommand() {
        super("logout", "Log out of Navine and clear saved session", ".logout");
    }

    @Override
    public void onCommand(String[] args) {
        if (!AuthManager.isVerified()) {
            ChatUtils.message("§7You are not logged in.");
            return;
        }
        String user = AuthManager.getLoggedInUser();
        AuthManager.logout();
        ChatUtils.message("§aLogged out §f" + user + "§a. Use the title screen Login button or §f.login§a next launch.");
        if (ConfigManager.isAutoLoginEnabled()) {
            ChatUtils.message("§7Auto-login is on. Use §f.login autologin off§7 to require login each launch.");
        }
    }
}
