package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nv.navineclient.util.ChatUtils;

public class TeleportCommand extends Command {
    public TeleportCommand() {
        super("teleport", "Teleport to coordinates", ".teleport <x> <y> <z>", "tp");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (args.length < 3) {
            ChatUtils.message("Usage: .teleport <x> <y> <z>");
            return;
        }
        
        try {
            double x = Double.parseDouble(args[0]);
            double y = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);

            // Update player position
            player.setPos(x, y, z);

            // Send position update to server
            player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                x, y, z, player.getYRot(), player.getXRot(), player.onGround(), true));

            ChatUtils.message(String.format("§aTeleported to §f%.1f, %.1f, %.1f", x, y, z));
        } catch (NumberFormatException e) {
            ChatUtils.message("§cInvalid coordinates!");
        }
    }
}
