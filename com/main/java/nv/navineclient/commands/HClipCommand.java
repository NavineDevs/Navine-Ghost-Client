package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;

public class HClipCommand extends Command {
    public HClipCommand() {
        super("hclip", "Horizontal clip", ".hclip <distance>");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        if (args.length == 0) {
            ChatUtils.message("Usage: .hclip <distance>");
            return;
        }
        
        try {
            double distance = Double.parseDouble(args[0]);
            double yaw = Math.toRadians(mc.player.getYRot());
            double x = mc.player.getX() - Math.sin(yaw) * distance;
            double z = mc.player.getZ() + Math.cos(yaw) * distance;
            mc.player.setPos(x, mc.player.getY(), z);
            ChatUtils.message("Clipped " + distance + " blocks horizontally");
        } catch (NumberFormatException e) {
            ChatUtils.error("Invalid number!");
        }
    }
}
