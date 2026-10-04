package nv.navineghost.injector;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

public final class GhostAgent {
    private static final String EMBEDDED_FABRIC = "/embedded/navine-ghost-client.jar";
    private static final String EMBEDDED_VANILLA = "/embedded/navine-ghost-client-vanilla.jar";
    private static final String MARKER = "navine-ghost-agent";
    private static final String MARKER_VANILLA = "navine-ghost-agent-vanilla";
    private static final String LOG_NAME = "ghost-java-inject.log";
    private static final String[] CLIENTS = {
            "nv.navineclient.NavineClient",
            "nv.navuryxclient.NavuryxClient"
    };
    private static final String[] BOOTSTRAPS = {
            "nv.navineclient.GhostBootstrap",
            "nv.navuryxclient.GhostBootstrap"
    };

    private static volatile Instrumentation instrumentation;
    private static final AtomicBoolean bootstrapped = new AtomicBoolean(false);
    private static volatile ClassLoader runtimeLoader;
    private static volatile boolean mixinApplied = false;
    private static volatile boolean tickPumpStarted = false;

    private GhostAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        GhostAgent.instrumentation = instrumentation;
        inject(agentArgs);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        GhostAgent.instrumentation = instrumentation;
        inject(agentArgs);
    }

    public static void inject(String agentArgs) {
        try {
            System.setProperty("ghost.openAccess", "true");
            log("inject begin instrumentation=" + (instrumentation != null)
                    + " args=" + (agentArgs == null ? "" : agentArgs));
            ClassLoader gameLoader = findGameClassLoader();
            LoaderChoice choice = resolveLoader(gameLoader, agentArgs);
            String loaderKind = choice.kind;
            log("loader=" + loaderKind + " forced=" + choice.forced
                    + " detect=" + choice.detected
                    + " gameLoader=" + (gameLoader == null ? "null" : gameLoader.getClass().getName()));

            Path payload = resolvePayload(agentArgs, loaderKind);
            log("payload=" + payload + " exists=" + Files.isRegularFile(payload) + " mode=" + loaderKind);
            registerUniversal(payload, loaderKind);

            ClassLoader mcLoader = findMinecraftClassLoader(gameLoader);
            log("mcLoader=" + (mcLoader == null ? "null" : mcLoader.getClass().getName()));

            if (runtimeLoader == null) {
                runtimeLoader = findExistingRuntime(mcLoader);
            }
            boolean already = bootstrapped.get() || runtimeLoader != null && pipelineVisible(runtimeLoader);
            if (already) {
                log("alreadyBootstrapped skipConfigReload");
                bootstrapped.set(true);
            } else {
                ClassLoader bound = bindPayload(payload, mcLoader, loaderKind);
                if (instrumentation != null) {
                    mixinApplied = GhostMixinHost.bootstrap(instrumentation, payload, bound != null ? bound : mcLoader, GhostAgent::log)
                            || mixinApplied;
                } else {
                    log("mixin host: skipped (no Instrumentation on DLL-only path)");
                }
                boolean startedNow = bootstrapOn(bound != null ? bound : mcLoader, payload);
                if (!startedNow) {
                    startedNow = scheduleOnMinecraftThread(payload, bound != null ? bound : mcLoader);
                }
                already = false;
                if (!startedNow && !bootstrapped.get()) {
                    log("ghost done clientStarted=false");
                    return;
                }
            }

            ClassLoader tickCl = runtimeLoader != null ? runtimeLoader : mcLoader;
            log("tickClassLoader=" + (tickCl == null ? "null" : tickCl.getClass().getName() + "@" + System.identityHashCode(tickCl)));
            if (tickCl != null) {
                ensureTicksRegistered(tickCl);
                boolean pump = GhostMixinHost.startTickPump(tickCl, GhostAgent::log);
                tickPumpStarted = tickPumpStarted || pump;
            }
            boolean tickFired = waitForTickFired(8000);
            log("tickPump=" + tickPumpStarted + " tickFired=" + tickFired);

            String tickDriven = buildTickDrivenLabel(tickPumpStarted);
            log("ghost done loader=" + loaderKind + " forced=" + choice.forced
                    + " clientStarted=" + bootstrapped.get()
                    + " alreadyBootstrapped=" + already
                    + " mixinApplied=" + mixinApplied
                    + " tickPump=" + tickPumpStarted
                    + " tickFired=" + tickFired
                    + " tickDriven=" + tickDriven);
        } catch (Throwable error) {
            log("injection failed: " + error.getClass().getName() + ": " + error.getMessage());
            for (Throwable c = error.getCause(); c != null; c = c.getCause()) {
                log("  cause: " + c.getClass().getSimpleName() + ": " + c.getMessage());
            }
        }
    }

    private static String buildTickDrivenLabel(boolean pump) {
        StringBuilder sb = new StringBuilder();
        if (pump || tickPumpStarted) {
            sb.append("pump");
        }
        if (mixinApplied) {
            if (sb.length() > 0) {
                sb.append('+');
            }
            sb.append("mixin");
        }
        if (sb.length() == 0) {
            sb.append("events?");
        }
        return sb.toString();
    }

    private static void ensureTicksRegistered(ClassLoader cl) {
        if (cl == null) {
            return;
        }
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(cl);
            for (String name : CLIENTS) {
                try {
                    Class<?> type = Class.forName(name, true, cl);
                    Method ensure = type.getMethod("ensureClientRuntime");
                    ensure.invoke(null);
                    log("ensureClientRuntime ok via " + name);
                    return;
                } catch (NoSuchMethodException missing) {
                    try {
                        Method reg = Class.forName(name, true, cl).getDeclaredMethod("tryRegisterFabricTicks");
                        reg.setAccessible(true);
                        reg.invoke(null);
                        log("tryRegisterFabricTicks ok via " + name);
                        return;
                    } catch (Throwable ignored) {
                    }
                } catch (ClassNotFoundException ignored) {
                } catch (Throwable error) {
                    log("ensureTicks " + name + " failed: " + error.getClass().getSimpleName());
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private static Path resolvePayload(String agentArgs, String loaderKind) throws IOException {
        List<String> candidates = new ArrayList<>();
        if (agentArgs != null && !agentArgs.isBlank()) {
            for (String part : agentArgs.split("[;|]")) {
                if (!part.isBlank()) {
                    candidates.add(part.trim());
                }
            }
        }
        String prop = System.getProperty("ghost.payload", "");
        if (prop != null && !prop.isBlank()) {
            candidates.add(prop.trim());
        }
        String propV = System.getProperty("ghost.payload.vanilla", "");
        String propF = System.getProperty("ghost.payload.fabric", "");
        if ("fabric".equals(loaderKind) && propF != null && !propF.isBlank()) {
            candidates.add(0, propF.trim());
        }
        if (!"fabric".equals(loaderKind) && propV != null && !propV.isBlank()) {
            candidates.add(0, propV.trim());
        }
        if (!"fabric".equals(loaderKind)) {
            for (String c : candidates) {
                try {
                    Path p = Path.of(c);
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.contains("vanilla") || name.contains("payload-v") || name.contains("-v-")) {
                        log("selected vanilla payload candidate: " + p);
                        return p.toAbsolutePath().normalize();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (candidates.size() >= 2) {
                try {
                    Path p = Path.of(candidates.get(candidates.size() - 1));
                    if (Files.isRegularFile(p)) {
                        log("selected trailing dual-path vanilla payload: " + p);
                        return p.toAbsolutePath().normalize();
                    }
                } catch (Throwable ignored) {
                }
            }
        } else {
            for (String c : candidates) {
                try {
                    Path p = Path.of(c);
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.contains("vanilla")) {
                        continue;
                    }
                    log("selected fabric payload candidate: " + p);
                    return p.toAbsolutePath().normalize();
                } catch (Throwable ignored) {
                }
            }
        }
        for (String c : candidates) {
            try {
                Path p = Path.of(c);
                if (Files.isRegularFile(p)) {
                    return p.toAbsolutePath().normalize();
                }
                log("payload candidate missing: " + c);
            } catch (Throwable ignored) {
            }
        }
        String embedded = "fabric".equals(loaderKind) ? EMBEDDED_FABRIC : EMBEDDED_VANILLA;
        String marker = "fabric".equals(loaderKind) ? MARKER : MARKER_VANILLA;
        InputStream stream = GhostAgent.class.getResourceAsStream(embedded);
        if (stream == null && !"fabric".equals(loaderKind)) {
            stream = GhostAgent.class.getResourceAsStream(EMBEDDED_FABRIC);
            marker = MARKER;
            log("vanilla embedded missing — falling back to fabric payload");
        }
        if (stream == null) {
            throw new IOException("Embedded client payload missing from agent jar");
        }
        try (InputStream in = stream) {
            Path embeddedPath = StealthDeploy.deployEmbedded(in, marker);
            log("using embedded deploy: " + embeddedPath);
            return embeddedPath;
        }
    }

    private static void attachJar(Path payload) {
        if (instrumentation == null || payload == null || !Files.isRegularFile(payload)) {
            return;
        }
        try {
            instrumentation.appendToSystemClassLoaderSearch(new JarFile(payload.toFile()));
            log("attached to system classloader");
        } catch (Throwable error) {
            log("system attach failed: " + error.getMessage());
        }
    }

    private static ClassLoader findMinecraftClassLoader(ClassLoader fallback) {
        ClassLoader[] search = {
                fallback,
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : search) {
            if (cl == null) {
                continue;
            }
            try {
                Class<?> mc = Class.forName("net.minecraft.client.Minecraft", false, cl);
                Method getInstance = null;
                for (String name : new String[]{"getInstance", "method_1551"}) {
                    try {
                        getInstance = mc.getMethod(name);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (getInstance != null) {
                    Object inst = getInstance.invoke(null);
                    if (inst != null && inst.getClass().getClassLoader() != null) {
                        return inst.getClass().getClassLoader();
                    }
                }
                if (mc.getClassLoader() != null) {
                    return mc.getClassLoader();
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback != null ? fallback : ClassLoader.getSystemClassLoader();
    }

    private static boolean pipelineVisible(ClassLoader cl) {
        if (cl == null) {
            return false;
        }
        String[] names = {
                "nv.navineclient.TickPipeline",
                "nv.navuryxclient.TickPipeline",
                "nv.navineclient.GhostBootstrap",
                "nv.navuryxclient.GhostBootstrap"
        };
        for (String name : names) {
            try {
                Class.forName(name, false, cl);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static ClassLoader findExistingRuntime(ClassLoader preferred) {
        ClassLoader[] scan = {
                runtimeLoader,
                preferred,
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : scan) {
            if (cl != null && pipelineVisible(cl)) {
                return cl;
            }
        }
        if (instrumentation != null) {
            try {
                for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                    if (loaded == null) {
                        continue;
                    }
                    String n = loaded.getName();
                    if ("nv.navineclient.TickPipeline".equals(n)
                            || "nv.navuryxclient.TickPipeline".equals(n)
                            || "nv.navineclient.GhostBootstrap".equals(n)
                            || "nv.navuryxclient.GhostBootstrap".equals(n)
                            || "nv.navineclient.module.ModuleManager".equals(n)
                            || "nv.navuryxclient.module.ModuleManager".equals(n)) {
                        ClassLoader found = loaded.getClassLoader();
                        if (found != null) {
                            return found;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isBootstrapStarted(ClassLoader cl) {
        if (cl == null) {
            return false;
        }
        for (String name : BOOTSTRAPS) {
            try {
                Class<?> type = Class.forName(name, false, cl);
                Method started = type.getMethod("isStarted");
                Object value = started.invoke(null);
                if (Boolean.TRUE.equals(value)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static ClassLoader bindPayload(Path payload, ClassLoader mcLoader, String loaderKind) {
        ClassLoader existing = findExistingRuntime(mcLoader);
        if (existing != null) {
            runtimeLoader = existing;
            log("runtimeLoader reused " + existing.getClass().getName());
            return existing;
        }
        URL url;
        try {
            url = payload.toUri().toURL();
        } catch (Throwable error) {
            log("payload url failed: " + error.getMessage());
            return mcLoader;
        }
        if (mcLoader != null && tryAddUrl(mcLoader, url) && pipelineVisible(mcLoader)) {
            runtimeLoader = mcLoader;
            log("bound payload on game loader");
            return mcLoader;
        }
        if (!"fabric".equals(loaderKind)) {
            attachJar(payload);
            ClassLoader sys = ClassLoader.getSystemClassLoader();
            if (pipelineVisible(sys)) {
                runtimeLoader = sys;
                log("bound payload on system classloader");
                return sys;
            }
            if (mcLoader != null && pipelineVisible(mcLoader)) {
                runtimeLoader = mcLoader;
                log("bound payload visible on game loader after append");
                return mcLoader;
            }
            runtimeLoader = sys != null ? sys : mcLoader;
            log("vanilla bind using " + (runtimeLoader == null ? "null" : runtimeLoader.getClass().getName()));
            return runtimeLoader;
        }
        try {
            URLClassLoader child = new URLClassLoader(new URL[]{url}, mcLoader != null ? mcLoader : ClassLoader.getSystemClassLoader());
            runtimeLoader = child;
            log("bound payload on child URLClassLoader");
            return child;
        } catch (Throwable error) {
            log("child loader failed: " + error.getMessage());
            runtimeLoader = mcLoader;
            return mcLoader;
        }
    }

    private static boolean bootstrapOn(ClassLoader cl, Path payload) {
        if (cl == null) {
            log("no game classloader");
            return false;
        }
        if (runtimeLoader == null) {
            runtimeLoader = cl;
        }
        ClassLoader target = runtimeLoader != null ? runtimeLoader : cl;
        if (bootstrapped.get() || isBootstrapStarted(target)) {
            bootstrapped.set(true);
            log("alreadyBootstrapped skipConfigReload");
            return true;
        }
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(target);
        try {
            for (String name : CLIENTS) {
                if (tryInit(name, target)) {
                    return true;
                }
            }
            for (String name : BOOTSTRAPS) {
                if (tryBootstrapStatic(name, target)) {
                    return true;
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
        return bootstrapped.get();
    }

    private static boolean waitForTickFired(long ms) {
        if ("true".equals(System.getProperty("ghost.tick.fired"))) {
            log("tickFired=true");
            return true;
        }
        long end = System.currentTimeMillis() + Math.max(0L, ms);
        while (System.currentTimeMillis() < end) {
            if ("true".equals(System.getProperty("ghost.tick.fired"))) {
                log("tickFired=true");
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log("tickFired=false");
        return false;
    }

    private static ClassLoader findGameClassLoader() {
        LinkedHashSet<ClassLoader> found = new LinkedHashSet<>();
        try {
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                ClassLoader cl = e.getKey().getContextClassLoader();
                if (cl == null) {
                    continue;
                }
                String n = cl.getClass().getName();
                if (n.contains("Knot") || n.contains("TransformingClassLoader")
                        || n.contains("ModClassLoader") || n.contains("Launcher")
                        || n.contains("AppClassLoader")) {
                    found.add(cl);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> knot = Class.forName("net.fabricmc.loader.impl.launch.knot.Knot");
            Object launcher = knot.getMethod("getLauncher").invoke(null);
            Object cl = launcher.getClass().getMethod("getTargetClassLoader").invoke(launcher);
            if (cl instanceof ClassLoader) {
                found.add((ClassLoader) cl);
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> fabric = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object fl = fabric.getMethod("getInstance").invoke(null);
            Object env = fabric.getMethod("getEnvironmentType").invoke(fl);
            log("fabric env=" + env);
        } catch (Throwable ignored) {
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            found.add(ctx);
        }
        for (ClassLoader cl : found) {
            if (cl.getClass().getName().contains("Knot")) {
                return cl;
            }
        }
        if (!found.isEmpty()) {
            return found.iterator().next();
        }
        return ctx != null ? ctx : ClassLoader.getSystemClassLoader();
    }

    private static boolean tryAddUrl(ClassLoader cl, URL url) {
        Class<?> c = cl.getClass();
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod("addURL", URL.class);
                m.setAccessible(true);
                m.invoke(cl, url);
                log("addURL ok on " + cl.getClass().getName());
                return true;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            } catch (Throwable error) {
                log("addURL fail " + cl.getClass().getName() + ": " + error.getMessage());
                return false;
            }
        }
        return false;
    }

    private static boolean tryInit(String name, ClassLoader cl) {
        try {
            Class<?> type = Class.forName(name, true, cl);
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();
            Method init = type.getMethod("onInitializeClient");
            init.invoke(instance);
            bootstrapped.set(true);
            log("client bootstrapped: " + name + " via " + cl.getClass().getName());
            return true;
        } catch (ClassNotFoundException missing) {
            log("class missing on " + cl.getClass().getName() + ": " + name);
            return false;
        } catch (Throwable error) {
            Throwable root = error;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log("bootstrap " + name + " failed: " + root.getClass().getSimpleName() + ": " + root.getMessage());
            if (root.getMessage() != null && root.getMessage().contains("ClientModInitializer")) {
                log("HINT: Fabric API is missing or not visible — install fabric-api in the Fabric instance");
            }
            return false;
        }
    }

    private static boolean tryBootstrapStatic(String name, ClassLoader cl) {
        try {
            Class<?> type = Class.forName(name, true, cl);
            Method init = type.getMethod("init");
            init.invoke(null);
            bootstrapped.set(true);
            log("client bootstrapped: " + name + ".init via " + cl.getClass().getName());
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        } catch (Throwable error) {
            Throwable root = error;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log("static bootstrap " + name + " failed: " + root.getClass().getSimpleName() + ": " + root.getMessage());
            return false;
        }
    }

    private static boolean scheduleOnMinecraftThread(Path payload, ClassLoader parent) {
        try {
            ClassLoader search = parent != null ? parent : findGameClassLoader();
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", false, search);
            Method getInstance = null;
            for (String m : new String[]{"getInstance", "method_1551"}) {
                try {
                    getInstance = mcClass.getMethod(m);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (getInstance == null) {
                log("Minecraft getInstance missing");
                return false;
            }
            Object mc = getInstance.invoke(null);
            if (mc == null) {
                log("Minecraft instance null");
                return false;
            }
            Method execute = null;
            for (String m : new String[]{"execute", "method_20493", "tell"}) {
                try {
                    execute = mcClass.getMethod(m, Runnable.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (execute == null) {
                log("Minecraft.execute missing");
                return false;
            }
            ClassLoader parentFinal = search;
            execute.invoke(mc, (Runnable) () -> bootstrapOn(parentFinal, payload));
            log("scheduled bootstrap on client thread");
            for (int i = 0; i < 40 && !bootstrapped.get(); i++) {
                Thread.sleep(100);
            }
            return bootstrapped.get();
        } catch (Throwable error) {
            log("schedule failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    static void registerUniversal(Path payload, String loaderKind) {
        String absolute = payload.toAbsolutePath().normalize().toString();
        System.setProperty("ghost.payload", absolute);
        System.setProperty("ghost.injected", "true");
        System.setProperty("ghost.loader", loaderKind);
        if ("fabric".equals(loaderKind)) {
            appendProperty("fabric.addMods", absolute);
            System.setProperty("ghost.payload.fabric", absolute);
        } else {
            System.setProperty("ghost.payload.vanilla", absolute);
        }
    }

    private static void appendProperty(String key, String absolute) {
        String existing = System.getProperty(key, "");
        if (existing == null || existing.isBlank()) {
            System.setProperty(key, absolute);
            return;
        }
        String sep = java.io.File.pathSeparator;
        for (String entry : existing.split(sep)) {
            if (absolute.equalsIgnoreCase(entry.trim())) {
                return;
            }
        }
        System.setProperty(key, existing + sep + absolute);
    }

    private static final class LoaderChoice {
        final String kind;
        final String detected;
        final boolean forced;

        LoaderChoice(String kind, String detected, boolean forced) {
            this.kind = kind;
            this.detected = detected;
            this.forced = forced;
        }
    }

    private static LoaderChoice resolveLoader(ClassLoader preferred, String agentArgs) {
        String detected = detectLoader(preferred);
        String forced = readForceLoader(agentArgs);
        if (forced != null) {
            System.setProperty("ghost.force.loader", forced);
            return new LoaderChoice(forced, detected, true);
        }
        String kind = detected;
        if ("forge".equals(kind) || "neoforge".equals(kind)) {
            kind = "vanilla";
        }
        return new LoaderChoice(kind, detected, false);
    }

    private static String readForceLoader(String agentArgs) {
        String force = normalizeForce(System.getProperty("ghost.force.loader", ""));
        if (force != null) {
            return force;
        }
        force = normalizeForce(System.getenv("GHOST_FORCE_LOADER"));
        if (force != null) {
            return force;
        }
        if (agentArgs != null && !agentArgs.isBlank()) {
            for (String part : agentArgs.split("[;|]")) {
                String p = part.trim();
                if (p.regionMatches(true, 0, "force=", 0, 6)) {
                    force = normalizeForce(p.substring(6));
                    if (force != null) {
                        return force;
                    }
                }
                if ("fabric".equalsIgnoreCase(p) || "vanilla".equalsIgnoreCase(p)) {
                    if (!looksLikePath(p)) {
                        force = normalizeForce(p);
                        if (force != null) {
                            return force;
                        }
                    }
                }
            }
        }
        force = normalizeForce(readForceFromInjectConfig());
        return force;
    }

    private static boolean looksLikePath(String value) {
        return value.contains("/") || value.contains("\\") || value.contains(".") || value.contains(":");
    }

    private static String normalizeForce(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("force=")) {
            v = v.substring(6).trim();
        }
        if ("fabric".equals(v) || "f".equals(v)) {
            return "fabric";
        }
        if ("vanilla".equals(v) || "v".equals(v) || "official".equals(v)) {
            return "vanilla";
        }
        return null;
    }

    private static String readForceFromInjectConfig() {
        try {
            String env = System.getenv("GHOST_JAVA_INJECT_CFG");
            Path cfg = (env != null && !env.isBlank())
                    ? Path.of(env)
                    : Path.of(System.getProperty("java.io.tmpdir", ".")).resolve("ghost-java-inject.cfg");
            if (!Files.isRegularFile(cfg)) {
                return null;
            }
            List<String> lines = Files.readAllLines(cfg, StandardCharsets.UTF_8);
            if (lines.size() >= 5) {
                return lines.get(4);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String detectLoader(ClassLoader preferred) {
        if (classExists("net.fabricmc.loader.api.FabricLoader", preferred)
                || classExists("net.fabricmc.loader.impl.launch.knot.KnotClient", preferred)) {
            return "fabric";
        }
        String cmd = System.getProperty("sun.java.command", "").toLowerCase(Locale.ROOT);
        if (cmd.contains("fabric")) {
            return "fabric";
        }
        if (cmd.contains("forge") || cmd.contains("neoforge")) {
            return "forge";
        }
        return "vanilla";
    }

    private static boolean classExists(String name, ClassLoader preferred) {
        ClassLoader[] loaders = preferred != null
                ? new ClassLoader[]{preferred, ClassLoader.getSystemClassLoader()}
                : new ClassLoader[]{Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader()};
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try {
                Class.forName(name, false, cl);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void log(String message) {
        String line = "[ghost-inject] " + message;
        System.out.println(line);
        System.err.println(line);
        try {
            Path temp = Path.of(System.getProperty("java.io.tmpdir", ".")).resolve(LOG_NAME);
            Files.writeString(temp, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    static void registerFabricMod(Path payload) {
        registerUniversal(payload, detectLoader(null));
    }

    static Path copyPayloadToStealth(Path source) throws IOException {
        Path parent = StealthDeploy.stealthDirectory();
        Files.createDirectories(parent);
        Path target = parent.resolve(StealthDeploy.stealthFileName());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        StealthDeploy.touchAsLibrary(target);
        return target.toAbsolutePath().normalize();
    }
}
