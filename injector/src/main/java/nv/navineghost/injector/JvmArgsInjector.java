package nv.navineghost.injector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JvmArgsInjector {
    private JvmArgsInjector() {
    }

    public static Path jvmArgsFile(Path instance) {
        Path direct = instance.resolve("user_jvm_args.txt");
        if (Files.exists(direct)) {
            return direct;
        }
        Path nested = instance.resolve("minecraft").resolve("user_jvm_args.txt");
        if (Files.exists(nested)) {
            return nested;
        }
        return direct;
    }

    public static InstallResult installInjection(Path instance, Path agentJar, Path payload) throws IOException {
        Path jvmArgs = jvmArgsFile(instance);
        List<String> lines = cleanExisting(readLines(jvmArgs));
        String agentLine = "-javaagent:" + agentJar.toAbsolutePath().normalize();
        String modsLine = "-Dfabric.addMods=" + payload.toAbsolutePath().normalize();
        boolean changed = false;
        if (!replaceLine(lines, agentLine) && !containsLine(lines, agentLine)) {
            lines.add(agentLine);
            changed = true;
        } else {
            changed = true;
        }
        if (!replaceLine(lines, modsLine) && !containsLine(lines, modsLine)) {
            lines.add(modsLine);
            changed = true;
        } else {
            changed = true;
        }
        writeLines(jvmArgs, lines);
        return new InstallResult(jvmArgs, changed, "Installed injection args in " + jvmArgs);
    }

    public static InstallResult installAgent(Path instance, Path agentJar) throws IOException {
        Path jvmArgs = jvmArgsFile(instance);
        List<String> lines = cleanExisting(readLines(jvmArgs));
        String agentLine = "-javaagent:" + agentJar.toAbsolutePath().normalize();
        if (replaceLine(lines, agentLine)) {
            writeLines(jvmArgs, lines);
            return new InstallResult(jvmArgs, true, "Updated javaagent in " + jvmArgs);
        }
        if (containsLine(lines, agentLine)) {
            return new InstallResult(jvmArgs, false, "Agent arg already present");
        }
        lines.add(agentLine);
        writeLines(jvmArgs, lines);
        return new InstallResult(jvmArgs, true, "Added javaagent to " + jvmArgs);
    }

    public static InstallResult installFabricMods(Path instance, Path payload) throws IOException {
        Path jvmArgs = jvmArgsFile(instance);
        List<String> lines = cleanExisting(readLines(jvmArgs));
        String modsLine = "-Dfabric.addMods=" + payload.toAbsolutePath().normalize();
        if (replaceLine(lines, modsLine)) {
            writeLines(jvmArgs, lines);
            return new InstallResult(jvmArgs, true, "Updated fabric.addMods in " + jvmArgs);
        }
        if (containsLine(lines, modsLine)) {
            return new InstallResult(jvmArgs, false, "fabric.addMods already present");
        }
        lines.add(modsLine);
        writeLines(jvmArgs, lines);
        return new InstallResult(jvmArgs, true, "Added fabric.addMods to " + jvmArgs);
    }

    public static boolean uninstall(Path instance) throws IOException {
        Path jvmArgs = jvmArgsFile(instance);
        if (!Files.exists(jvmArgs)) {
            return false;
        }
        List<String> cleaned = cleanExisting(readLines(jvmArgs));
        if (cleaned.size() == readLines(jvmArgs).size() && cleaned.equals(readLines(jvmArgs))) {
            return false;
        }
        writeLines(jvmArgs, cleaned);
        return true;
    }

    private static List<String> cleanExisting(List<String> lines) {
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.contains("navine-ghost")) {
                continue;
            }
            if (trimmed.startsWith("-javaagent:") && trimmed.contains("Navine-Client-Ghost.jar")) {
                continue;
            }
            if (trimmed.startsWith("-javaagent:") && trimmed.contains("GhostInjector.jar")) {
                continue;
            }
            if (trimmed.startsWith("-Dfabric.addMods=") && trimmed.contains("datafixerupper-8.0.16-runtime.jar")) {
                continue;
            }
            cleaned.add(trimmed);
        }
        return cleaned;
    }

    private static boolean replaceLine(List<String> lines, String replacement) {
        if (replacement.startsWith("-javaagent:")) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("-javaagent:")) {
                    if (lines.get(i).equals(replacement)) {
                        return false;
                    }
                    lines.set(i, replacement);
                    return true;
                }
            }
            return false;
        }
        if (replacement.startsWith("-Dfabric.addMods=")) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("-Dfabric.addMods=")) {
                    if (lines.get(i).equals(replacement)) {
                        return false;
                    }
                    lines.set(i, replacement);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private static List<String> readLines(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    private static void writeLines(Path file, List<String> lines) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static boolean containsLine(List<String> lines, String target) {
        for (String line : lines) {
            if (target.equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    public record InstallResult(Path jvmArgsFile, boolean changed, String message) {
    }
}
