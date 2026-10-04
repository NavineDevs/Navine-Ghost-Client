package nv.navineghost.injector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModConflictResolver {
    public static final String DISABLED_SUFFIX = ".ghost-disabled";

    private ModConflictResolver() {
    }

    public static List<String> disableConflictingMods(Path instance) throws IOException {
        Path modsDir = instance.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }
        List<String> disabled = new ArrayList<>();
        try (var stream = Files.list(modsDir)) {
            for (Path mod : stream.toList()) {
                if (!Files.isRegularFile(mod)) {
                    continue;
                }
                String name = mod.getFileName().toString();
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (!isConflictingMod(lowerName)) {
                    continue;
                }
                Path target = mod.resolveSibling(name + DISABLED_SUFFIX);
                if (Files.exists(target)) {
                    if (isGhostMod(lowerName)) {
                        continue;
                    }
                    Files.delete(mod);
                    disabled.add(name + " (already disabled)");
                    continue;
                }
                Files.move(mod, target, StandardCopyOption.REPLACE_EXISTING);
                disabled.add(name);
            }
        }
        return disabled;
    }

    public static List<String> restoreDisabledMods(Path instance) throws IOException {
        Path modsDir = instance.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }
        List<String> restored = new ArrayList<>();
        try (var stream = Files.list(modsDir)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(DISABLED_SUFFIX)) {
                    continue;
                }
                String originalName = name.substring(0, name.length() - DISABLED_SUFFIX.length());
                if (isGhostMod(originalName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Path target = modsDir.resolve(originalName);
                if (Files.exists(target)) {
                    continue;
                }
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                restored.add(originalName);
            }
        }
        return restored;
    }

    public static List<String> findConflictingMods(Path instance) throws IOException {
        Path modsDir = instance.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }
        List<String> conflicts = new ArrayList<>();
        try (var stream = Files.list(modsDir)) {
            for (Path mod : stream.toList()) {
                if (!Files.isRegularFile(mod)) {
                    continue;
                }
                String name = mod.getFileName().toString().toLowerCase(Locale.ROOT);
                if (isConflictingMod(name)) {
                    conflicts.add(mod.getFileName().toString());
                }
            }
        }
        return conflicts;
    }

    public static boolean isGhostMod(String lowerName) {
        if (lowerName.endsWith(".jar")) {
            lowerName = lowerName.substring(0, lowerName.length() - 4);
        }
        return lowerName.equals("navine-client-ghost")
                || lowerName.equals("navine-ghost-client")
                || lowerName.contains("navine-ghost")
                || lowerName.contains("ghost-client")
                || (lowerName.contains("navine") && lowerName.contains("ghost"));
    }

    private static boolean isConflictingMod(String lowerName) {
        if (lowerName.endsWith(DISABLED_SUFFIX)) {
            return false;
        }
        if (isGhostMod(lowerName)) {
            return false;
        }
        return lowerName.contains("navine-client")
                || lowerName.equals("navine-client.jar")
                || lowerName.startsWith("navine-client-")
                || lowerName.contains("navine_client");
    }
}
