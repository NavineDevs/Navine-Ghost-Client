package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class SpectateCommand extends Command {
    private static Player spectatingPlayer = null;
    private static Vec3 originalPos = null;
    private static float originalYaw = 0;
    private static float originalPitch = 0;
    private static boolean isSpectating = false;
    
    public SpectateCommand() {
        super("spectate", "Spectate a player's POV", ".spectate [player] | .spectate stop");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        if (args.length == 0) {
            if (isSpectating) {
                stopSpectating(mc);
                return;
            }
            ChatUtils.message("§b=== Spectate ===");
            ChatUtils.message("§fUsage: §7.spectate <player>");
            ChatUtils.message("§fUsage: §7.spectate stop");
            ChatUtils.message("");
            ChatUtils.message("§6Nearby Players:");
            int count = 0;
            for (Player player : mc.level.players()) {
                if (player != mc.player) {
                    double dist = mc.player.distanceTo(player);
                    int health = (int) player.getHealth();
                    ChatUtils.message(String.format("  §f%s §7- %.1f blocks §c%d HP", 
                        player.getName().getString(), dist, health));
                    count++;
                }
            }
            if (count == 0) {
                ChatUtils.message("  §7No players in render distance");
            }
            return;
        }
        
        String targetName = args[0].toLowerCase();
        
        if (targetName.equals("stop") || targetName.equals("off")) {
            stopSpectating(mc);
            return;
        }
        
        Player target = null;
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.getName().getString().toLowerCase().equals(targetName)) {
                target = player;
                break;
            }
        }
        
        if (target == null) {
            for (Player player : mc.level.players()) {
                if (player != mc.player && player.getName().getString().toLowerCase().startsWith(targetName)) {
                    target = player;
                    break;
                }
            }
        }
        
        if (target == null) {
            ChatUtils.message("§cPlayer not found: " + args[0]);
            return;
        }
        
        if (!isSpectating) {
            originalPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            originalYaw = mc.player.getYRot();
            originalPitch = mc.player.getXRot();
        }
        
        spectatingPlayer = target;
        isSpectating = true;
        ChatUtils.message("§aSpectating §f" + target.getName().getString() + "§a. Type §f.spectate stop§a to return.");
    }
    
    private void stopSpectating(Minecraft mc) {
        if (!isSpectating) {
            ChatUtils.message("§cNot currently spectating anyone.");
            return;
        }
        
        if (originalPos != null && mc.player != null) {
            mc.player.setPos(originalPos.x, originalPos.y, originalPos.z);
            mc.player.setYRot(originalYaw);
            mc.player.setXRot(originalPitch);
        }
        
        isSpectating = false;
        spectatingPlayer = null;
        originalPos = null;
        ChatUtils.message("§aStopped spectating. Returned to original position.");
    }
    
    public static void tick() {
        if (!isSpectating || spectatingPlayer == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        if (!mc.level.players().contains(spectatingPlayer)) {
            ChatUtils.message("§cPlayer left render distance. Stopping spectate.");
            isSpectating = false;
            if (originalPos != null) {
                mc.player.setPos(originalPos.x, originalPos.y, originalPos.z);
                mc.player.setYRot(originalYaw);
                mc.player.setXRot(originalPitch);
            }
            spectatingPlayer = null;
            originalPos = null;
            return;
        }
        
        mc.player.setPos(spectatingPlayer.getX(), spectatingPlayer.getY(), spectatingPlayer.getZ());
        mc.player.setYRot(spectatingPlayer.getYRot());
        mc.player.setXRot(spectatingPlayer.getXRot());
        mc.player.setYHeadRot(spectatingPlayer.getYHeadRot());
    }
    
    public static boolean isSpectating() {
        return isSpectating;
    }
    
    public static Player getSpectatingPlayer() {
        return spectatingPlayer;
    }
    
    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return completions;
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("stop".startsWith(input)) {
                completions.add("stop");
            }
            for (Player player : mc.level.players()) {
                if (player != mc.player) {
                    String name = player.getName().getString();
                    if (name.toLowerCase().startsWith(input)) {
                        completions.add(name);
                    }
                }
            }
        }
        return completions;
    }
}
