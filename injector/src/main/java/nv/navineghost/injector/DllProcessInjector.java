package nv.navineghost.injector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class DllProcessInjector {
    public static final String DLL_NAME = "ghost_java_inject.dll";
    public static final String HELPER_NAME = "ghost-dll-inject.exe";
    private static final String CFG_ENV = "GHOST_JAVA_INJECT_CFG";
    private static final String AGENT_CLASS = "nv.navineghost.injector.GhostAgent";

    private DllProcessInjector() {
    }

    public static Path deployNativeBinary(String resourceName, String fileName) throws IOException {
        Path dir = nativeDeployDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        try (InputStream in = DllProcessInjector.class.getResourceAsStream("/native/" + resourceName)) {
            if (in == null) {
                Path fallback = Path.of("native", "out", resourceName);
                if (Files.isRegularFile(fallback)) {
                    Files.copy(fallback, target, StandardCopyOption.REPLACE_EXISTING);
                    return target.toAbsolutePath().normalize();
                }
                Path sibling = resolveBesideJar(resourceName);
                if (sibling != null) {
                    Files.copy(sibling, target, StandardCopyOption.REPLACE_EXISTING);
                    return target.toAbsolutePath().normalize();
                }
                throw new IOException("Native file missing: " + resourceName + " (build injector/native)");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toAbsolutePath().normalize();
    }

    public static Path writeInjectConfig(Path agentJar, Path payloadJar) throws IOException {
        Path cfg = Path.of(System.getProperty("java.io.tmpdir"), "ghost-java-inject.cfg");
        String body = agentJar.toAbsolutePath().normalize()
                + System.lineSeparator()
                + payloadJar.toAbsolutePath().normalize()
                + System.lineSeparator()
                + AGENT_CLASS
                + System.lineSeparator();
        Files.writeString(cfg, body, StandardCharsets.UTF_8);
        return cfg.toAbsolutePath().normalize();
    }

    public static boolean injectIntoPid(long pid, Path agentJar, Path payloadJar) throws Exception {
        Path dll = deployNativeBinary(DLL_NAME, DLL_NAME);
        Path helper = deployNativeBinary(HELPER_NAME, HELPER_NAME);
        Path cfg = writeInjectConfig(agentJar, payloadJar);

        ProcessBuilder builder = new ProcessBuilder(
                helper.toString(),
                Long.toString(pid),
                dll.toString()
        );
        builder.environment().put(CFG_ENV, cfg.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("DLL inject helper timed out");
        }
        int code = process.exitValue();
        if (!output.isBlank()) {
            System.out.println(output);
        }
        if (code != 0) {
            throw new IOException("DLL inject failed with code " + code);
        }
        return true;
    }

    public static boolean injectIntoMinecraft(Path instance, Path agentJar, Path payloadJar, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Optional<Long> pid = Optional.empty();
        while (System.currentTimeMillis() < deadline) {
            pid = ProcessWatcher.findMinecraftProcessId(instance);
            if (pid.isEmpty()) {
                pid = ProcessWatcher.findMinecraftProcessId(null);
            }
            if (pid.isPresent()) {
                break;
            }
            Thread.sleep(250L);
        }
        if (pid.isEmpty()) {
            throw new IOException("Minecraft Java process not found for DLL inject");
        }
        System.out.println("Injecting DLL into Java process pid=" + pid.get());
        return injectIntoPid(pid.get(), agentJar, payloadJar);
    }

    private static Path nativeDeployDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "NavineGhost", "native");
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "navine-ghost-native");
    }

    private static Path resolveBesideJar(String name) {
        try {
            Path codeSource = Path.of(DllProcessInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codeSource)) {
                Path beside = codeSource.getParent().resolve(name);
                if (Files.isRegularFile(beside)) {
                    return beside;
                }
                Path nativeBeside = codeSource.getParent().resolve("native").resolve(name);
                if (Files.isRegularFile(nativeBeside)) {
                    return nativeBeside;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
