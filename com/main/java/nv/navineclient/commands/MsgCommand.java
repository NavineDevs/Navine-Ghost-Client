package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;

public class MsgCommand extends Command {
    public MsgCommand() {
        super("msg", "Send a private message", ".msg <player> <message>");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        if (args.length < 2) {
            ChatUtils.message("Usage: .msg <player> <message>");
            return;
        }
        
        String player = args[0];
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            message.append(args[i]).append(" ");
        }
        
        mc.player.connection.sendCommand("msg " + player + " " + message.toString().trim());
    }
}
