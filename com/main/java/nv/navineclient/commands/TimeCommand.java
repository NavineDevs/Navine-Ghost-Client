package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class TimeCommand extends Command {
    public TimeCommand() {
        super("time", "Show or set world time", ".time [set|day|night|query]", new String[]{"worldtime"});
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ChatUtils.message("§cYou must be in a world!");
            return;
        }

        if (args.length == 0) {
            showTime();
            return;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "day" -> setTime(0);
            case "night" -> setTime(18000);
            case "noon" -> setTime(6000);
            case "midnight" -> setTime(0);
            case "set" -> {
                if (args.length < 2) {
                    ChatUtils.message("§cUsage: .time set <ticks>");
                    return;
                }
                try {
                    long ticks = Long.parseLong(args[1]);
                    setTime(ticks);
                } catch (NumberFormatException e) {
                    ChatUtils.message("§cInvalid time value!");
                }
            }
            case "query" -> showTime();
            default -> ChatUtils.message("§cUnknown subcommand! Use: day, night, noon, midnight, set, or query");
        }
    }

    private void showTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long ticks = mc.level.getLevelData().getGameTime() % 24000;
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) ((ticks % 1000) * 60 / 1000);

        String period = hours >= 6 && hours < 18 ? "§6Day" : "§bNight";
        ChatUtils.message(String.format("§7World Time: %s §7(%02d:%02d) §7[Ticks: §f%d§7]",
            period, hours, minutes, ticks));

        // Describe current time
        if (ticks >= 0 && ticks < 1000) {
            ChatUtils.message("§7→ Sunrise");
        } else if (ticks >= 1000 && ticks < 6000) {
            ChatUtils.message("§7→ Morning");
        } else if (ticks >= 6000 && ticks < 12000) {
            ChatUtils.message("§7→ Noon");
        } else if (ticks >= 12000 && ticks < 18000) {
            ChatUtils.message("§7→ Afternoon");
        } else if (ticks >= 18000 && ticks < 23000) {
            ChatUtils.message("§7→ Evening");
        } else if (ticks >= 23000 && ticks < 24000) {
            ChatUtils.message("§7→ Night");
        }
    }

    private void setTime(long ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ChatUtils.message("§cYou must be in a world!");
            return;
        }

        // Client-side time change (visual only)
        try {
            // Use reflection to set world time
            java.lang.reflect.Field timeField = mc.level.getClass().getDeclaredField("timeOfDay");
            timeField.setAccessible(true);
            timeField.setLong(mc.level, ticks);
            ChatUtils.message("§aTime set to §f" + ticks + " §7(visual only)");
        } catch (Exception e) {
            // Fallback: inform user to use command
            ChatUtils.message("§7Note: Client-side time change failed.");
            ChatUtils.message("§7Use server command: §f/time set " + ticks);
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            String[] options = {"day", "night", "noon", "midnight", "set", "query"};
            for (String option : options) {
                if (option.startsWith(input)) {
                    completions.add(option);
                }
            }
        }
        return completions;
    }
}
