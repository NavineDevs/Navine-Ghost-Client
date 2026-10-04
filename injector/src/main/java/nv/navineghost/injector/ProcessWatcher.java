package nv.navineghost.injector;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ProcessWatcher {
    private ProcessWatcher() {
    }

    public static boolean isMinecraftRunning() {
        return findMinecraftProcessId(null).isPresent();
    }

    public static boolean isMinecraftRunning(Path instance) {
        return findMinecraftProcessId(instance).isPresent();
    }

    public static Optional<Long> findMinecraftProcessId() {
        return findMinecraftProcessId(null);
    }

    public static Optional<Long> findMinecraftProcessId(Path instance) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            Optional<Long> powershell = findWindowsMinecraftPidPowerShell(instance);
            if (powershell.isPresent()) {
                return powershell;
            }
            return findWindowsMinecraftPidWmic(instance);
        }
        return findUnixMinecraftPid(instance);
    }

    public static boolean waitForMinecraftStart(long timeoutMs) throws InterruptedException {
        return waitForMinecraftStart(null, timeoutMs);
    }

    public static boolean waitForMinecraftStart(Path instance, long timeoutMs) throws InterruptedException {
        if (instance != null) {
            return InstanceLogWatcher.waitForGameStart(instance, timeoutMs);
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isMinecraftRunning()) {
                return true;
            }
            Thread.sleep(500L);
        }
        return isMinecraftRunning();
    }

    public static void waitForMinecraftExit() throws InterruptedException {
        while (isMinecraftRunning()) {
            Thread.sleep(1000L);
        }
    }

    public static void stopMinecraft(Path instance) throws InterruptedException {
        Optional<Long> pid = findMinecraftProcessId(instance);
        if (pid.isEmpty()) {
            pid = findMinecraftProcessId(null);
        }
        if (pid.isEmpty()) {
            return;
        }
        try {
            new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Stop-Process -Id " + pid.get() + " -Force"
            ).start().waitFor();
        } catch (Exception ignored) {
        }
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline && isMinecraftRunning(instance)) {
            Thread.sleep(500L);
        }
    }

    private static Optional<Long> findWindowsMinecraftPidPowerShell(Path instance) {
        try {
            Process process = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'javaw.exe' -or $_.Name -eq 'java.exe' } | ForEach-Object { $_.ProcessId.ToString() + '|' + $_.CommandLine }"
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            for (String line : output.split("\\R")) {
                int separator = line.indexOf('|');
                if (separator <= 0) {
                    continue;
                }
                String commandLine = line.substring(separator + 1);
                if (!looksLikeMinecraft(commandLine, instance)) {
                    continue;
                }
                String pidValue = line.substring(0, separator).trim();
                if (pidValue.matches("\\d+")) {
                    return Optional.of(Long.parseLong(pidValue));
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Long> findWindowsMinecraftPidWmic(Path instance) {
        try {
            Process process = new ProcessBuilder(
                    "wmic",
                    "process",
                    "where",
                    "name='javaw.exe' or name='java.exe'",
                    "get",
                    "ProcessId,CommandLine",
                    "/FORMAT:CSV"
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            for (String line : output.split("\\R")) {
                if (!looksLikeMinecraft(line, instance)) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    continue;
                }
                String pidValue = parts[parts.length - 1].trim();
                if (pidValue.matches("\\d+")) {
                    return Optional.of(Long.parseLong(pidValue));
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static Optional<Long> findUnixMinecraftPid(Path instance) {
        try {
            Process process = new ProcessBuilder("ps", "-eo", "pid,command")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            for (String line : output.split("\\R")) {
                if (!looksLikeMinecraft(line, instance)) {
                    continue;
                }
                String trimmed = line.trim();
                int space = trimmed.indexOf(' ');
                if (space <= 0) {
                    continue;
                }
                String pidValue = trimmed.substring(0, space).trim();
                if (pidValue.matches("\\d+")) {
                    return Optional.of(Long.parseLong(pidValue));
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static boolean looksLikeMinecraft(String commandLine, Path instance) {
        if (commandLine == null || commandLine.isBlank()) {
            return false;
        }
        String lower = commandLine.toLowerCase(Locale.ROOT);
        if (isExcludedProcess(lower)) {
            return false;
        }
        if (instance != null) {
            String instancePath = instance.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (lower.contains(instancePath)) {
                return true;
            }
            String instanceName = instance.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!instanceName.isBlank() && lower.contains(instanceName) && lower.contains("minecraft")) {
                return true;
            }
        }
        List<String> markers = List.of(
                "net.minecraft.client.main.main",
                "net.minecraft.client.main",
                "net.fabricmc.loader.impl.launch.knot.knotclient",
                "net.fabricmc.loader.impl.launch.knot.knot",
                "net.fabricmc.loader",
                "minecraft.exe",
                "-dminecraft.applet.targetdirectory",
                "--gameDir",
                "curseforge\\minecraft\\instances\\",
                "curseforge/minecraft/instances/"
        );
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedProcess(String lower) {
        return lower.contains("gradle")
                || lower.contains("org.gradle")
                || lower.contains("ghostinjector")
                || lower.contains("navine-client-ghost.jar")
                || lower.contains("jetbrains")
                || lower.contains("cursor");
    }
}
