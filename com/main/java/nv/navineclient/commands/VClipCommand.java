package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;

public class VClipCommand extends Command {
    public VClipCommand() {
        super("vclip", "Vertical clip", ".vclip <distance>");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        if (args.length == 0) {
            ChatUtils.message("Usage: .vclip <distance>");
            return;
        }
        
        try {
            double distance = Double.parseDouble(args[0]);
            mc.player.setPos(mc.player.getX(), mc.player.getY() + distance, mc.player.getZ());
            ChatUtils.message("Clipped " + distance + " blocks vertically");
        } catch (NumberFormatException e) {
            ChatUtils.error("Invalid number!");
        }
    }
}
