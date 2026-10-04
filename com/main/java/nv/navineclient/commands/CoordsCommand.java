package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords", "Copies your coordinates to clipboard", ".coords");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String coords = String.format("%d %d %d", 
                (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
            mc.keyboardHandler.setClipboard(coords);
            ChatUtils.message("Copied: " + coords);
        }
    }
}
