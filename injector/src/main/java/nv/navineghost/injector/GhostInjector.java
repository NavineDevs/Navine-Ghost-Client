package nv.navineghost.injector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class GhostInjector {
    public static final String INJECTOR_JAR = "Navine-Client-Ghost.jar";
    public static final String PRODUCT_NAME = "Navine-Ghost-Client";
    private static final String MARKER = "navine-ghost-agent";

    public static void main(String[] args) {
        try {
            int exitCode = run(args);
            System.exit(exitCode);
        } catch (Exception error) {
            System.err.println("[ghost] " + error.getMessage());
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        if (args.length == 0) {
            return start(new InjectOptions("start", null, null, null));
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        InjectOptions options = InjectOptions.parse(command, args);

        return switch (options.command()) {
            case "help", "--help", "-h" -> {
                printHelp();
                yield 0;
            }
            case "list" -> listInstances();
            case "start" -> start(options);
            case "inject" -> inject(options, false);
            case "run" -> inject(options, true);
            case "cleanup", "uninstall" -> cleanup(options);
            case "status" -> status(options);
            case "launch" -> launch(options);
            default -> {
                System.err.println("Unknown command: " + options.command());
                printHelp();
                yield 1;
            }
        };
    }

    private static int start(InjectOptions options) throws Exception {
        Path instance = InstanceLocator.resolve(options.instance())
                .orElseThrow(() -> new IOException("No Fabric instance found. Use --instance <path>."));
        System.out.println("Using instance: " + instance);
        return inject(new InjectOptions("inject", instance, options.clientJar(), options.agentJar()), false);
    }

    private static int listInstances() throws IOException {
        List<Path> instances = InstanceLocator.discoverInstances();
        if (instances.isEmpty()) {
            System.out.println("No Minecraft instances found.");
            return 0;
        }
        System.out.println("Detected instances:");
        Optional<Path> preferred = InstanceLocator.resolvePreferred();
        for (Path instance : instances) {
            String marker = preferred.isPresent() && preferred.get().equals(instance) ? " (recommended)" : "";
            System.out.println(" - " + instance + marker);
        }
        return 0;
    }

    private static int inject(InjectOptions options, boolean watch) throws Exception {
        Path agentJar = resolveAgentJar(options.agentJar());
        Path instance = InstanceLocator.resolve(options.instance())
                .orElseThrow(() -> new IOException("No Minecraft instance found. Use --instance <path> or run list."));

        boolean removedFromMods = ModFolderDeploy.remove(instance);
        Path payload = deployPayload(options.clientJar());
        List<String> disabledMods = ModConflictResolver.disableConflictingMods(instance);
        JvmArgsInjector.InstallResult injectionResult = JvmArgsInjector.installInjection(instance, agentJar, payload);
        boolean profilePatched = CurseForgeArgsPatcher.install(instance, agentJar, payload);
        DllProcessInjector.writeInjectConfig(agentJar, payload);

        System.out.println("Injected stealth payload: " + payload);
        if (removedFromMods) {
            System.out.println("Removed ghost client from mods folder.");
        }
        System.out.println(injectionResult.message());
        System.out.println(profilePatched ? "Updated CurseForge javaArgsOverride (instance + agent cache)." : "CurseForge javaArgsOverride will be refreshed before launch.");
        if (!disabledMods.isEmpty()) {
            System.out.println("Disabled conflicting mods:");
            for (String mod : disabledMods) {
                System.out.println(" - " + mod);
            }
        }
        System.out.println("Instance: " + instance);
        System.out.println();

        long launchTime = System.currentTimeMillis();
        boolean alreadyRunning = ProcessWatcher.isMinecraftRunning(instance) || ProcessWatcher.isMinecraftRunning();
        if (alreadyRunning) {
            System.out.println("Minecraft is already running. Injecting DLL into the Java process...");
            try {
                DllProcessInjector.injectIntoMinecraft(instance, agentJar, payload, 5_000L);
                System.out.println("DLL injected into Java process.");
            } catch (Exception error) {
                System.out.println("DLL inject failed: " + error.getMessage());
                return 1;
            }
            boolean loaded = InstanceLogWatcher.waitForGhostClientLoaded(instance, launchTime, 60_000L);
            if (loaded) {
                System.out.println(PRODUCT_NAME + " loaded. Press ` in-game to open the GUI.");
            } else {
                System.out.println("DLL inject completed. If modules are missing, restart Minecraft with Start Ghost.");
            }
            return 0;
        }

        LauncherHelper.ensureCurseForgeOpen();
        LauncherHelper.waitForAgent(30_000L);
        CurseForgeArgsPatcher.install(instance, agentJar, payload);
        boolean launched = false;
        try {
            CurseForgeLauncher.launch(instance, agentJar, payload);
            launched = true;
        } catch (Exception error) {
            System.out.println("Auto-launch failed: " + error.getMessage());
            System.out.println("Open CurseForge and click Play on: " + instance.getFileName());
        }

        if (launched) {
            System.out.println("Waiting for Minecraft Java process...");
            try {
                DllProcessInjector.injectIntoMinecraft(instance, agentJar, payload, 120_000L);
                System.out.println("DLL injected into Java process.");
            } catch (Exception error) {
                System.out.println("DLL inject failed: " + error.getMessage());
                System.out.println("Falling back to javaagent launch args only.");
            }

            boolean started = ProcessWatcher.waitForMinecraftStart(instance, 120_000L);
            if (started) {
                boolean loaded = InstanceLogWatcher.waitForGhostClientLoaded(instance, launchTime, 90_000L);
                if (loaded) {
                    ModFolderDeploy.remove(instance);
                    System.out.println("Minecraft started. " + PRODUCT_NAME + " loaded. Press ` in-game to open the GUI.");
                } else {
                    System.out.println("Stealth/DLL path did not load. Applying mods-folder fallback and relaunching...");
                    ModFolderDeploy.deploy(instance, options.clientJar());
                    ProcessWatcher.stopMinecraft(instance);
                    CurseForgeArgsPatcher.install(instance, agentJar, payload);
                    DllProcessInjector.writeInjectConfig(agentJar, payload);
                    launchTime = System.currentTimeMillis();
                    try {
                        CurseForgeLauncher.launch(instance, agentJar, payload);
                        try {
                            DllProcessInjector.injectIntoMinecraft(instance, agentJar, payload, 120_000L);
                            System.out.println("DLL re-injected into Java process.");
                        } catch (Exception injectError) {
                            System.out.println("DLL re-inject failed: " + injectError.getMessage());
                        }
                        boolean restarted = ProcessWatcher.waitForMinecraftStart(instance, 120_000L);
                        if (restarted && InstanceLogWatcher.waitForGhostClientLoaded(instance, launchTime, 90_000L)) {
                            System.out.println("Minecraft started. " + PRODUCT_NAME + " loaded. Press ` in-game to open the GUI.");
                        } else {
                            System.out.println("Ghost client still not detected. Open CurseForge and click Play on: " + instance.getFileName());
                        }
                    } catch (Exception error) {
                        System.out.println("Relaunch failed: " + error.getMessage());
                        System.out.println("Close Minecraft and run Start Ghost again.");
                    }
                }
            } else {
                System.out.println("Minecraft was not detected yet. Click Play in CurseForge on: " + instance.getFileName());
            }
        }

        if (!watch) {
            return 0;
        }

        if (!ProcessWatcher.isMinecraftRunning()) {
            return 0;
        }
        System.out.println("Cleaning launcher traces...");
        JvmArgsInjector.uninstall(instance);
        CurseForgeArgsPatcher.uninstall(instance);
        System.out.println("Waiting for Minecraft to close...");
        ProcessWatcher.waitForMinecraftExit();
        StealthDeploy.cleanupMarker(MARKER);
        System.out.println("Cleanup complete.");
        return 0;
    }

    private static int cleanup(InjectOptions options) throws Exception {
        Optional<Path> instance = InstanceLocator.resolve(options.instance());
        if (instance.isPresent()) {
            Path resolved = instance.get();
            boolean removed = JvmArgsInjector.uninstall(resolved);
            boolean modRemoved = ModFolderDeploy.remove(resolved);
            boolean profileRemoved = CurseForgeArgsPatcher.uninstall(resolved);
            System.out.println(removed ? "Removed injector args from instance." : "No injector args found in instance.");
            System.out.println(modRemoved ? "Removed mod from instance mods folder." : "No mod jar in instance mods folder.");
            System.out.println(profileRemoved ? "Removed CurseForge javaArgsOverride." : "No CurseForge javaArgsOverride found.");
            List<String> restored = ModConflictResolver.restoreDisabledMods(instance.get());
            if (!restored.isEmpty()) {
                System.out.println("Restored mods:");
                for (String mod : restored) {
                    System.out.println(" - " + mod);
                }
            }
        }
        StealthDeploy.cleanupMarker(MARKER);
        System.out.println("Removed stealth payload.");
        return 0;
    }

    private static int launch(InjectOptions options) throws Exception {
        return inject(options, false);
    }

    private static int status(InjectOptions options) throws Exception {
        Optional<Path> instance = InstanceLocator.resolve(options.instance());
        if (instance.isEmpty()) {
            System.out.println("No instance resolved.");
        } else {
            Path resolved = instance.get();
            Path jvmArgs = JvmArgsInjector.jvmArgsFile(resolved);
            System.out.println("Instance: " + resolved);
            System.out.println("JVM args: " + jvmArgs + (Files.exists(jvmArgs) ? "" : " (missing)"));
            if (Files.exists(jvmArgs)) {
                String content = Files.readString(jvmArgs).trim();
                if (!content.isEmpty()) {
                    System.out.println("JVM args content:");
                    for (String line : content.split("\\R")) {
                        System.out.println(" - " + line);
                    }
                }
            }
            Path modsJar = resolved.resolve("mods").resolve(ModFolderDeploy.MOD_FILE_NAME);
            Path legacyJar = resolved.resolve("mods").resolve(ModFolderDeploy.LEGACY_MOD_FILE_NAME);
            if (Files.exists(modsJar) || Files.exists(legacyJar)) {
                System.out.println("Mods folder ghost jar: present (should be removed after stealth inject)");
            } else {
                System.out.println("Mods folder ghost jar: not present");
            }
            List<String> conflicts = ModConflictResolver.findConflictingMods(resolved);
            if (conflicts.isEmpty()) {
                System.out.println("Conflicting mods: none");
            } else {
                System.out.println("Conflicting mods still active:");
                for (String mod : conflicts) {
                    System.out.println(" - " + mod);
                }
            }
        }
        Path marker = StealthDeploy.readMarker(MARKER);
        System.out.println("Payload: " + (marker == null ? "not deployed" : marker));
        System.out.println("Minecraft running: " + ProcessWatcher.isMinecraftRunning());
        return 0;
    }

    private static Path resolveAgentJar(Path explicit) throws IOException {
        if (explicit != null) {
            if (!Files.isRegularFile(explicit)) {
                throw new IOException("Agent jar not found: " + explicit);
            }
            return explicit.toAbsolutePath().normalize();
        }
        try {
            Path codeSource = Path.of(GhostInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codeSource)) {
                return codeSource.toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {
        }
        throw new IOException("Could not determine " + INJECTOR_JAR + " path");
    }

    private static Path deployPayload(Path explicitClientJar) throws IOException {
        if (explicitClientJar != null) {
            if (!Files.isRegularFile(explicitClientJar)) {
                throw new IOException("Client jar not found: " + explicitClientJar);
            }
            Path stealth = GhostAgent.copyPayloadToStealth(explicitClientJar);
            StealthDeploy.writeMarker(MARKER, stealth);
            return stealth;
        }
        try (InputStream embedded = GhostAgent.class.getResourceAsStream("/embedded/navine-ghost-client.jar")) {
            if (embedded == null) {
                throw new IOException("Embedded client missing. Rebuild with :injector:build or pass --jar <path>.");
            }
            return StealthDeploy.deployEmbedded(embedded, MARKER);
        }
    }

    private static void printHelp() {
        System.out.println("""
                Navine-Ghost-Client

                Quick start:
                  Double-click Start Ghost.bat
                  java -jar Navine-Client-Ghost.jar

                Usage:
                  java -jar Navine-Client-Ghost.jar start [--instance <path>]
                  java -jar Navine-Client-Ghost.jar list
                  java -jar Navine-Client-Ghost.jar inject [--instance <path>]
                  java -jar Navine-Client-Ghost.jar launch [--instance <path>]
                  java -jar Navine-Client-Ghost.jar cleanup [--instance <path>]
                  java -jar Navine-Client-Ghost.jar status [--instance <path>]

                Commands:
                  start    Recommended. DLL-inject into Java + launch Minecraft
                  list     Show detected instances
                  inject   Deploy payload, inject ghost_java_inject.dll into Java
                  launch   Same as inject
                  run      Inject, auto-clean JVM args after launch
                  cleanup  Remove injector args and stealth payload
                  status   Show current injection state

                Native inject:
                  ghost_java_inject.dll  loaded into the Minecraft java/javaw process
                  ghost-dll-inject.exe   remote LoadLibrary helper

                Supported Minecraft versions: Fabric 26.1.x, 26.2, 26.3 (Java 25+)
                """);
    }

    private record InjectOptions(String command, Path instance, Path clientJar, Path agentJar) {
        static InjectOptions parse(String command, String[] args) {
            Path instance = null;
            Path clientJar = null;
            Path agentJar = null;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if ("--instance".equals(arg) && i + 1 < args.length) {
                    instance = Path.of(args[++i]);
                } else if ("--jar".equals(arg) && i + 1 < args.length) {
                    clientJar = Path.of(args[++i]);
                } else if ("--agent".equals(arg) && i + 1 < args.length) {
                    agentJar = Path.of(args[++i]);
                }
            }
            return new InjectOptions(command, instance, clientJar, agentJar);
        }
    }
}
