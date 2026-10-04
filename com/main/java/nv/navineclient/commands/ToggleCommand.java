package nv.navineclient.commands;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", "Toggles a module on or off", ".toggle <module>", "t");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            ChatUtils.message("§cUsage: " + getSyntax());
            return;
        }
        Module m = ModuleManager.getModuleByName(args[0]);
        if (m != null) {
            if (!ModuleManager.canToggleModule(m)) {
                ChatUtils.message("§cLogin required to toggle modules.");
                return;
            }
            m.toggle();
            String status = m.isEnabled() ? "§aenabled" : "§cdisabled";
            ChatUtils.message("§f" + m.getName() + " §7is now " + status);
        } else {
            ChatUtils.message("§cModule not found: " + args[0]);
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (Module m : ModuleManager.getModules()) {
                if (m.getName().toLowerCase().startsWith(input)) {
                    completions.add(m.getName());
                }
            }
        }
        return completions;
    }
}
