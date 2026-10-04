package nv.navineclient.commands;

import net.minecraft.SharedConstants;
import nv.navineclient.NavineClient;
import nv.navineclient.util.ChatUtils;

public class VersionCommand extends Command {
    public VersionCommand() {
        super("version", "Shows client version info", ".version");
    }

    @Override
    public void onCommand(String[] args) {
        ChatUtils.message("§bNavine Client §fv" + NavineClient.VERSION);
        ChatUtils.message("§7Made by §dNavine Team");
        ChatUtils.message("§7Minecraft Version: §f" + SharedConstants.getCurrentVersion().name());
        ChatUtils.message("§7Fabric Loader: §f0.19.5");
        ChatUtils.message("§7Minecraft: §f26.3");
    }
}
