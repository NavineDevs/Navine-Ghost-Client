package nv.navineclient.commands;

import nv.navineclient.NavineClient;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class BindCommand extends Command {
    public BindCommand() {
        super("bind", "Bind a module to a key", ".bind <module|clickgui> <key>", "b");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2) {
            ChatUtils.message("§cUsage: " + getSyntax());
            return;
        }
        
        String target = args[0].toLowerCase();
        String keyName = args[1].toUpperCase();
        
        // Handle clickgui rebind
        if (target.equals("clickgui")) {
            int key = parseKey(keyName);
            if (key != -1) {
                NavineClient.setClickGuiKey(key);
                ChatUtils.message("§aClickGUI keybind set to §b" + keyName);
            } else {
                ChatUtils.message("§cInvalid key! Use single letters (A-Z), SHIFT, CTRL, ALT, TAB, SPACE, or F1-F12");
            }
            return;
        }
        
        // Handle module bind (existing functionality)
        Module m = ModuleManager.getModuleByName(target);
        if (m == null) {
            ChatUtils.message("§cModule not found!");
            return;
        }
        
        if (keyName.equals("NONE")) {
            m.setKey(0);
            ChatUtils.message("§aUnbound §f" + m.getName());
            return;
        }
        
        int key = parseKey(keyName);
        if (key != -1) {
            m.setKey(key);
            ChatUtils.message("§aBound §f" + m.getName() + " §7to §b" + keyName);
        } else {
            ChatUtils.message("§cInvalid key! Use single letters (A-Z), SHIFT, CTRL, ALT, TAB, SPACE, or F1-F12");
        }
    }
    
    private int parseKey(String keyName) {
        try {
            if (keyName.length() == 1) {
                return keyName.charAt(0);
            } else if (keyName.equals("SHIFT")) { return 344; } // Default to right shift
            else if (keyName.equals("RSHIFT")) { return 344; }
            else if (keyName.equals("LSHIFT")) { return 340; }
            else if (keyName.equals("CTRL") || keyName.equals("LCTRL")) { return 341; }
            else if (keyName.equals("RCTRL")) { return 345; }
            else if (keyName.equals("ALT") || keyName.equals("LALT")) { return 342; }
            else if (keyName.equals("RALT")) { return 346; }
            else if (keyName.equals("SPACE")) { return 32; }
            else if (keyName.equals("TAB")) { return 258; }
            else if (keyName.startsWith("F") && keyName.length() <= 3) {
                int fNum = Integer.parseInt(keyName.substring(1));
                if (fNum >= 1 && fNum <= 25) return 289 + fNum;
            }
        } catch (Exception e) {}
        return -1;
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            // Add clickgui as an option
            if ("clickgui".startsWith(input)) {
                completions.add("clickgui");
            }
            // Add modules
            for (Module m : ModuleManager.getModules()) {
                if (m.getName().toLowerCase().startsWith(input)) {
                    completions.add(m.getName());
                }
            }
        }
        return completions;
    }
}
