package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import nv.navineclient.mixin.PlayerInfoAccessor;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "Changes gamemode client-side", ".gamemode <survival|creative|adventure|spectator>", "gm");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        if (args.length == 0) {
            ChatUtils.message("§b=== Gamemode ===");
            ChatUtils.message("§fUsage: §7.gamemode <mode>");
            ChatUtils.message("");
            ChatUtils.message("§fModes:");
            ChatUtils.message("  §a• survival §7(s, 0)");
            ChatUtils.message("  §e• creative §7(c, 1)");
            ChatUtils.message("  §6• adventure §7(a, 2)");
            ChatUtils.message("  §b• spectator §7(sp, 3)");
            return;
        }

        String mode = args[0].toLowerCase();
        GameType gameMode = switch (mode) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> null;
        };

        if (gameMode == null) {
            ChatUtils.message("§cUnknown gamemode: " + mode);
            ChatUtils.message("§7Valid modes: survival, creative, adventure, spectator");
            return;
        }

        applyLocalGamemode(mc, gameMode);
        ChatUtils.message("§aSet gamemode to §f" + gameMode.name().toLowerCase());
    }

    static void applyLocalGamemode(Minecraft mc, GameType gameMode) {
        mc.gameMode.setLocalMode(gameMode);
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
                ((PlayerInfoAccessor) info).navine$setGameMode(gameMode);
            }
        }
        mc.player.onGameModeChanged(gameMode);
        if (gameMode == GameType.SPECTATOR) {
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().mayfly = true;
            mc.player.noPhysics = true;
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0.0));
        } else {
            mc.player.noPhysics = false;
        }
        mc.player.onUpdateAbilities();
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String mode : Arrays.asList("survival", "creative", "adventure", "spectator")) {
                if (mode.startsWith(input)) {
                    completions.add(mode);
                }
            }
        }
        return completions;
    }
}
