package nv.navineclient.integration.baritone;

import net.minecraft.client.Minecraft;
import nv.navineclient.commands.CommandManager;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.ChatUtils;
import nv.navineclient.util.NavinePathfinder;

import java.util.Arrays;
import java.util.Locale;

public final class BaritoneBridge {
    private BaritoneBridge() {
    }

    public static String getStatusLabel() {
        return NavinePathfinder.isActive() ? NavinePathfinder.getStatus() : "Idle";
    }

    public static void gotoPosition(int x, int y, int z) {
        ensurePathfinderModule();
        NavinePathfinder.gotoPosition(x, y, z);
    }

    private static void ensurePathfinderModule() {
        Module module = ModuleManager.getModuleByName("Pathfinder");
        if (module != null && !module.isEnabled()) {
            module.setEnabled(true);
        }
    }

    public static boolean executeCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String command = raw.trim();
        if (command.startsWith("#")) {
            command = command.substring(1).trim();
        }
        String[] parts = command.split("\\s+");
        String action = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ChatUtils.error("Not in world.");
            return false;
        }

        switch (action) {
            case "goto" -> {
                return handleGoto(mc, args);
            }
            case "stop", "cancel" -> {
                NavinePathfinder.stop();
                ChatUtils.success("Pathfinding stopped.");
                return true;
            }
            case "mine" -> {
                return handleMine(args);
            }
            case "follow" -> {
                return handleFollow(args);
            }
            case "status" -> {
                ChatUtils.message("§bPathfinder: §f" + getStatusLabel());
                return true;
            }
            case "help" -> {
                return false;
            }
            default -> {
                ChatUtils.error("Unknown pathfinder command: " + action);
                ChatUtils.message("§7Available: goto, mine, follow, stop, status");
                return false;
            }
        }
    }

    private static boolean handleGoto(Minecraft mc, String[] args) {
        try {
            if (args.length >= 3) {
                int x = (int) Math.floor(Double.parseDouble(args[0]));
                int y = (int) Math.floor(Double.parseDouble(args[1]));
                int z = (int) Math.floor(Double.parseDouble(args[2]));
                gotoPosition(x, y, z);
                return true;
            }
            if (args.length == 2) {
                int x = (int) Math.floor(Double.parseDouble(args[0]));
                int z = (int) Math.floor(Double.parseDouble(args[1]));
                gotoPosition(x, (int) Math.floor(mc.player.getY()), z);
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        String prefix = CommandManager.getPrefix();
        ChatUtils.error("Usage: " + prefix + "baritone goto <x> <y> <z> or " + prefix + "baritone goto <x> <z>");
        return false;
    }

    private static boolean handleMine(String[] args) {
        if (args.length == 0) {
            String prefix = CommandManager.getPrefix();
            ChatUtils.error("Usage: " + prefix + "baritone mine <block> [count]");
            return false;
        }
        int count = 0;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        ensurePathfinderModule();
        NavinePathfinder.mine(args[0], count);
        return true;
    }

    private static boolean handleFollow(String[] args) {
        if (args.length == 0) {
            String prefix = CommandManager.getPrefix();
            ChatUtils.error("Usage: " + prefix + "baritone follow <player>");
            return false;
        }
        ensurePathfinderModule();
        NavinePathfinder.follow(args[0]);
        return true;
    }
}
