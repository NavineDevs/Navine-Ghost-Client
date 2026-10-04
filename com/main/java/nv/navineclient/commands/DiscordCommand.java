package nv.navineclient.commands;

import nv.navineclient.util.ChatUtils;

public class DiscordCommand extends Command {
    public DiscordCommand() {
        super("discord", "Shows the Navine Discord invite", ".discord", "dc");
    }

    @Override
    public void onCommand(String[] args) {
        ChatUtils.message("§b§l=== Navine Discord ===");
        ChatUtils.message("§fJoin our community: §bhttps://discord.navine.dev");
        ChatUtils.message("§7Click the link or copy it to join!");
    }
}
