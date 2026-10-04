package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class EnderChestCommand extends Command {
    public EnderChestCommand() {
        super("enderchest", "Open your enderchest", ".ec", new String[]{"ec"});
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ChatUtils.message("§cYou must be in a world!");
            return;
        }

        // Enderchest opening requires right-clicking on an enderchest block
        // This command just provides info - actual opening requires the block
        ChatUtils.message("§5EnderChest Command:");
        ChatUtils.message("§7Right-click an EnderChest block to open it.");
        ChatUtils.message("§7Or use §f/enderchest §7if you have permission.");
    }

    @Override
    public List<String> getCompletions(String[] args) {
        return new ArrayList<>();
    }
}
