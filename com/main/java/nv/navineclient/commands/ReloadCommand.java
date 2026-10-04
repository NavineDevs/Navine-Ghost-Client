package nv.navineclient.commands;

import nv.navineclient.AddonManager;
import nv.navineclient.util.ChatUtils;

public class ReloadCommand extends Command {
    public ReloadCommand() {
        super("reload", "Reload addon metadata", ".reload");
    }

    @Override
    public void onCommand(String[] args) {
        AddonManager.registerCategories();
        ChatUtils.message("§aReloaded " + AddonManager.getAddons().size() + " addon(s).");
    }
}
