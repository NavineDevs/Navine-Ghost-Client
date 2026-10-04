package nv.navineclient.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public class AuthManager {
    private static final String USERNAMES_URL = "https://kurohana-dev.github.io/verified/usernames.txt";
    private static final Set<String> ALL_CLIENT_USERS = new HashSet<>();
    private static final Set<String> DEV_USERS = new HashSet<>();
    private static final Set<String> OWNER_USERS = new HashSet<>(Arrays.asList("hitboyxx23", "zach"));
    private static volatile String loggedInUser = null;
    private static volatile boolean verified = false;
    private static volatile boolean failed = false;
    private static final Object fetchLock = new Object();
    
    private static final List<DiscordMessage> discordMessages = new ArrayList<>();
    private static boolean discordConnected = false;

    public static boolean verify(String username) {
        return verifyInternal(username, false);
    }

    public static boolean verifyBlocking(String username) {
        if (username == null || username.trim().isEmpty()) {
            verified = false;
            failed = true;
            loggedInUser = null;
            return false;
        }
        ensureUsersLoadedBlocking();
        return verifyInternal(username.trim(), true);
    }

    private static boolean verifyInternal(String username, boolean allowAsyncRefresh) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.isEmpty()) {
            verified = false;
            failed = true;
            loggedInUser = null;
            return false;
        }
        String lower = trimmed.toLowerCase();
        if (OWNER_USERS.contains(lower)) {
            applyVerified(trimmed);
            if (allowAsyncRefresh) {
                fetchUsersAsync();
            }
            return true;
        }
        if (ALL_CLIENT_USERS.contains(lower)) {
            applyVerified(trimmed);
            if (allowAsyncRefresh) {
                fetchUsersAsync();
            }
            return true;
        }
        verified = false;
        failed = true;
        loggedInUser = null;
        if (allowAsyncRefresh) {
            fetchUsersAsync();
        }
        return false;
    }

    private static void applyVerified(String username) {
        loggedInUser = username;
        verified = true;
        failed = false;
        Runnable persist = () -> {
            try {
                nv.navineclient.config.ConfigManager.saveSession();
            } catch (Throwable ignored) {
            }
            try {
                IRCClient.onAuthChanged();
            } catch (Throwable ignored) {
            }
        };
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null) {
            try {
                if (mc.isSameThread()) {
                    persist.run();
                } else {
                    mc.execute(persist);
                }
            } catch (Throwable ignored) {
                persist.run();
            }
        } else {
            persist.run();
        }
    }

    private static volatile boolean fetchRunning = false;

    private static void fetchUsersAsync() {
        if (fetchRunning) {
            return;
        }
        fetchRunning = true;
        Thread worker = new Thread(() -> {
            try {
                fetchUsers();
            } finally {
                fetchRunning = false;
            }
        }, "navine-auth-fetch");
        worker.setDaemon(true);
        worker.start();
    }

    public static void restoreSession(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        String trimmed = username.trim();
        String lower = trimmed.toLowerCase();
        if (OWNER_USERS.contains(lower)) {
            loggedInUser = trimmed;
            verified = true;
            failed = false;
            IRCClient.onAuthChanged();
            return;
        }
        ensureUsersLoaded();
        boolean known = ALL_CLIENT_USERS.contains(lower);
        if (!known && !ALL_CLIENT_USERS.isEmpty()) {
            return;
        }
        loggedInUser = trimmed;
        verified = true;
        failed = false;
        IRCClient.onAuthChanged();
    }

    public static void tryRestoreSavedSession() {
        if (verified) {
            return;
        }
        nv.navineclient.config.ConfigManager.restoreSavedSessionIfNeeded();
    }

    public static void logout() {
        loggedInUser = null;
        verified = false;
        failed = false;
        nv.navineclient.config.ConfigManager.clearSavedSession();
        nv.navineclient.config.ConfigManager.save();
    }

    private static void ensureUsersLoadedBlocking() {
        if (!ALL_CLIENT_USERS.isEmpty()) {
            return;
        }
        synchronized (fetchLock) {
            if (!ALL_CLIENT_USERS.isEmpty()) {
                return;
            }
            fetchUsersLocked();
        }
    }

    private static void ensureUsersLoaded() {
        if (!ALL_CLIENT_USERS.isEmpty()) {
            return;
        }
        fetchUsersAsync();
    }

    public static void fetchUsers() {
        synchronized (fetchLock) {
            fetchUsersLocked();
        }
    }

    private static void fetchUsersLocked() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USERNAMES_URL))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String[] names = response.body().split("\\r?\\n");
                ALL_CLIENT_USERS.clear();
                DEV_USERS.clear();
                for (String name : names) {
                    name = name.trim();
                    if (!name.isEmpty()) {
                        if (name.startsWith("#")) {
                            String actualName = name.substring(1).trim().toLowerCase();
                            ALL_CLIENT_USERS.add(actualName);
                            DEV_USERS.add(actualName);
                        } else {
                            ALL_CLIENT_USERS.add(name.toLowerCase());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void enableGhostSession() {
    }

    public static boolean isVerified() {
        if ("true".equals(System.getProperty("ghost.openAccess"))) {
            return true;
        }
        return verified;
    }

    public static boolean hasFailed() {
        return failed;
    }

    public static void resetFailure() {
        failed = false;
    }

    public static String getLoggedInUser() {
        return loggedInUser;
    }

    public static String getMinecraftUsername() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.getUser() != null && mc.getUser().getName() != null && !mc.getUser().getName().isEmpty()) {
            return mc.getUser().getName();
        }
        if (loggedInUser != null && !loggedInUser.isEmpty()) {
            return loggedInUser;
        }
        return "Player";
    }

    public static String getIrcRankUser() {
        if (isVerified() && loggedInUser != null && !loggedInUser.isEmpty()) {
            return loggedInUser;
        }
        return getMinecraftUsername();
    }

    public static String getMinecraftUsername(String username) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            return mc.getUser().getName();
        }
        return username;
    }

    public static boolean isLocalPlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (loggedInUser != null && name.equalsIgnoreCase(loggedInUser)) {
            return true;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        if (mc.getUser() != null && name.equalsIgnoreCase(mc.getUser().getName())) {
            return true;
        }
        if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) {
            return true;
        }
        if (mc.getConnection() != null && mc.player != null) {
            net.minecraft.client.multiplayer.PlayerInfo self = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (self != null && self.getProfile() != null && self.getProfile().name() != null
                    && name.equalsIgnoreCase(self.getProfile().name())) {
                return true;
            }
        }
        return false;
    }

    public static String getActiveRankUser() {
        if (isVerified() && loggedInUser != null && !loggedInUser.isEmpty()) {
            return loggedInUser.toLowerCase();
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            return mc.getUser().getName().toLowerCase();
        }
        return "";
    }

    public static String getTagUsername(String mcUsername) {
        if (isVerified() && loggedInUser != null && !loggedInUser.isEmpty()) {
            if (mcUsername == null || mcUsername.isEmpty() || isLocalPlayerName(mcUsername)) {
                return loggedInUser;
            }
        }
        return mcUsername;
    }

    public static String getTag(String username) {
        return getTagForPlayer(username);
    }

    public static String getTagForIRC(String username) {
        String rankUser = resolveTagRankUser(username);
        if (rankUser == null || rankUser.isEmpty()) {
            return "";
        }
        if (OWNER_USERS.contains(rankUser)) {
            return "[Navine Owner]";
        }
        if (DEV_USERS.contains(rankUser)) {
            return "[Navine Dev]";
        }
        return "[Navine]";
    }
    
    public static String getTagColor(String username) {
        String rankUser = resolveTagRankUser(username);
        if (rankUser == null || rankUser.isEmpty()) {
            return "§7";
        }
        if (OWNER_USERS.contains(rankUser)) {
            return "§9";
        }
        if (DEV_USERS.contains(rankUser)) {
            return "§1";
        }
        return "§b";
    }

    private static String resolveTagRankUser(String mcUsername) {
        if (mcUsername == null || mcUsername.isEmpty()) {
            return null;
        }
        if (isLocalPlayerName(mcUsername)) {
            if (!isVerified() || loggedInUser == null || loggedInUser.isEmpty()) {
                return null;
            }
            return loggedInUser.toLowerCase();
        }
        if (!isNavineUser(mcUsername)) {
            return null;
        }
        return mcUsername.toLowerCase();
    }

    private static String buildColoredTag(String rankUser) {
        if (OWNER_USERS.contains(rankUser)) {
            return "§8[§9Navine Owner§8] ";
        }
        if (DEV_USERS.contains(rankUser)) {
            return "§8[§1Navine Dev§8] ";
        }
        return "§8[§bNavine§8] ";
    }

    public static String getTagForPlayer(String mcUsername) {
        String rankUser = resolveTagRankUser(mcUsername);
        if (rankUser == null || rankUser.isEmpty()) {
            return "";
        }
        return buildColoredTag(rankUser);
    }

    public static String getTagForIRCPlayer(String mcUsername) {
        return getTagForIRC(mcUsername);
    }

    public static String getTagColorForPlayer(String mcUsername) {
        return getTagColor(mcUsername);
    }

    public static boolean isOwner() {
        if (!verified || loggedInUser == null || loggedInUser.isEmpty()) {
            return false;
        }
        return OWNER_USERS.contains(loggedInUser.toLowerCase());
    }
    
    public static boolean isOwner(String username) {
        return OWNER_USERS.contains(username.toLowerCase());
    }

    public static String getOwnerRankUser() {
        if (!verified || loggedInUser == null || loggedInUser.isEmpty()) {
            return "";
        }
        return loggedInUser;
    }

    public static boolean canUseOwnerCommands() {
        return isOwner();
    }

    public static boolean isDev() {
        if (!verified || loggedInUser == null || loggedInUser.isEmpty()) {
            return false;
        }
        String lower = loggedInUser.toLowerCase();
        return DEV_USERS.contains(lower) || OWNER_USERS.contains(lower);
    }
    
    public static boolean isDev(String username) {
        return DEV_USERS.contains(username.toLowerCase()) || isOwner(username);
    }

    public static boolean canUseDevCommands() {
        return isDev();
    }
    
    public static boolean isNavineUser(String username) {
        return ALL_CLIENT_USERS.contains(username.toLowerCase()) || isOwner(username);
    }
    
    public static boolean isDiscordConnected() {
        return discordConnected;
    }
    
    public static void setDiscordConnected(boolean connected) {
        discordConnected = connected;
    }
    
    public static void addDiscordMessage(String sender, String message, boolean isDM) {
        addDiscordMessage(sender, message, isDM, "");
    }

    public static void addDiscordMessage(String sender, String message, boolean isDM, String recipient) {
        discordMessages.add(new DiscordMessage(sender, message, isDM, recipient, System.currentTimeMillis()));
        if (discordMessages.size() > 100) {
            discordMessages.remove(0);
        }
    }
    
    public static List<DiscordMessage> getDiscordMessages() {
        return new ArrayList<>(discordMessages);
    }
    
    public static class DiscordMessage {
        public final String sender;
        public final String message;
        public final boolean isDM;
        public final String recipient;
        public final long timestamp;
        
        public DiscordMessage(String sender, String message, boolean isDM, long timestamp) {
            this(sender, message, isDM, "", timestamp);
        }

        public DiscordMessage(String sender, String message, boolean isDM, String recipient, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.isDM = isDM;
            this.recipient = recipient != null ? recipient : "";
            this.timestamp = timestamp;
        }
    }
}
