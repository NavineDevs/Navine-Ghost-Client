package nv.navineghost.injector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class InstanceLocator {
    private static final Pattern MC_26_VERSION = Pattern.compile(
            "26\\.(\\d+)(?:\\.(\\d+))?(-snapshot(?:-\\d+)?)?",
            Pattern.CASE_INSENSITIVE);

    private InstanceLocator() {
    }

    public static Optional<Path> resolvePreferred() throws IOException {
        List<ScoredInstance> scored = new ArrayList<>();
        for (Path instance : discoverInstances()) {
            scored.add(new ScoredInstance(instance, scoreInstance(instance)));
        }
        scored.sort(Comparator.comparingInt(ScoredInstance::score).reversed()
                .thenComparing(item -> item.path().toString().toLowerCase(Locale.ROOT)));
        Optional<ScoredInstance> best = scored.stream()
                .filter(item -> item.score() > 0)
                .findFirst();
        if (best.isPresent()) {
            return Optional.of(best.get().path());
        }
        List<Path> all = discoverInstances();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(all.get(0));
    }

    public static Optional<Path> resolve(Path rawInstance) throws IOException {
        if (rawInstance != null) {
            Path explicit = rawInstance.toAbsolutePath().normalize();
            if (!Files.isDirectory(explicit)) {
                throw new IOException("Instance directory does not exist: " + explicit);
            }
            return Optional.of(explicit);
        }
        return resolvePreferred();
    }

    public static Optional<Path> resolve(String rawInstance) throws IOException {
        if (rawInstance != null && !rawInstance.isBlank()) {
            return resolve(Path.of(rawInstance.trim()));
        }
        return resolvePreferred();
    }

    public static List<Path> discoverInstances() throws IOException {
        List<Path> instances = new ArrayList<>();
        for (Path root : instanceRoots()) {
            collectInstances(instances, root);
        }
        instances.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));
        return instances;
    }

    public static Optional<String> detectGameVersion(Path instance) {
        if (instance == null) {
            return Optional.empty();
        }
        try {
            Path cfJson = instance.resolve("minecraftinstance.json");
            if (Files.isRegularFile(cfJson)) {
                String json = Files.readString(cfJson);
                for (String key : List.of("gameVersion", "minecraftVersion", "baseMinecraftVersion", "version")) {
                    Optional<String> value = readJsonString(json, key);
                    if (value.isPresent() && !value.get().isBlank()) {
                        return value;
                    }
                }
                Optional<String> embedded = firstVersionInText(json);
                if (embedded.isPresent()) {
                    return embedded;
                }
            }
            Optional<String> blobVersion = firstVersionInText(readInstanceBlob(instance));
            if (blobVersion.isPresent()) {
                return blobVersion;
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    public static boolean isSupportedGameVersion(String version) {
        return scoreGameVersionText(version == null ? "" : version) >= 70;
    }

    public static String supportedVersionsLabel() {
        return "Fabric 26.1.x, 26.2, 26.3";
    }

    private static int scoreInstance(Path instance) {
        int score = 0;
        String lower = instance.toString().toLowerCase(Locale.ROOT);
        if (lower.contains("navine")) {
            score += 120;
        }
        if (lower.contains("navuryx")) {
            score += 120;
        }
        if (lower.contains("ghost")) {
            score += 40;
        }
        if (lower.contains("navine ghost client") || lower.contains("navuryx ghost client")) {
            score += 200;
        }
        if (lower.contains("fabric")) {
            score += 20;
        }

        String blob = readInstanceBlob(instance).toLowerCase(Locale.ROOT);
        if (!blob.isBlank()) {
            score += 10;
            score += scoreGameVersionText(blob);
            if (blob.contains("fabric") || blob.contains("net.fabricmc")) {
                score += 35;
            }
            if (blob.contains("forge") && !blob.contains("fabric")) {
                score -= 50;
            }
            if (blob.contains("neoforge") && !blob.contains("fabric")) {
                score -= 50;
            }
        }
        if (Files.isRegularFile(instance.resolve("minecraftinstance.json"))) {
            score += 15;
        }
        if (Files.isDirectory(instance.resolve("mods"))) {
            score += 10;
        }
        if (Files.isRegularFile(instance.resolve("mmc-pack.json"))
                || Files.isRegularFile(instance.resolve("instance.cfg"))) {
            score += 8;
        }
        return score;
    }

    static int scoreGameVersionText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Matcher matcher = MC_26_VERSION.matcher(text);
        int best = 0;
        while (matcher.find()) {
            int minor = Integer.parseInt(matcher.group(1));
            int patch = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            boolean snapshot = matcher.group(3) != null;
            int score;
            if (minor == 1) {
                score = 80 + (patch >= 2 ? 12 : (patch == 1 ? 8 : 4));
            } else if (minor == 2) {
                score = 94 + (patch > 0 ? 2 : 0);
            } else if (minor == 3) {
                score = 92 - (snapshot ? 4 : 0);
            } else if (minor >= 4 && minor <= 9) {
                score = 70;
            } else {
                score = 40;
            }
            best = Math.max(best, score);
        }
        if (best == 0 && text.toLowerCase(Locale.ROOT).contains("26.")) {
            best = 35;
        }
        return best;
    }

    private static Optional<String> firstVersionInText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = MC_26_VERSION.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    private static Optional<String> readJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int pos = json.indexOf(needle);
        if (pos < 0) {
            return Optional.empty();
        }
        int start = pos + needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return Optional.empty();
        }
        return Optional.of(json.substring(start, end));
    }

    private static String readInstanceBlob(Path instance) {
        StringBuilder blob = new StringBuilder();
        for (String name : List.of(
                "minecraftinstance.json",
                "mmc-pack.json",
                "instance.cfg",
                "config.json",
                "profile.json")) {
            Path file = instance.resolve(name);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                blob.append(Files.readString(file)).append('\n');
            } catch (IOException ignored) {
            }
        }
        return blob.toString();
    }

    private static void collectInstances(List<Path> instances, Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        if (isMinecraftInstance(root)) {
            instances.add(root.toAbsolutePath().normalize());
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .filter(InstanceLocator::isMinecraftInstance)
                    .map(path -> path.toAbsolutePath().normalize())
                    .forEach(instances::add);
        }
    }

    private static boolean isMinecraftInstance(Path directory) {
        return Files.isDirectory(directory.resolve("mods"))
                || Files.isRegularFile(directory.resolve("minecraftinstance.json"))
                || Files.isRegularFile(directory.resolve("instance.cfg"))
                || Files.isRegularFile(directory.resolve("mmc-pack.json"))
                || Files.isRegularFile(directory.resolve("options.txt"));
    }

    private static List<Path> instanceRoots() {
        List<Path> roots = new ArrayList<>();
        String home = System.getProperty("user.home");
        String appDataEnv = System.getenv("APPDATA");
        String localEnv = System.getenv("LOCALAPPDATA");
        Path appData = appDataEnv != null && !appDataEnv.isBlank()
                ? Path.of(appDataEnv)
                : (home == null ? null : Path.of(home, "AppData", "Roaming"));
        Path local = localEnv != null && !localEnv.isBlank()
                ? Path.of(localEnv)
                : (home == null ? null : Path.of(home, "AppData", "Local"));

        if (appData != null) {
            roots.add(appData.resolve(".minecraft"));
            roots.add(appData.resolve("PrismLauncher").resolve("instances"));
            roots.add(appData.resolve("PrismLauncher-cracked").resolve("instances"));
            roots.add(appData.resolve("PolyMC").resolve("instances"));
            roots.add(appData.resolve("MultiMC").resolve("instances"));
            roots.add(appData.resolve("ModrinthApp").resolve("profiles"));
        }
        if (local != null) {
            roots.add(local.resolve("ModrinthApp").resolve("profiles"));
        }
        if (home != null && !home.isBlank()) {
            roots.add(Path.of(home, "curseforge", "minecraft", "Instances"));
            roots.add(Path.of(home, "scoop", "apps", "prismlauncher", "current", "instances"));
            roots.add(Path.of(home, "AppData", "Roaming", "PrismLauncher", "instances"));
        }
        return roots;
    }

    private record ScoredInstance(Path path, int score) {
    }
}
