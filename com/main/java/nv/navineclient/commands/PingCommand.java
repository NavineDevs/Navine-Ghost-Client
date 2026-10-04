package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;
import nv.navineclient.util.PingUtil;

public class PingCommand extends Command {
    public PingCommand() {
        super("ping", "Shows your ping to the server", ".ping");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ChatUtils.message("§cNot in a world!");
            return;
        }

        String server = PingUtil.getServerLabel(mc);
        ChatUtils.message("§7Server: §f" + server);

        if (!PingUtil.isMultiplayer(mc)) {
            ChatUtils.message("§7Server ping: §7N/A");
            ChatUtils.message("§7Your ping: §7N/A");
            return;
        }

        int ping = PingUtil.getPingMs(mc);
        if (ping < 0) {
            ChatUtils.message("§cCould not retrieve ping.");
            return;
        }

        String color = PingUtil.formatPingColor(ping);
        ChatUtils.message("§7Server ping: " + color + ping + "ms");
        ChatUtils.message("§7Ping: " + color + ping + "ms");
    }
}
