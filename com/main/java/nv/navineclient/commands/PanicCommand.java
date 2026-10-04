package nv.navineclient.commands;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.ChatUtils;

import java.util.ArrayList;
import java.util.List;

public class PanicCommand extends Command {
    private static final List<String> panicSnapshot = new ArrayList<>();

    public PanicCommand() {
        super("panic", "Disables all active modules", ".panic [restore]", "p");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("restore")) {
            if (panicSnapshot.isEmpty()) {
                ChatUtils.message("§7No panic snapshot to restore.");
                return;
            }
            int restored = 0;
            for (String name : panicSnapshot) {
                Module module = ModuleManager.getModuleByName(name);
                if (module != null && ModuleManager.canToggleModule(module) && !module.isEnabled()) {
                    module.setEnabled(true);
                    restored++;
                }
            }
            panicSnapshot.clear();
            ChatUtils.message("§aRestored §f" + restored + " §amodules.");
            return;
        }

        panicSnapshot.clear();
        int count = 0;
        for (Module m : ModuleManager.getModules()) {
            if (m.isEnabled()) {
                panicSnapshot.add(m.getName());
                m.setEnabled(false);
                count++;
            }
        }
        ChatUtils.message("§cPanic! Disabled §f" + count + " §cmodules. Use §f.panic restore §cto re-enable.");
    }
}
