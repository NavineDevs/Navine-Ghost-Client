package nv.navineclient.commands;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.world.DevMod;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.ChatUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DevCommand extends Command {
    private static final List<String> SUBCOMMANDS = Arrays.asList("status", "packetlog", "timings");

    public DevCommand() {
        super("dev", "Developer commands", ".dev <status|packetlog|timings>");
    }

    @Override
    public boolean isVisibleToUser() {
        return AuthManager.canUseDevCommands();
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        }
        return completions;
    }

    @Override
    public void onCommand(String[] args) {
        if (!AuthManager.canUseDevCommands()) {
            ChatUtils.error("Developer access required.");
            return;
        }
        if (args.length == 0) {
            showHelp();
            return;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> {
                ChatUtils.message("§b§l=== Dev Status ===");
                ChatUtils.message("§7Logged in: §f" + AuthManager.getLoggedInUser());
                ChatUtils.message("§7DevMod: " + (DevMod.isDeveloper() ? "§aactive" : "§7inactive"));
            }
            case "packetlog" -> {
                Module mod = ModuleManager.getModuleByName("DevMod");
                if (mod instanceof DevMod devMod) {
                    devMod.toggle();
                    ChatUtils.message("§7Packet log: " + (devMod.isEnabled() ? "§aon" : "§coff"));
                }
            }
            case "timings" -> ChatUtils.message("§7Use DevMod module for render timings overlay.");
            default -> showHelp();
        }
    }

    private void showHelp() {
        String prefix = CommandManager.getPrefix();
        ChatUtils.message("§b§l=== Dev Commands ===");
        ChatUtils.message("§f" + prefix + "dev status §7- Show dev status");
        ChatUtils.message("§f" + prefix + "dev packetlog §7- Toggle packet logging");
        ChatUtils.message("§f" + prefix + "dev timings §7- Render timing info");
    }
}
