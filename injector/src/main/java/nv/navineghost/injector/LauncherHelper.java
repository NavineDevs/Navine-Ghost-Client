package nv.navineghost.injector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LauncherHelper {
    private static final int MINECRAFT_GAME_ID = 432;

    private LauncherHelper() {
    }

    public static void ensureCurseForgeOpen() throws IOException, InterruptedException {
        if (ProcessWatcher.isMinecraftRunning()) {
            return;
        }
        if (isCurseForgeRunning()) {
            if (waitForAgent(5_000L)) {
                System.out.println("CurseForge is already open.");
                return;
            }
            System.out.println("CurseForge agent is not responding. Restarting CurseForge...");
            restartCurseForge();
            return;
        }
        Path curseForge = findCurseForgeExecutable();
        if (curseForge == null) {
            System.out.println("CurseForge not found. Open it manually and click Play on your Fabric instance.");
            return;
        }
        new ProcessBuilder(curseForge.toString()).start();
        System.out.println("Opened CurseForge.");
        waitForAgent(45_000L);
    }

    public static boolean restartCurseForge() throws IOException, InterruptedException {
        Path curseForge = findCurseForgeExecutable();
        if (curseForge == null) {
            return false;
        }
        stopCurseForgeProcesses();
        Thread.sleep(2000L);
        new ProcessBuilder(curseForge.toString()).start();
        System.out.println("Restarted CurseForge to apply injection args.");
        boolean ready = waitForAgent(90_000L);
        if (!ready) {
            System.out.println("CurseForge is still starting...");
        }
        return ready;
    }

    private static void stopCurseForgeProcesses() {
        try {
            new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Get-Process CurseForge,Curse.Agent.Host -ErrorAction SilentlyContinue | Stop-Process -Force"
            ).start().waitFor();
        } catch (Exception ignored) {
        }
    }

    public static boolean launchInstance(Path instance) {
        if (instance == null) {
            return false;
        }
        String guid = readInstanceGuid(instance);
        if (guid == null || guid.isBlank()) {
            return false;
        }
        String uri = "curseforge://launch-game?instanceId=" + guid + "&gameId=" + MINECRAFT_GAME_ID;
        Path curseForge = findCurseForgeExecutable();
        if (curseForge != null) {
            try {
                new ProcessBuilder(curseForge.toString(), uri).start();
                System.out.println("Sent launch request to CurseForge.");
                return true;
            } catch (IOException ignored) {
            }
        }
        if (!openUriFallback(uri)) {
            return false;
        }
        System.out.println("Sent launch request to CurseForge.");
        return true;
    }

    public static void openInstance(Path instance) {
        launchInstance(instance);
    }

    public static boolean waitForAgent(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isCurseForgeRunning() && CurseForgeLauncher.isAgentAvailable()) {
                return true;
            }
            Thread.sleep(500L);
        }
        return isCurseForgeRunning() && CurseForgeLauncher.isAgentAvailable();
    }

    private static boolean openUriFallback(String uri) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return false;
        }
        try {
            new ProcessBuilder("cmd", "/c", "start", "", uri).start();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String readInstanceGuid(Path instance) {
        Path json = instance.resolve("minecraftinstance.json");
        if (!Files.isRegularFile(json)) {
            return null;
        }
        try {
            String content = Files.readString(json);
            return extractJsonString(content, "guid");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return json.substring(firstQuote + 1, secondQuote);
    }

    private static Path findCurseForgeExecutable() {
        List<Path> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Programs", "CurseForge Windows", "CurseForge.exe"));
            candidates.add(Path.of(localAppData, "Overwolf", "CurseForge.exe"));
        }
        if (programFiles != null) {
            candidates.add(Path.of(programFiles, "CurseForge", "CurseForge.exe"));
        }
        if (programFilesX86 != null) {
            candidates.add(Path.of(programFilesX86, "CurseForge", "CurseForge.exe"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isCurseForgeRunning() {
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "(Get-Process CurseForge -ErrorAction SilentlyContinue | Measure-Object).Count"
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            return !output.equals("0") && !output.isBlank();
        } catch (Exception ignored) {
        }
        return false;
    }
}
