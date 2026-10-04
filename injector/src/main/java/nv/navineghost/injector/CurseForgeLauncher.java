package nv.navineghost.injector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CurseForgeLauncher {
    private static final Pattern PORT_PATTERN = Pattern.compile("--port=(\\d+)");
    private static final List<String> ORIGIN_CANDIDATES = List.of(
            "https://www.curseforge.com",
            "https://curseforge.com",
            "app://.",
            "null"
    );

    private CurseForgeLauncher() {
    }

    public static void launch(Path instance) throws Exception {
        if (LauncherHelper.launchInstance(instance)) {
            return;
        }
        launchViaAgent(instance, "");
    }

    public static void launch(Path instance, Path agentJar, Path payload) throws Exception {
        String extraArgs = buildJavaArgs(agentJar, payload);
        if (!extraArgs.isBlank()) {
            try {
                launchViaAgent(instance, extraArgs);
                System.out.println("Sent launch request with injection args to CurseForge.");
                return;
            } catch (Exception error) {
                System.out.println("Agent launch with injection args failed: " + error.getMessage());
            }
            CurseForgeArgsPatcher.install(instance, agentJar, payload);
            LauncherHelper.waitForAgent(30_000L);
        }
        if (LauncherHelper.launchInstance(instance)) {
            return;
        }
        if (!extraArgs.isBlank()) {
            launchViaAgent(instance, extraArgs);
            System.out.println("Sent launch request with injection args to CurseForge.");
            return;
        }
        throw new IOException("Could not launch instance through CurseForge.");
    }

    public static boolean isAgentAvailable() {
        try {
            return findAgentPort().isPresent();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void launchViaAgent(Path instance, String extraArgs) throws Exception {
        String guid = readInstanceGuid(instance);
        int port = findAgentPort().orElseThrow(() -> new IOException("CurseForge agent is not running. Open CurseForge first."));
        String message = buildLaunchMessage(guid, extraArgs);
        Exception lastError = null;
        for (String origin : ORIGIN_CANDIDATES) {
            try {
                sendLaunchMessage(port, message, origin);
                return;
            } catch (Exception error) {
                lastError = error;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Could not reach CurseForge agent.");
    }

    private static String readInstanceGuid(Path instance) throws IOException {
        Path json = instance.resolve("minecraftinstance.json");
        if (!Files.isRegularFile(json)) {
            throw new IOException("minecraftinstance.json not found in " + instance);
        }
        String content = Files.readString(json);
        int index = content.indexOf("\"guid\":\"");
        if (index < 0) {
            throw new IOException("Instance guid not found in minecraftinstance.json");
        }
        int start = index + 8;
        int end = content.indexOf('"', start);
        if (end < 0) {
            throw new IOException("Malformed instance guid in minecraftinstance.json");
        }
        return content.substring(start, end);
    }

    private static Optional<Integer> findAgentPort() throws IOException {
        Optional<Integer> port = findWindowsAgentPortPowerShell();
        if (port.isPresent()) {
            return port;
        }
        return findWindowsAgentPortWmic();
    }

    private static Optional<Integer> findWindowsAgentPortPowerShell() throws IOException {
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "$agent = Get-CimInstance Win32_Process -Filter \"Name='Curse.Agent.Host.exe'\" -ErrorAction SilentlyContinue | Select-Object -First 1; if ($agent -and $agent.CommandLine -match '--port=(\\d+)') { $Matches[1] }"
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(5, TimeUnit.SECONDS);
            if (output.matches("\\d+")) {
                return Optional.of(Integer.parseInt(output));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private static Optional<Integer> findWindowsAgentPortWmic() throws IOException {
        Process process = new ProcessBuilder(
                "wmic",
                "process",
                "where",
                "name='Curse.Agent.Host.exe'",
                "get",
                "CommandLine",
                "/FORMAT:LIST"
        ).redirectErrorStream(true).start();
        try {
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(5, TimeUnit.SECONDS);
            Matcher matcher = PORT_PATTERN.matcher(output);
            if (matcher.find()) {
                return Optional.of(Integer.parseInt(matcher.group(1)));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private static String buildJavaArgs(Path agentJar, Path payload) {
        if (agentJar == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("-javaagent:").append(agentJar.toAbsolutePath().normalize());
        if (payload != null) {
            builder.append(" -Dfabric.addMods=").append(payload.toAbsolutePath().normalize());
        }
        return builder.toString();
    }

    private static String buildLaunchMessage(String guid, String extraArgs) {
        return "{"
                + "\"args\":[{"
                + "\"MinecraftInstanceGuid\":\"" + guid + "\","
                + "\"ResolutionWidth\":1024,"
                + "\"ResolutionHeight\":768,"
                + "\"LauncherVisibility\":\"Close\","
                + "\"LauncherType\":\"Classic\","
                + "\"AdditionalJavaArguments\":\"" + escapeJson(extraArgs) + "\""
                + "}],"
                + "\"type\":\"method\","
                + "\"name\":\"minecraftTaskLaunchInstance\""
                + "}";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sendLaunchMessage(int port, String message, String origin) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("ws://127.0.0.1:" + port + "/CurseForge");
        CompletableFuture<Void> done = new CompletableFuture<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                done.complete(null);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                done.completeExceptionally(error);
            }
        };

        WebSocket.Builder builder = client.newWebSocketBuilder();
        if (origin != null && !origin.isBlank()) {
            builder.header("Origin", origin);
        }
        WebSocket socket = builder.buildAsync(uri, listener).get(10, TimeUnit.SECONDS);
        socket.sendText(message, true).get(10, TimeUnit.SECONDS);
        done.get(20, TimeUnit.SECONDS);
    }
}
