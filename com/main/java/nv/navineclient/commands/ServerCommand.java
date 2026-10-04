package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class ServerCommand extends Command {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final List<Long> tickTimes = new ArrayList<>();
    private static long lastTickTime = 0;
    
    public static void recordTick() {
        long currentTime = System.currentTimeMillis();
        if (lastTickTime > 0) {
            long tickDuration = currentTime - lastTickTime;
            tickTimes.add(tickDuration);
            
            // Keep only last 100 ticks
            if (tickTimes.size() > 100) {
                tickTimes.remove(0);
            }
        }
        lastTickTime = currentTime;
    }
    
    public ServerCommand() {
        super("server", "Shows server information, plugins, and TPS", ".server", "serverinfo", "si");
    }
    
    @Override
    public void onCommand(String[] args) {
        if (mc.player == null || mc.getConnection() == null) {
            ChatUtils.message("§cNot connected to a server!");
            return;
        }
        
        ClientPacketListener connection = mc.getConnection();
        ServerData serverInfo = mc.getCurrentServer();
        
        ChatUtils.message("§b§l=== Server Information ===");
        
        // Server Name/IP
        if (serverInfo != null) {
            ChatUtils.message("§7Server: §f" + serverInfo.name);
            ChatUtils.message("§7Address: §f" + serverInfo.ip);
        } else {
            ChatUtils.message("§7Server: §fUnknown");
            ChatUtils.message("§7Address: §fUnknown");
        }
        
        // Player Count
        if (connection.getListedOnlinePlayers() != null) {
            int onlinePlayers = connection.getListedOnlinePlayers().size();
            ChatUtils.message("§7Players Online: §f" + onlinePlayers);
        }
        
        // Server Brand (try to detect from server info)
        String serverBrand = null;
        if (serverInfo != null) {
            // Try to get server brand from various sources
            try {
                if (serverInfo.name != null) {
                    serverBrand = serverInfo.name;
                    if (serverBrand != null && !serverBrand.isEmpty() && !serverBrand.equals(serverInfo.name)) {
                        ChatUtils.message("§7Brand: §f" + serverBrand);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Ping
        var playerListEntry = connection.getPlayerInfo(mc.player.getUUID());
        if (playerListEntry != null) {
            int ping = playerListEntry.getLatency();
            String pingColor = ping < 50 ? "§a" : ping < 100 ? "§e" : ping < 200 ? "§6" : "§c";
            ChatUtils.message("§7Ping: " + pingColor + ping + "ms");
        }
        
        // TPS Calculation
        double tps = calculateTPS();
        if (tps > 0) {
            String tpsColor = tps >= 19.5 ? "§a" : tps >= 15 ? "§e" : "§c";
            ChatUtils.message("§7TPS: " + tpsColor + String.format("%.2f", tps));
        } else {
            ChatUtils.message("§7TPS: §eCalculating...");
        }
        
        // Plugins (from server brand or attempt to detect)
        List<String> plugins = detectPlugins(serverBrand);
        if (!plugins.isEmpty()) {
            ChatUtils.message("§7Plugins (§f" + plugins.size() + "§7):");
            StringBuilder pluginList = new StringBuilder("§f");
            for (int i = 0; i < plugins.size(); i++) {
                pluginList.append(plugins.get(i));
                if (i < plugins.size() - 1) {
                    pluginList.append("§7, §f");
                }
            }
            ChatUtils.message(pluginList.toString());
        } else {
            ChatUtils.message("§7Plugins: §cUnable to detect (server may not expose plugin list)");
        }
    }
    
    private double calculateTPS() {
        // Calculate average TPS from tick times
        if (tickTimes.size() >= 20) {
            long totalTime = 0;
            for (Long time : tickTimes) {
                totalTime += time;
            }
            double avgTickTime = totalTime / (double) tickTimes.size();
            double tps = 1000.0 / avgTickTime;
            return Math.min(tps, 20.0); // Cap at 20 TPS
        }
        return 0;
    }
    
    private List<String> detectPlugins(String serverBrand) {
        List<String> plugins = new ArrayList<>();
        
        if (serverBrand == null || serverBrand.isEmpty()) {
            return plugins;
        }
        
        String brandLower = serverBrand.toLowerCase();
        
        // Detect common server software
        if (brandLower.contains("paper") || brandLower.contains("purpur") || brandLower.contains("pufferfish")) {
            plugins.add("Paper");
        } else if (brandLower.contains("spigot")) {
            plugins.add("Spigot");
        } else if (brandLower.contains("bukkit")) {
            plugins.add("Bukkit");
        } else if (brandLower.contains("forge")) {
            plugins.add("Forge");
        } else if (brandLower.contains("fabric")) {
            plugins.add("Fabric");
        } else if (brandLower.contains("quilt")) {
            plugins.add("Quilt");
        }
        
        // Try to parse plugin list from brand (some servers include it)
        if (brandLower.contains("plugins:")) {
            String[] parts = serverBrand.split("plugins:", 2);
            if (parts.length > 1) {
                String pluginString = parts[1].trim();
                String[] pluginArray = pluginString.split(",");
                for (String plugin : pluginArray) {
                    String trimmed = plugin.trim();
                    if (!trimmed.isEmpty() && !plugins.contains(trimmed)) {
                        plugins.add(trimmed);
                    }
                }
            }
        }
        
        // Common plugin detection patterns
        if (brandLower.contains("worldedit")) plugins.add("WorldEdit");
        if (brandLower.contains("worldguard")) plugins.add("WorldGuard");
        if (brandLower.contains("essentials")) plugins.add("Essentials");
        if (brandLower.contains("vault")) plugins.add("Vault");
        if (brandLower.contains("luckperms")) plugins.add("LuckPerms");
        if (brandLower.contains("citizens")) plugins.add("Citizens");
        if (brandLower.contains("griefprevention")) plugins.add("GriefPrevention");
        if (brandLower.contains("plotsquared") || brandLower.contains("plotsquare")) plugins.add("PlotSquared");
        if (brandLower.contains("coreprotect")) plugins.add("CoreProtect");
        if (brandLower.contains("dynmap")) plugins.add("Dynmap");
        if (brandLower.contains("placeholderapi")) plugins.add("PlaceholderAPI");
        
        return plugins;
    }
}
