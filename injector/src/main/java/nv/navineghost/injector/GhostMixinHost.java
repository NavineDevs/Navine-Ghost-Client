package nv.navineghost.injector;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GhostMixinHost {
    private static final AtomicBoolean pumpRunning = new AtomicBoolean(false);
    private static final AtomicBoolean tickLogged = new AtomicBoolean(false);

    private GhostMixinHost() {
    }

    public static boolean bootstrap(Instrumentation instrumentation, Path payload, ClassLoader gameLoader, LogSink log) {
        if (instrumentation == null || payload == null || !Files.isRegularFile(payload)) {
            log.log("mixin host: missing instrumentation or payload");
            return false;
        }
        try {
            ClassLoader mixinLoader = gameLoader != null ? gameLoader : GhostMixinHost.class.getClassLoader();
            boolean sponge = trySpongeMixin(instrumentation, mixinLoader, payload, log);
            if (!sponge) {
                ClassLoader agentCl = GhostMixinHost.class.getClassLoader();
                if (agentCl != mixinLoader) {
                    sponge = trySpongeMixin(instrumentation, agentCl, payload, log);
                }
            }
            if (!sponge) {
                log.log("mixin host: sponge mixin unavailable — registering passthrough + retransform only");
            }

            retransformLoaded(instrumentation, log);
            log.log("mixin host: ready sponge=" + sponge);
            return true;
        } catch (Throwable error) {
            log.log("mixin host failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    private static boolean trySpongeMixin(Instrumentation instrumentation, ClassLoader loader, Path payload, LogSink log) {
        try {
            Class<?> bootstrap = Class.forName("org.spongepowered.asm.launch.MixinBootstrap", true, loader);
            Method init = bootstrap.getMethod("init");
            init.invoke(null);
            log.log("mixin host: MixinBootstrap.init ok");
        } catch (Throwable error) {
            log.log("mixin host: MixinBootstrap missing: " + error.getClass().getSimpleName());
            return false;
        }

        try {
            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins", true, loader);
            Method add = mixins.getMethod("addConfiguration", String.class);
            add.invoke(null, "navine-client.mixins.json");
            log.log("mixin host: registered navine-client.mixins.json");
        } catch (Throwable error) {
            try {
                Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins", true, loader);
                Method add = mixins.getMethod("addConfiguration", String.class);
                add.invoke(null, "navuryx-client.mixins.json");
                log.log("mixin host: registered navuryx-client.mixins.json");
            } catch (Throwable error2) {
                log.log("mixin host: Mixins.addConfiguration failed: " + error.getMessage());
            }
        }

        try {
            Class<?> transformerCls = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer", true, loader);
            Object transformer = transformerCls.getDeclaredConstructor().newInstance();
            Method transform = findTransform(transformerCls);
            if (transform != null) {
                instrumentation.addTransformer(new ClassFileTransformer() {
                    @Override
                    public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined,
                                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                        if (className == null || classfileBuffer == null) {
                            return null;
                        }
                        try {
                            Object result = transform.invoke(transformer, classLoader, className, classBeingRedefined,
                                    protectionDomain, classfileBuffer);
                            if (result instanceof byte[] bytes && bytes != classfileBuffer) {
                                return bytes;
                            }
                        } catch (Throwable ignored) {
                        }
                        return null;
                    }
                }, true);
                log.log("mixin host: MixinTransformer hooked");
            }
        } catch (Throwable error) {
            log.log("mixin host: MixinTransformer hook skipped: " + error.getClass().getSimpleName());
        }
        return true;
    }

    private static Method findTransform(Class<?> transformerCls) {
        for (Method m : transformerCls.getMethods()) {
            if (m.getName().equals("transform") && m.getParameterCount() >= 5) {
                return m;
            }
        }
        for (Method m : transformerCls.getDeclaredMethods()) {
            if (m.getName().equals("transform")) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static void retransformLoaded(Instrumentation instrumentation, LogSink log) {
        String[] priority = {
                "net.minecraft.client.gui.Gui",
                "net.minecraft.client.renderer.GameRenderer",
                "net.minecraft.client.renderer.LevelRenderer",
                "net.minecraft.world.entity.LivingEntity",
                "net.minecraft.client.renderer.entity.LivingEntityRenderer",
                "net.minecraft.client.gui.screens.ChatScreen",
                "net.minecraft.client.multiplayer.ClientPacketListener",
                "net.minecraft.world.entity.player.Player",
                "net.minecraft.client.player.LocalPlayer",
                "net.minecraft.client.Minecraft",
                "net.minecraft.client.multiplayer.MultiPlayerGameMode",
                "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
                "net.minecraft.world.level.block.state.BlockState",
                "net.minecraft.world.level.block.Block"
        };
        int priorityOk = retransformPriority(instrumentation, log, priority, "pass1");
        priorityOk += retransformPriority(instrumentation, log, priority, "pass2");

        List<Class<?>> targets = new ArrayList<>();
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded == null) {
                continue;
            }
            String n = loaded.getName();
            if (!n.startsWith("net.minecraft.") && !n.startsWith("com.mojang.")) {
                continue;
            }
            if (!instrumentation.isModifiableClass(loaded)) {
                continue;
            }
            targets.add(loaded);
        }
        log.log("mixin host: priorityOk=" + priorityOk + " retransform candidates=" + targets.size());
        int ok = 0;
        int batch = 32;
        for (int i = 0; i < targets.size(); i += batch) {
            int end = Math.min(i + batch, targets.size());
            Class<?>[] slice = targets.subList(i, end).toArray(new Class<?>[0]);
            try {
                instrumentation.retransformClasses(slice);
                ok += slice.length;
            } catch (Throwable error) {
                for (Class<?> one : slice) {
                    try {
                        instrumentation.retransformClasses(one);
                        ok++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        retransformPriority(instrumentation, log, priority, "pass3");
        log.log("mixin host: retransform ok=" + ok + " mixinApplied=" + (ok > 0 || priorityOk > 0));
    }

    private static int retransformPriority(Instrumentation instrumentation, LogSink log, String[] priority, String label) {
        int priorityOk = 0;
        for (String name : priority) {
            try {
                Class<?> cls = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
                if (instrumentation.isModifiableClass(cls)) {
                    instrumentation.retransformClasses(cls);
                    priorityOk++;
                    log.log("mixin host: priority " + label + " " + name);
                    continue;
                }
            } catch (Throwable ignored) {
            }
            for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                if (loaded != null && name.equals(loaded.getName()) && instrumentation.isModifiableClass(loaded)) {
                    try {
                        instrumentation.retransformClasses(loaded);
                        priorityOk++;
                        log.log("mixin host: priority " + label + " " + name);
                    } catch (Throwable ignored2) {
                    }
                    break;
                }
            }
        }
        return priorityOk;
    }

    public static boolean startTickPump(ClassLoader runtimeLoader, LogSink log) {
        if (runtimeLoader == null) {
            log.log("tick pump: no classloader");
            return false;
        }
        log.log("tickClassLoader=" + runtimeLoader.getClass().getName() + "@" + System.identityHashCode(runtimeLoader));
        if (!pumpRunning.compareAndSet(false, true)) {
            log.log("tick pump already running");
            return true;
        }
        Thread pump = new Thread(() -> {
            log.log("tick pump started");
            for (int i = 0; i < 20 * 60 * 60; i++) {
                try {
                    Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", false, runtimeLoader);
                    Method getInstance = null;
                    for (String name : new String[]{"getInstance", "method_1551"}) {
                        try {
                            getInstance = mcClass.getMethod(name);
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (getInstance == null) {
                        Thread.sleep(250);
                        continue;
                    }
                    Object mc = getInstance.invoke(null);
                    if (mc == null) {
                        Thread.sleep(100);
                        continue;
                    }
                    Method execute = null;
                    for (String name : new String[]{"execute", "method_20493", "tell"}) {
                        try {
                            execute = mcClass.getMethod(name, Runnable.class);
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (execute == null) {
                        Thread.sleep(250);
                        continue;
                    }
                    execute.invoke(mc, (Runnable) () -> runTickReflect(runtimeLoader, log));
                    Thread.sleep(16);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable ignored) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "ghost-tick-pump");
        pump.setDaemon(true);
        pump.start();
        return true;
    }

    private static void runTickReflect(ClassLoader cl, LogSink log) {
        if (cl == null) {
            return;
        }
        String[] pipelines = {
                "nv.navineclient.TickPipeline",
                "nv.navuryxclient.TickPipeline"
        };
        for (String pipelineName : pipelines) {
            try {
                Class<?> pipeline = Class.forName(pipelineName, true, cl);
                Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", true, cl);
                Method getInstance = null;
                for (String name : new String[]{"getInstance", "method_1551"}) {
                    try {
                        getInstance = mcClass.getMethod(name);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (getInstance == null) {
                    continue;
                }
                Object mc = getInstance.invoke(null);
                pipeline.getMethod("onStartTick", mcClass).invoke(null, mc);
                pipeline.getMethod("onEndTick", mcClass).invoke(null, mc);
                System.setProperty("ghost.tick.fired", "true");
                if (tickLogged.compareAndSet(false, true)) {
                    log.log("tickFired=true tickClassLoader=" + cl.getClass().getName());
                }
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface LogSink {
        void log(String message);
    }
}

