package nv.navineghost.injector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModFolderDeploy {
    public static final String MOD_FILE_NAME = "navine-ghost-client.jar";
    public static final String LEGACY_MOD_FILE_NAME = "Navine-Client-Ghost.jar";

    private ModFolderDeploy() {
    }

    public static Path deploy(Path instance, Path clientJar) throws IOException {
        Path modsDir = instance.resolve("mods");
        Files.createDirectories(modsDir);
        Path target = modsDir.resolve(MOD_FILE_NAME);
        Path source = resolveSource(clientJar);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        verifyFabricMod(target);
        removeLegacyNames(modsDir);
        return target;
    }

    public static boolean remove(Path instance) throws IOException {
        Path modsDir = instance.resolve("mods");
        boolean removed = false;
        for (String name : new String[]{MOD_FILE_NAME, LEGACY_MOD_FILE_NAME}) {
            Path target = modsDir.resolve(name);
            if (Files.exists(target)) {
                Files.delete(target);
                removed = true;
            }
        }
        return removed;
    }

    private static void removeLegacyNames(Path modsDir) {
        deleteQuietly(modsDir.resolve(LEGACY_MOD_FILE_NAME));
        deleteQuietly(modsDir.resolve(LEGACY_MOD_FILE_NAME + ModConflictResolver.DISABLED_SUFFIX));
    }

    private static void deleteQuietly(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Files.delete(file);
        } catch (IOException ignored) {
        }
    }

    private static void verifyFabricMod(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry fabricMod = zip.getEntry("fabric.mod.json");
            if (fabricMod == null) {
                throw new IOException("Deployed file is not a Fabric mod (missing fabric.mod.json): " + jar);
            }
            try (InputStream entry = zip.getInputStream(fabricMod)) {
                String content = new String(entry.readAllBytes()).toLowerCase(Locale.ROOT);
                if (!content.contains("navine-ghost") && !content.contains("navineclient")) {
                    throw new IOException("Deployed file does not look like Navine Ghost client: " + jar);
                }
            }
        }
    }

    private static Path resolveSource(Path explicitClientJar) throws IOException {
        if (explicitClientJar != null) {
            if (!Files.isRegularFile(explicitClientJar)) {
                throw new IOException("Client jar not found: " + explicitClientJar);
            }
            Path normalized = explicitClientJar.toAbsolutePath().normalize();
            verifyFabricMod(normalized);
            return normalized;
        }
        try (InputStream embedded = GhostAgent.class.getResourceAsStream("/embedded/navine-ghost-client.jar")) {
            if (embedded == null) {
                throw new IOException("Embedded client missing from Navine-Client-Ghost.jar. Rebuild with :injector:deployInjector");
            }
            Path temp = Files.createTempFile("navine-ghost-client-", ".jar");
            Files.copy(embedded, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            verifyFabricMod(temp);
            return temp;
        }
    }
}
