package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Locale;
import java.util.UUID;

public final class PingUtil {
    private PingUtil() {
    }

    public static boolean isMultiplayer(Minecraft client) {
        return client != null && client.player != null && !client.isLocalServer() && client.getConnection() != null;
    }

    public static String getServerLabel(Minecraft client) {
        if (client == null) {
            return "Unknown";
        }
        if (client.isLocalServer()) {
            return "Singleplayer";
        }
        if (client.getCurrentServer() != null && client.getCurrentServer().ip != null && !client.getCurrentServer().ip.isEmpty()) {
            return client.getCurrentServer().ip;
        }
        return "Multiplayer";
    }

    public static int getPingMs(Minecraft client) {
        if (client == null || client.player == null) {
            return -1;
        }
        if (client.isLocalServer()) {
            return -1;
        }
        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return -1;
        }

        int latency = lookupSelfLatency(connection, client);
        if (latency >= 0 && latency <= 5000) {
            return latency;
        }

        return -1;
    }

    private static int lookupSelfLatency(ClientPacketListener connection, Minecraft client) {
        PlayerInfo info = connection.getPlayerInfo(client.player.getUUID());
        if (info == null) {
            info = connection.getPlayerInfo(client.getUser().getProfileId());
        }
        if (info == null) {
            info = connection.getPlayerInfo(client.player.getName().getString());
        }
        if (info == null) {
            info = connection.getPlayerInfoIgnoreCase(client.player.getName().getString());
        }
        if (info != null) {
            return Math.max(0, info.getLatency());
        }

        String selfName = client.player.getName().getString().toLowerCase(Locale.ROOT);
        UUID selfId = client.player.getUUID();
        for (PlayerInfo entry : connection.getOnlinePlayers()) {
            if (entry == null || entry.getProfile() == null) {
                continue;
            }
            if (selfId.equals(entry.getProfile().id())) {
                return Math.max(0, entry.getLatency());
            }
            String entryName = entry.getProfile().name();
            if (entryName != null && entryName.equalsIgnoreCase(selfName)) {
                return Math.max(0, entry.getLatency());
            }
        }
        return -1;
    }

    public static String formatPingColor(int ping) {
        if (ping < 0) {
            return "§7";
        }
        if (ping < 50) {
            return "§a";
        }
        if (ping < 100) {
            return "§e";
        }
        if (ping < 200) {
            return "§6";
        }
        return "§c";
    }
}
