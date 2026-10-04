package nv.navineclient.commands;

import nv.navineclient.util.NavineInput;
import nv.navineclient.NavineClient;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.ChatUtils;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Displays all commands with their syntax", ".help [command|modules]", "h", "?");
    }

    @Override
    public void onCommand(String[] args) {
        String prefix = CommandManager.getPrefix();

        if (args.length == 0) {
            ChatUtils.message("§b§l=== [Navine Ghost Client] Commands ===");
            ChatUtils.message("§7Use §f" + prefix + "help <command>§7 for details");
            ChatUtils.message("");
            for (Command cmd : CommandManager.getCommands()) {
                if (!cmd.isVisibleToUser()) {
                    continue;
                }
                ChatUtils.message("§f" + prefix + cmd.getName() + " §7- " + cmd.getDescription());
            }
            ChatUtils.message("");
            ChatUtils.message("§7Auth: §f" + prefix + "login §7once — autologin keeps you signed in");
            ChatUtils.message("§7" + prefix + "logout §7| §f" + prefix + "login autologin on/off");
            if (!NavineClient.GHOST) {
                ChatUtils.message("§7IRC: §f" + prefix + "irc connect §7| §f" + prefix + "irc logs §7| §f" + prefix + "irc say <msg>");
            }
            ChatUtils.message("§7Type §f" + prefix + "help modules§7 to see all modules");
            return;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("modules")) {
            ChatUtils.message("§b§l=== All Modules (" + ModuleManager.getModules().size() + ") ===");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Module m : ModuleManager.getModules()) {
                String status = m.isEnabled() ? "§a" : "§7";
                sb.append(status).append(m.getName()).append("§f, ");
                count++;
                if (count % 5 == 0) {
                    ChatUtils.message(sb.toString());
                    sb = new StringBuilder();
                }
            }
            if (sb.length() > 0) {
                ChatUtils.message(sb.substring(0, sb.length() - 2));
            }
            ChatUtils.message("§7Green = enabled, Gray = disabled");
            return;
        }

        for (Command cmd : CommandManager.getCommands()) {
            if (cmd.getName().equalsIgnoreCase(arg)) {
                if (!cmd.isVisibleToUser()) {
                    ChatUtils.message("§cUnknown command or module: " + arg);
                    return;
                }
                ChatUtils.message("§b§l=== " + cmd.getName() + " ===");
                ChatUtils.message("§7Description: §f" + cmd.getDescription());
                ChatUtils.message("§7Usage: §f" + cmd.getSyntax());
                if (cmd.getAliases().length > 0) {
                    ChatUtils.message("§7Aliases: §f" + String.join(", ", cmd.getAliases()));
                }
                return;
            }
        }

        Module module = ModuleManager.getModuleByName(arg);
        if (module != null) {
            ChatUtils.message("§b§l=== " + module.getName() + " ===");
            ChatUtils.message("§7Description: §f" + module.getDescription());
            ChatUtils.message("§7Status: " + (module.isEnabled() ? "§aEnabled" : "§cDisabled"));
            if (module.getKey() != 0) {
                String keyName = null;
                try {
                    keyName = NavineInput.getKeyName(module.getKey());
                } catch (Throwable ignored) {
                }
                ChatUtils.message("§7Keybind: §f" + (keyName != null && !keyName.isEmpty() ? keyName : "Key " + module.getKey()));
            }
            if (!module.getSettings().isEmpty()) {
                ChatUtils.message("§7Settings:");
                for (var setting : module.getSettings()) {
                    ChatUtils.message("  §f" + setting.getName() + "§7: " + setting.getValue());
                }
            }
            return;
        }

        ChatUtils.message("§cUnknown command or module: " + arg);
        ChatUtils.message("§7Use §f" + prefix + "help§7 for a list of commands");
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("modules".startsWith(input)) {
                completions.add("modules");
            }
            for (Command cmd : CommandManager.getCommands()) {
                if (!cmd.isVisibleToUser()) {
                    continue;
                }
                if (cmd.getName().toLowerCase().startsWith(input)) {
                    completions.add(cmd.getName());
                }
            }
            for (Module m : ModuleManager.getModules()) {
                if (m.getName().toLowerCase().startsWith(input)) {
                    completions.add(m.getName());
                }
            }
        }
        return completions;
    }
}
