package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.integration.baritone.BaritoneBridge;
import nv.navineclient.util.ChatUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaritoneCommand extends Command {
    public BaritoneCommand() {
        super(
                "baritone",
                "Built-in pathfinding commands",
                ".baritone goto <x> <z> | mine <block> [count] | follow <player> | stop",
                "bt",
                "goto",
                "path"
        );
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ChatUtils.error("Not in world.");
            return;
        }

        if (args.length == 0) {
            showHelp();
            return;
        }

        if (looksLikeCoords(args)) {
            handleGoto(args);
            return;
        }

        if (!BaritoneBridge.executeCommand(String.join(" ", args))) {
            if (args[0].equalsIgnoreCase("help")) {
                showHelp();
            }
        }
    }

    private void showHelp() {
        String prefix = CommandManager.getPrefix();
        ChatUtils.message("§b§l=== Pathfinder ===");
        ChatUtils.message("§7Status: §f" + BaritoneBridge.getStatusLabel());
        ChatUtils.message("§f" + prefix + "baritone goto <x> <z> §7- walk to coords");
        ChatUtils.message("§f" + prefix + "baritone goto <x> <y> <z>");
        ChatUtils.message("§f" + prefix + "baritone mine <block> [count] §7- find and mine blocks");
        ChatUtils.message("§f" + prefix + "baritone follow <player> §7- follow a player");
        ChatUtils.message("§f" + prefix + "baritone stop §7- stop pathfinding");
        ChatUtils.message("§f" + prefix + "baritone status §7- current task");
        ChatUtils.message("§7Aliases: §f" + prefix + "goto§7, §f" + prefix + "bt§7, §f" + prefix + "path");
    }

    private boolean looksLikeCoords(String[] args) {
        if (args.length < 2 || args.length > 3) {
            return false;
        }
        return isCoordToken(args[0]) && isCoordToken(args[1]) && (args.length == 2 || isCoordToken(args[2]));
    }

    private boolean isCoordToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith("~")) {
            return value.length() == 1 || isNumeric(value.substring(1));
        }
        return isNumeric(value);
    }

    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void handleGoto(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        try {
            int x;
            int y;
            int z;
            if (args.length == 2) {
                x = parseCoord(args[0], mc.player.getX());
                y = (int) Math.floor(mc.player.getY());
                z = parseCoord(args[1], mc.player.getZ());
            } else {
                x = parseCoord(args[0], mc.player.getX());
                y = parseCoord(args[1], mc.player.getY());
                z = parseCoord(args[2], mc.player.getZ());
            }
            BaritoneBridge.gotoPosition(x, y, z);
        } catch (NumberFormatException e) {
            ChatUtils.error("Invalid coordinates.");
        }
    }

    private int parseCoord(String value, double relativeBase) throws NumberFormatException {
        if (value.startsWith("~")) {
            if (value.length() == 1) {
                return (int) Math.floor(relativeBase);
            }
            return (int) Math.floor(relativeBase + Double.parseDouble(value.substring(1)));
        }
        return (int) Math.floor(Double.parseDouble(value));
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String option : Arrays.asList("goto", "mine", "follow", "stop", "status", "help")) {
                if (option.startsWith(input)) {
                    completions.add(option);
                }
            }
        }
        return completions;
    }
}
