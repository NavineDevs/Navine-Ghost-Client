package nv.navineghost.injector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class InstanceLogWatcher {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private InstanceLogWatcher() {
    }

    public static boolean waitForGameStart(Path instance, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long launchTime = System.currentTimeMillis();
        Path latestLog = instance.resolve("logs").resolve("latest.log");
        while (System.currentTimeMillis() < deadline) {
            if (ProcessWatcher.isMinecraftRunning(instance) || ProcessWatcher.isMinecraftRunning()) {
                return true;
            }
            if (showsGameStarted(latestLog, launchTime)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return ProcessWatcher.isMinecraftRunning(instance)
                || ProcessWatcher.isMinecraftRunning()
                || showsGameStarted(latestLog, launchTime);
    }

    public static boolean showsGhostClientLoaded(Path instance) {
        return showsGhostClientLoaded(instance, 0L);
    }

    public static boolean showsGhostClientLoaded(Path instance, long launchTime) {
        Path latestLog = instance.resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(latestLog)) {
            return false;
        }
        try {
            long modified = Files.getLastModifiedTime(latestLog).toMillis();
            if (launchTime > 0 && modified + 1000L < launchTime) {
                return false;
            }
            String tail = readTail(latestLog, 24_576);
            LocalTime launchClock = launchTime > 0
                    ? LocalTime.ofInstant(java.time.Instant.ofEpochMilli(launchTime), java.time.ZoneId.systemDefault())
                    : null;
            for (String line : tail.split("\\R")) {
                if (!line.contains("navine-ghost") && !line.contains("Navine-Ghost-Client")) {
                    continue;
                }
                if (launchClock == null || isLogLineAfter(line, launchClock)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean waitForGhostClientLoaded(Path instance, long launchTime, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (showsGhostClientLoaded(instance, launchTime)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return showsGhostClientLoaded(instance, launchTime);
    }

    private static boolean showsGameStarted(Path latestLog, long launchTime) {
        if (!Files.isRegularFile(latestLog)) {
            return false;
        }
        try {
            long modified = Files.getLastModifiedTime(latestLog).toMillis();
            if (modified + 1000L < launchTime) {
                return false;
            }
            String tail = readTail(latestLog, 16_384);
            LocalTime launchClock = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(launchTime), java.time.ZoneId.systemDefault());
            for (String line : tail.split("\\R")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (!lower.contains("loading minecraft") && !lower.contains("setting user:")) {
                    continue;
                }
                if (isLogLineAfter(line, launchClock)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isLogLineAfter(String line, LocalTime launchClock) {
        int start = line.indexOf('[');
        int end = line.indexOf(']', start + 1);
        if (start < 0 || end <= start + 1) {
            return true;
        }
        String timeText = line.substring(start + 1, end);
        try {
            LocalTime lineTime = LocalTime.parse(timeText, LOG_TIME);
            return !lineTime.isBefore(launchClock.minusSeconds(2));
        } catch (DateTimeParseException ignored) {
            return true;
        }
    }

    private static String readTail(Path file, int maxBytes) throws IOException {
        byte[] all = Files.readAllBytes(file);
        int start = Math.max(0, all.length - maxBytes);
        return new String(all, start, all.length - start, StandardCharsets.UTF_8);
    }
}
