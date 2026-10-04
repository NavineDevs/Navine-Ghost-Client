package nv.navineghost.injector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CurseForgeArgsPatcher {
    private static final Pattern JAVA_ARGS_PATTERN = Pattern.compile("\"javaArgsOverride\":(?:null|\"(?:\\\\.|[^\"])*\")");

    private CurseForgeArgsPatcher() {
    }

    public static boolean install(Path instance, Path agentJar, Path payload) throws IOException {
        if (hasInjectionArgs(instance, agentJar, payload)) {
            return true;
        }
        String args = buildArgs(agentJar, payload);
        String replacement = "\"javaArgsOverride\":\"" + escapeJson(args) + "\"";
        boolean instancePatched = patchJsonFile(instance.resolve("minecraftinstance.json"), replacement);
        String guid = readInstanceGuid(instance);
        boolean cachePatched = guid != null && patchAgentCache(guid, replacement);
        return instancePatched || cachePatched || hasInjectionArgs(instance, agentJar, payload);
    }

    public static boolean uninstall(Path instance) throws IOException {
        boolean instanceRemoved = removeFromJsonFile(instance.resolve("minecraftinstance.json"));
        String guid = readInstanceGuid(instance);
        boolean cacheRemoved = guid != null && removeFromAgentCache(guid);
        return instanceRemoved || cacheRemoved;
    }

    public static boolean hasInjectionArgs(Path instance, Path agentJar, Path payload) throws IOException {
        String agent = agentJar.toAbsolutePath().normalize().toString();
        String modPath = payload.toAbsolutePath().normalize().toString();
        String guid = readInstanceGuid(instance);
        if (guid != null && containsInjectionArgs(agentCacheFile(), agent, modPath, guid)) {
            return true;
        }
        return containsInjectionArgs(instance.resolve("minecraftinstance.json"), agent, modPath, null);
    }

    private static String buildArgs(Path agentJar, Path payload) {
        return "-javaagent:" + agentJar.toAbsolutePath().normalize()
                + " -Dfabric.addMods=" + payload.toAbsolutePath().normalize();
    }

    private static boolean patchJsonFile(Path jsonFile, String replacement) throws IOException {
        if (!Files.isRegularFile(jsonFile)) {
            return false;
        }
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        String updated = applyReplacement(json, replacement);
        if (updated.equals(json)) {
            return false;
        }
        Files.writeString(jsonFile, updated, StandardCharsets.UTF_8);
        return true;
    }

    private static boolean removeFromJsonFile(Path jsonFile) throws IOException {
        if (!Files.isRegularFile(jsonFile)) {
            return false;
        }
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        String updated = json.replaceAll(",?\"javaArgsOverride\":(?:null|\"(?:\\\\.|[^\"])*\")", "");
        if (updated.equals(json)) {
            return false;
        }
        Files.writeString(jsonFile, updated, StandardCharsets.UTF_8);
        return true;
    }

    private static boolean patchAgentCache(String guid, String replacement) throws IOException {
        Path cache = agentCacheFile();
        if (!Files.isRegularFile(cache)) {
            return false;
        }
        String json = Files.readString(cache, StandardCharsets.UTF_8);
        String object = extractInstanceObject(json, guid);
        if (object == null) {
            return false;
        }
        String patchedObject = applyReplacement(object, replacement);
        if (object.equals(patchedObject)) {
            return false;
        }
        int start = json.indexOf(object);
        if (start < 0) {
            return false;
        }
        String updated = json.substring(0, start) + patchedObject + json.substring(start + object.length());
        Files.writeString(cache, updated, StandardCharsets.UTF_8);
        return true;
    }

    private static boolean removeFromAgentCache(String guid) throws IOException {
        Path cache = agentCacheFile();
        if (!Files.isRegularFile(cache)) {
            return false;
        }
        String json = Files.readString(cache, StandardCharsets.UTF_8);
        String object = extractInstanceObject(json, guid);
        if (object == null) {
            return false;
        }
        String patchedObject = object.replaceAll(",?\"javaArgsOverride\":(?:null|\"(?:\\\\.|[^\"])*\")", "");
        if (object.equals(patchedObject)) {
            return false;
        }
        int start = json.indexOf(object);
        if (start < 0) {
            return false;
        }
        String updated = json.substring(0, start) + patchedObject + json.substring(start + object.length());
        Files.writeString(cache, updated, StandardCharsets.UTF_8);
        return true;
    }

    private static String applyReplacement(String json, String replacement) {
        Matcher matcher = JAVA_ARGS_PATTERN.matcher(json);
        if (!matcher.find()) {
            String updated = json.replaceFirst("\"isUnlocked\"", replacement + ",\"isUnlocked\"");
            return updated.equals(json) ? json : updated;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static boolean containsInjectionArgs(Path jsonFile, String agent, String modPath, String guid) throws IOException {
        if (!Files.isRegularFile(jsonFile)) {
            return false;
        }
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        if (guid != null) {
            String object = extractInstanceObject(json, guid);
            if (object == null) {
                return false;
            }
            return object.contains(agent) && object.contains(modPath);
        }
        return json.contains(agent) && json.contains(modPath);
    }

    private static Path agentCacheFile() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return Path.of("CurseForge", "agent", "GameInstances", "MinecraftGameInstance.json");
        }
        return Path.of(appData, "CurseForge", "agent", "GameInstances", "MinecraftGameInstance.json");
    }

    private static String readInstanceGuid(Path instance) throws IOException {
        Path jsonFile = instance.resolve("minecraftinstance.json");
        if (!Files.isRegularFile(jsonFile)) {
            return null;
        }
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        int index = json.indexOf("\"guid\":\"");
        if (index < 0) {
            return null;
        }
        int start = index + 8;
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    private static String extractInstanceObject(String json, String guid) {
        String needle = "\"guid\":\"" + guid + "\"";
        int guidIndex = json.indexOf(needle);
        if (guidIndex < 0) {
            return null;
        }
        int start = findObjectStart(json, guidIndex);
        int end = findObjectEnd(json, start);
        if (start < 0 || end <= start) {
            return null;
        }
        return json.substring(start, end + 1);
    }

    private static int findObjectStart(String json, int index) {
        int depth = 0;
        for (int i = index; i >= 0; i--) {
            char ch = json.charAt(i);
            if (ch == '}') {
                depth++;
            } else if (ch == '{') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static int findObjectEnd(String json, int objectStart) {
        int depth = 0;
        for (int i = objectStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
