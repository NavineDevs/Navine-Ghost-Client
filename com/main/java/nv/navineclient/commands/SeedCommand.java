package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import nv.navineclient.util.ChatUtils;

public class SeedCommand extends Command {
    public SeedCommand() {
        super("seed", "Shows the world seed (if available)", ".seed");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            ServerLevel overworld = mc.getSingleplayerServer().overworld();
            if (overworld != null) {
                ChatUtils.message("Seed: " + overworld.getSeed());
                return;
            }
        }
        ChatUtils.message("Seed not available on this server");
    }
}
