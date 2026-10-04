package nv.navineclient.commands;

import net.minecraft.core.BlockPos;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.Waypoints;
import nv.navineclient.util.ChatUtils;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;

public class WaypointsCommand extends Command {
    public WaypointsCommand() {
        super("waypoints", "Manage waypoints", ".waypoints <add|remove|list> [name]", "wp");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        Waypoints waypoints = (Waypoints) ModuleManager.getModuleByName("Waypoints");
        
        if (waypoints == null) {
            ChatUtils.message("§cWaypoints module not found!");
            return;
        }
        
        if (args.length == 0) {
            showUsage();
            return;
        }
        
        String action = args[0].toLowerCase();
        
        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    ChatUtils.message("§cUsage: .waypoints add <name>");
                    return;
                }
                if (mc.player == null) return;
                String name = args[1];
                BlockPos pos = mc.player.blockPosition();
                waypoints.addWaypoint(name, pos);
            }
            case "remove", "delete", "del" -> {
                if (args.length < 2) {
                    ChatUtils.message("§cUsage: .waypoints remove <name>");
                    return;
                }
                String name = args[1];
                waypoints.removeWaypoint(name);
            }
            case "list" -> {
                List<Waypoints.Waypoint> wps = waypoints.getWaypoints();
                if (wps.isEmpty()) {
                    ChatUtils.message("§7No waypoints saved.");
                    return;
                }
                ChatUtils.message("§b§l=== Waypoints (" + wps.size() + ") ===");
                for (Waypoints.Waypoint wp : wps) {
                    ChatUtils.message("§f" + wp.name + " §7- " + wp.pos.toShortString());
                }
            }
            case "clear" -> {
                waypoints.getWaypoints().clear();
                ChatUtils.message("§aCleared all waypoints.");
            }
            default -> showUsage();
        }
    }
    
    public void showUsage() {
        ChatUtils.message("§b=== Waypoints ===");
        ChatUtils.message("§f.waypoints add <name> §7- Add waypoint at current location");
        ChatUtils.message("§f.waypoints remove <name> §7- Remove waypoint");
        ChatUtils.message("§f.waypoints list §7- List all waypoints");
        ChatUtils.message("§f.waypoints clear §7- Clear all waypoints");
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String s : new String[]{"add", "remove", "list", "clear"}) {
                if (s.startsWith(input)) completions.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete"))) {
            Waypoints waypoints = (Waypoints) ModuleManager.getModuleByName("Waypoints");
            if (waypoints != null) {
                String input = args[1].toLowerCase();
                for (Waypoints.Waypoint wp : waypoints.getWaypoints()) {
                    if (wp.name.toLowerCase().startsWith(input)) {
                        completions.add(wp.name);
                    }
                }
            }
        }
        return completions;
    }
}
