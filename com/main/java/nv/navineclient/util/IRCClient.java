package nv.navineclient.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import nv.navineclient.NavineClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IRCClient {
    private static final String WS_URL = "wss://navineclient-irc.onrender.com/irc";
    private static final String HEALTH_URL = "https://navineclient-irc.onrender.com/health";
    private static final int MAX_RECONNECT_ATTEMPTS = 2;
    private static final long CONNECT_TIMEOUT_MS = 90000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static WebSocket webSocket;
    private static final AtomicBoolean connected = new AtomicBoolean(false);
    private static final AtomicBoolean connecting = new AtomicBoolean(false);
    private static volatile boolean intentionalDisconnect = false;
    private static volatile String lastUsername;
    private static volatile long connectStartedAt;
    private static String status = "Disconnected";
    private static int reconnectAttempts = 0;
    private static ScheduledExecutorService reconnectExecutor;
    private static volatile String pendingOwnerAnnounce;
    private static final List<IrcLogEntry> logEntries = new ArrayList<>();
    private static final int MAX_LOG_ENTRIES = 200;

    public enum LogType {
        SAY,
        DM,
        SYSTEM,
        JOIN,
        LEAVE,
        OWNER_ANNOUNCE,
        ERROR
    }

    public static final class IrcLogEntry {
        public final String sender;
        public final String message;
        public final LogType type;
        public final long timestamp;
        public final String recipient;

        public IrcLogEntry(String sender, String message, LogType type, long timestamp) {
            this(sender, message, type, timestamp, "");
        }

        public IrcLogEntry(String sender, String message, LogType type, long timestamp, String recipient) {
            this.sender = sender != null ? sender : "";
            this.message = message != null ? message : "";
            this.type = type;
            this.timestamp = timestamp;
            this.recipient = recipient != null ? recipient : "";
        }
    }

    private IRCClient() {
    }

    public static void connect(String username) {
        if (connected.get()) {
            return;
        }
        if (connecting.get()) {
            if (System.currentTimeMillis() - connectStartedAt > CONNECT_TIMEOUT_MS) {
                connecting.set(false);
                status = "Timed out";
            } else {
                ChatUtils.message("§e[IRC] Connection already in progress...");
                return;
            }
        }

        intentionalDisconnect = false;
        lastUsername = username;
        reconnectAttempts = 0;
        connecting.set(true);
        connectStartedAt = System.currentTimeMillis();
        status = "Connecting...";

        Minecraft.getInstance().execute(() ->
                ChatUtils.message("§7[IRC] Connecting..."));

        Thread wakeThread = new Thread(() -> {
            try {
                HttpRequest wake = HttpRequest.newBuilder()
                        .uri(URI.create(HEALTH_URL))
                        .timeout(Duration.ofSeconds(90))
                        .GET()
                        .build();
                HTTP.send(wake, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
            if (!intentionalDisconnect) {
                openSocket(username);
            } else {
                connecting.set(false);
            }
        }, "IRC-Wake");
        wakeThread.setDaemon(true);
        wakeThread.start();

        ensureReconnectExecutor();
        reconnectExecutor.schedule(() -> {
            if (connecting.get() && !connected.get() && !intentionalDisconnect) {
                connecting.set(false);
                status = "Timed out";
                Minecraft.getInstance().execute(() ->
                        ChatUtils.message("§cIRC connection timed out. Use §f.irc connect§c to try again."));
            }
        }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void openSocket(String username) {
        connecting.set(true);
        connectStartedAt = System.currentTimeMillis();
        status = "Connecting...";

        HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .buildAsync(URI.create(WS_URL), new IrcListener(username))
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        onConnectFailure(error);
                    }
                });
    }

    private static void onConnectFailure(Throwable error) {
        connected.set(false);
        connecting.set(false);
        webSocket = null;
        status = "Failed: " + (error.getMessage() != null ? error.getMessage() : "connection error");
        AuthManager.setDiscordConnected(false);
        NavineClient.LOGGER.error("IRC WebSocket failure", error);
        Minecraft.getInstance().execute(() ->
                ChatUtils.message("§cIRC connection failed: §7" + error.getMessage()));
        if (!intentionalDisconnect && lastUsername != null) {
            scheduleReconnect();
        }
    }

    private static void onSocketClosed(String reason) {
        boolean wasConnected = connected.getAndSet(false);
        connecting.set(false);
        status = "Disconnected";
        AuthManager.setDiscordConnected(false);
        webSocket = null;
        NavineClient.LOGGER.info("IRC WebSocket closed: {}", reason);
        if (wasConnected && !intentionalDisconnect && lastUsername != null) {
            scheduleReconnect();
        }
    }

    private static void scheduleReconnect() {
        if (intentionalDisconnect || lastUsername == null) {
            return;
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            status = "Reconnect failed";
            Minecraft.getInstance().execute(() ->
                    ChatUtils.message("§cIRC reconnect failed. Use §f.irc connect§c to try again."));
            return;
        }
        reconnectAttempts++;
        long delaySeconds = Math.min(30, reconnectAttempts * 3L);
        status = "Reconnecting (" + reconnectAttempts + ")";
        ensureReconnectExecutor();
        reconnectExecutor.schedule(() -> {
            if (!intentionalDisconnect && !connected.get() && !connecting.get() && lastUsername != null) {
                Minecraft.getInstance().execute(() ->
                        ChatUtils.message("§eIRC reconnecting... (attempt " + reconnectAttempts + ")"));
                openSocket(lastUsername);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private static void ensureReconnectExecutor() {
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "IRC-Reconnect");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    public static void disconnect() {
        intentionalDisconnect = true;
        reconnectAttempts = 0;
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor = null;
        }
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.sendText("{\"type\":\"disconnect\"}", true);
            } catch (Exception ignored) {
            }
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
        }
        connected.set(false);
        connecting.set(false);
        status = "Disconnected";
        AuthManager.setDiscordConnected(false);
    }

    public static boolean isConnected() {
        return connected.get() && webSocket != null;
    }

    public static String getStatus() {
        return status;
    }

    public static void sendMessage(String message) {
        if (!isConnected()) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "say");
        obj.addProperty("message", message);
        sendJson(obj.toString());
    }

    public static void sendDirectMessage(String target, String message) {
        if (!isConnected()) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "dm");
        obj.addProperty("to", target);
        obj.addProperty("message", message);
        sendJson(obj.toString());
    }

    public static void sendOwnerAnnounce(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (!AuthManager.canUseOwnerCommands()) {
            ChatUtils.message("§cOwner login required! Use §f.login <owner_username>");
            return;
        }
        String rankUser = AuthManager.getOwnerRankUser();
        if (!isConnected()) {
            pendingOwnerAnnounce = message;
            connect(AuthManager.getMinecraftUsername());
            return;
        }
        syncSessionRank();
        pendingOwnerAnnounce = null;
        String sender = AuthManager.getMinecraftUsername();
        addLogEntry(sender, message, LogType.OWNER_ANNOUNCE);
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "owner_announce");
        obj.addProperty("message", message);
        obj.addProperty("rank_user", rankUser);
        sendJson(obj.toString());
    }

    public static void onAuthChanged() {
        try {
            if (!isConnected()) {
                return;
            }
            syncSessionRank();
        } catch (Throwable ignored) {
        }
    }

    private static void syncSessionRank() {
        if (!isConnected() || !AuthManager.isVerified()) {
            return;
        }
        String rankUser = AuthManager.getLoggedInUser();
        if (rankUser == null || rankUser.isEmpty()) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "session_update");
        obj.addProperty("rank_user", rankUser);
        obj.addProperty("tag", AuthManager.getTagForIRC(rankUser));
        sendJson(obj.toString());
    }

    public static void addLogEntry(String sender, String message, LogType type) {
        addLogEntry(sender, message, type, System.currentTimeMillis());
    }

    public static void addLogEntry(String sender, String message, LogType type, String recipient) {
        addLogEntry(sender, message, type, System.currentTimeMillis(), recipient);
    }

    public static List<IrcLogEntry> getLogEntries() {
        String localUser = localUsername();
        if (logEntries.isEmpty()) {
            for (AuthManager.DiscordMessage msg : AuthManager.getDiscordMessages()) {
                LogType type = msg.isDM ? LogType.DM : LogType.SAY;
                if (type == LogType.DM && !isDmVisibleToUser(msg.sender, msg.recipient, localUser)) {
                    continue;
                }
                logEntries.add(new IrcLogEntry(
                        msg.sender,
                        msg.message,
                        type,
                        msg.timestamp,
                        msg.recipient));
            }
        }
        List<IrcLogEntry> visible = new ArrayList<>();
        for (IrcLogEntry entry : logEntries) {
            if (entry.type == LogType.DM && !isDmVisibleToUser(entry.sender, entry.recipient, localUser)) {
                continue;
            }
            visible.add(entry);
        }
        return visible;
    }

    public static void requestLogs() {
        if (!isConnected()) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "get_logs");
        sendJson(obj.toString());
    }

    public static void showStatusMessage(String message) {
        ChatUtils.message("§7[IRC] §f" + message);
    }

    private static String buildConnect(String username) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "connect");
        obj.addProperty("user", username);
        String rankUser = AuthManager.isVerified() && AuthManager.getLoggedInUser() != null
                ? AuthManager.getLoggedInUser()
                : username;
        obj.addProperty("rank_user", rankUser);
        obj.addProperty("tag", AuthManager.getTagForIRC(rankUser));
        return obj.toString();
    }

    private static void sendJson(String json) {
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.sendText(json, true);
        }
    }

    private static void handleIncoming(String text) {
        try {
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString().toLowerCase() : "";

            switch (type) {
                case "hello" -> {
                    String msg = readText(obj, "message");
                    addLogEntry("IRC", msg, LogType.SYSTEM);
                }
                case "connected" -> {
                    String user = readText(obj, "user");
                    String msg = user.isEmpty() ? "Connected" : "Connected as " + user;
                    addLogEntry("IRC", msg, LogType.SYSTEM);
                }
                case "error" -> {
                    String msg = readText(obj, "error", "message", "content");
                    addLogEntry("IRC", msg, LogType.ERROR);
                    Minecraft.getInstance().execute(() ->
                            ChatUtils.message("§c[IRC] §7" + msg));
                }
                case "system", "disconnected" -> {
                    String msg = readText(obj, "message", "reason", "content");
                    addLogEntry("IRC", msg, LogType.SYSTEM);
                    Minecraft.getInstance().execute(() ->
                            ChatUtils.message("§7[IRC] §8" + msg));
                }
                case "join" -> {
                    String user = readText(obj, "user", "from");
                    if (!user.isEmpty()) {
                        addLogEntry(user, user + " joined", LogType.JOIN);
                        Minecraft.getInstance().execute(() ->
                                ChatUtils.message("§7[IRC] §8" + user + " joined"));
                    }
                }
                case "leave" -> {
                    String user = readText(obj, "user", "from");
                    if (!user.isEmpty()) {
                        addLogEntry(user, user + " left", LogType.LEAVE);
                        Minecraft.getInstance().execute(() ->
                                ChatUtils.message("§7[IRC] §8" + user + " left"));
                    }
                }
                case "owner_announce" -> {
                    String sender = readText(obj, "from", "username", "sender");
                    String content = readText(obj, "message", "content");
                    String localMc = Minecraft.getInstance().getUser().getName();
                    if (sender.equalsIgnoreCase(localMc)) {
                        return;
                    }
                    addLogEntry(sender, content, LogType.OWNER_ANNOUNCE);
                    Minecraft.getInstance().execute(() ->
                            ChatUtils.message("§5§l[OWNER ANNOUNCE] §d" + sender + "§f: §e" + content));
                }
                case "say" -> {
                    String sender = readText(obj, "from", "username", "sender");
                    String content = readText(obj, "message", "content");
                    String tag = readText(obj, "tag");
                    String localUser = Minecraft.getInstance().getUser().getName();
                    if (!sender.equalsIgnoreCase(localUser)) {
                        addLogEntry(sender, content, LogType.SAY);
                        Minecraft.getInstance().execute(() -> displayMessage(sender, content, tag, false));
                    }
                }
                case "dm" -> {
                    String sender = readText(obj, "from", "username", "sender");
                    String recipient = readText(obj, "to", "target", "recipient");
                    String content = readText(obj, "message", "content");
                    String tag = readText(obj, "tag");
                    String localUser = localUsername();
                    if (!isDmVisibleToUser(sender, recipient, localUser)) {
                        return;
                    }
                    if (sender.equalsIgnoreCase(localUser)) {
                        return;
                    }
                    long timestamp = obj.has("time") && !obj.get("time").isJsonNull()
                            ? obj.get("time").getAsLong()
                            : System.currentTimeMillis();
                    addLogEntry(sender, content, LogType.DM, timestamp, recipient);
                    Minecraft.getInstance().execute(() -> displayMessage(sender, content, tag, true));
                }
                case "logs" -> handleLogsPayload(obj);
                default -> {
                    String sender = readText(obj, "from", "username", "sender");
                    String recipient = readText(obj, "to", "target", "recipient");
                    String content = readText(obj, "message", "content");
                    if (!sender.isEmpty() && !content.isEmpty()) {
                        boolean isDm = type.equals("dm");
                        String localUser = localUsername();
                        if (isDm) {
                            if (!isDmVisibleToUser(sender, recipient, localUser)) {
                                return;
                            }
                            if (sender.equalsIgnoreCase(localUser)) {
                                return;
                            }
                            long timestamp = obj.has("time") && !obj.get("time").isJsonNull()
                                    ? obj.get("time").getAsLong()
                                    : System.currentTimeMillis();
                            addLogEntry(sender, content, LogType.DM, timestamp, recipient);
                            Minecraft.getInstance().execute(() -> displayMessage(sender, content, readText(obj, "tag"), true));
                            return;
                        }
                        if (sender.equalsIgnoreCase(localUser)) {
                            return;
                        }
                        addLogEntry(sender, content, LogType.SAY, System.currentTimeMillis(), recipient);
                        Minecraft.getInstance().execute(() -> displayMessage(sender, content, readText(obj, "tag"), false));
                    }
                }
            }
        } catch (Exception e) {
            addLogEntry("IRC", text, LogType.SYSTEM);
            Minecraft.getInstance().execute(() ->
                    ChatUtils.message("§7[IRC] §f" + text));
        }
    }

    private static void handleLogsPayload(JsonObject obj) {
        if (!obj.has("entries") || !obj.get("entries").isJsonArray()) {
            return;
        }
        var array = obj.getAsJsonArray("entries");
        if (array.isEmpty()) {
            return;
        }
        logEntries.clear();
        String localUser = localUsername();
        for (var element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String type = readText(entry, "type").toLowerCase();
            String sender = readText(entry, "from", "username", "sender", "user");
            String recipient = readText(entry, "to", "target", "recipient");
            String content = readText(entry, "message", "content");
            long timestamp = entry.has("time") && !entry.get("time").isJsonNull()
                    ? entry.get("time").getAsLong()
                    : System.currentTimeMillis();
            LogType logType = mapLogType(type);
            if (logType == LogType.DM && !isDmVisibleToUser(sender, recipient, localUser)) {
                continue;
            }
            if (logType == LogType.SYSTEM && content.isEmpty()) {
                continue;
            }
            if (sender.isEmpty() && logType != LogType.SYSTEM && logType != LogType.ERROR) {
                sender = "IRC";
            }
            addLogEntry(sender, content.isEmpty() ? type : content, logType, timestamp, recipient);
        }
    }

    private static LogType mapLogType(String type) {
        return switch (type) {
            case "say" -> LogType.SAY;
            case "dm" -> LogType.DM;
            case "join" -> LogType.JOIN;
            case "leave" -> LogType.LEAVE;
            case "owner_announce" -> LogType.OWNER_ANNOUNCE;
            case "error" -> LogType.ERROR;
            default -> LogType.SYSTEM;
        };
    }

    private static void addLogEntry(String sender, String message, LogType type, long timestamp) {
        addLogEntry(sender, message, type, timestamp, "");
    }

    private static void addLogEntry(String sender, String message, LogType type, long timestamp, String recipient) {
        if (type == LogType.DM) {
            String localUser = localUsername();
            if (!isDmVisibleToUser(sender, recipient, localUser)) {
                return;
            }
            logEntries.add(new IrcLogEntry(sender, message, type, timestamp, recipient));
            if (logEntries.size() > MAX_LOG_ENTRIES) {
                logEntries.remove(0);
            }
            AuthManager.addDiscordMessage(sender, message, true, recipient);
            return;
        }
        logEntries.add(new IrcLogEntry(sender, message, type, timestamp, recipient));
        if (logEntries.size() > MAX_LOG_ENTRIES) {
            logEntries.remove(0);
        }
        if (type == LogType.SAY) {
            AuthManager.addDiscordMessage(sender, message, false);
        }
    }

    private static String localUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) {
            return lastUsername != null ? lastUsername : "";
        }
        return mc.getUser().getName();
    }

    private static boolean isDmVisibleToUser(String sender, String recipient, String localUser) {
        if (localUser == null || localUser.isEmpty()) {
            return false;
        }
        if (sender != null && sender.equalsIgnoreCase(localUser)) {
            return true;
        }
        return recipient != null && !recipient.isEmpty() && recipient.equalsIgnoreCase(localUser);
    }

    private static String readText(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        }
        return "";
    }

    private static void displayMessage(String sender, String content, String tagOverride, boolean isDm) {
        String tag = tagOverride != null && !tagOverride.isEmpty()
                ? tagOverride
                : AuthManager.getTagForIRC(sender);
        String color = AuthManager.getTagColor(sender);
        String prefix;
        if (tag == null || tag.isEmpty()) {
            if (isDm) {
                prefix = "§d[DM] §f" + sender + " §7→ You: ";
            } else {
                prefix = "§f" + sender + "§7: ";
            }
        } else {
            String tagDisplay = tag.replace("[", "").replace("]", "");
            if (isDm) {
                prefix = "§d[DM] §8[" + color + tagDisplay + "§8] §f" + sender + " §7→ You: ";
            } else {
                prefix = "§8[" + color + tagDisplay + "§8] §f" + sender + "§7: ";
            }
        }
        ChatUtils.message(prefix + content);
    }

    private static final class IrcListener implements WebSocket.Listener {
        private final String username;
        private final StringBuilder textBuffer = new StringBuilder();

        private IrcListener(String username) {
            this.username = username;
        }

        @Override
        public void onOpen(WebSocket socket) {
            webSocket = socket;
            connected.set(true);
            connecting.set(false);
            reconnectAttempts = 0;
            status = "Connected";
            AuthManager.setDiscordConnected(true);
            NavineClient.LOGGER.info("IRC WebSocket connected");
            socket.request(1);
            socket.sendText(buildConnect(username), true);
            final String connectedAs = username;
            Minecraft.getInstance().execute(() -> {
                String tag = AuthManager.getTag(AuthManager.getIrcRankUser());
                if (tag.isEmpty()) {
                    ChatUtils.message("§a[IRC] Connected as §f" + connectedAs);
                } else {
                    ChatUtils.message("§a[IRC] Connected as " + tag + "§f" + connectedAs);
                }
                syncSessionRank();
                if (pendingOwnerAnnounce != null) {
                    String announce = pendingOwnerAnnounce;
                    pendingOwnerAnnounce = null;
                    sendOwnerAnnounce(announce);
                }
                requestLogs();
            });
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                handleIncoming(textBuffer.toString());
                textBuffer.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            onSocketClosed(reason != null ? reason : "");
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            onConnectFailure(error);
        }
    }
}
