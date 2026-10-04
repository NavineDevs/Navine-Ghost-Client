package nv.navineclient.util;

import com.sun.jna.NativeLibrary;
import nv.navineclient.NavineClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class DiscordNativeLoader {
    private static final String NATIVE_JAR = "META-INF/jars/discord-rpc-release-v3.4.0.jar";
    private static boolean loaded = false;

    private DiscordNativeLoader() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            String platformPath = resolvePlatformPath();
            String libraryFile = resolveLibraryFile();
            String resourcePath = platformPath + "/" + libraryFile;
            Path libraryPath = extractResource(resourcePath, libraryFile);
            if (libraryPath == null) {
                NavineClient.LOGGER.error("Discord RPC native library not found at {}", resourcePath);
                return;
            }
            NativeLibrary.addSearchPath("discord-rpc", libraryPath.getParent().toString());
            System.setProperty("jna.library.path", libraryPath.getParent().toString());
            NavineClient.LOGGER.info("Discord RPC native library loaded from {}", libraryPath);
        } catch (IOException e) {
            NavineClient.LOGGER.error("Failed to load Discord RPC native library", e);
        }
    }

    private static Path extractResource(String resourcePath, String libraryFile) throws IOException {
        InputStream stream = DiscordNativeLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream != null) {
            return writeToTemp(stream, libraryFile);
        }
        Path modJar = resolveModJarPath();
        if (modJar == null) {
            return null;
        }
        Path tempNativeJar = Files.createTempFile("navine-discord-rpc", ".jar");
        tempNativeJar.toFile().deleteOnExit();
        try (JarFile modFile = new JarFile(modJar.toFile())) {
            JarEntry nestedEntry = modFile.getJarEntry(NATIVE_JAR);
            if (nestedEntry == null) {
                return null;
            }
            try (InputStream nestedJarStream = modFile.getInputStream(nestedEntry)) {
                Files.copy(nestedJarStream, tempNativeJar, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (JarFile nativeJar = new JarFile(tempNativeJar.toFile())) {
            JarEntry entry = nativeJar.getJarEntry(resourcePath);
            if (entry == null) {
                Enumeration<JarEntry> entries = nativeJar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry candidate = entries.nextElement();
                    if (candidate.getName().endsWith("/" + libraryFile)) {
                        entry = candidate;
                        break;
                    }
                }
            }
            if (entry == null) {
                return null;
            }
            try (InputStream nativeStream = nativeJar.getInputStream(entry)) {
                return writeToTemp(nativeStream, libraryFile);
            }
        } finally {
            Files.deleteIfExists(tempNativeJar);
        }
    }

    private static Path resolveModJarPath() {
        try {
            String location = DiscordNativeLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            if (location != null && location.endsWith(".jar")) {
                return Path.of(location);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path writeToTemp(InputStream stream, String libraryFile) throws IOException {
        Path extractDir = Files.createTempDirectory("navine-discord-rpc");
        extractDir.toFile().deleteOnExit();
        Path libraryPath = extractDir.resolve(libraryFile);
        Files.copy(stream, libraryPath, StandardCopyOption.REPLACE_EXISTING);
        stream.close();
        libraryPath.toFile().deleteOnExit();
        return libraryPath;
    }

    private static String resolvePlatformPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("win")) {
            return arch.contains("64") ? "win32-x86-64" : "win32-x86";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        return "linux-x86-64";
    }

    private static String resolveLibraryFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "discord-rpc.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libdiscord-rpc.dylib";
        }
        return "libdiscord-rpc.so";
    }
}
