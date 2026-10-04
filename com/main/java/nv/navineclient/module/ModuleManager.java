package nv.navineclient.module;

import nv.navineclient.AddonManager;
import nv.navineclient.NavineClient;
import nv.navineclient.module.combat.*;
import nv.navineclient.module.movement.*;
import nv.navineclient.module.player.*;
import nv.navineclient.module.render.*;
import nv.navineclient.module.world.*;
import nv.navineclient.module.misc.*;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) return;
        initialized = true;

        if (NavineClient.isGhost()) {
            registerGhostModules();
        } else {
            registerFullModules();
        }

        modules.sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
    }

    private static void registerGhostModules() {
        modules.add(new AimAssist());
        modules.add(new AutoMacer());
        modules.add(new KillAura());
        modules.add(new AutoClicker());
        modules.add(new AutoShield());
        modules.add(new AutoTotem());
        modules.add(new BowAimbot());
        modules.add(new BowSpam());
        modules.add(new Criticals());
        modules.add(new Hitboxes());
        modules.add(new KnockbackPlus());
        modules.add(new Reach());
        modules.add(new ShieldBypass());
        modules.add(new Velocity());
        modules.add(new WTap());

        modules.add(new NoSlow());
        modules.add(new SafeWalk());
        modules.add(new Sprint());
        modules.add(new Step());

        modules.add(new AntiAfk());
        modules.add(new AntiHunger());
        modules.add(new AutoFish());
        modules.add(new AutoTool());
        modules.add(new ChestStealer());
        modules.add(new FastPlace());
        modules.add(new InventoryWalk());
        modules.add(new SpeedMine());

        modules.add(new ESP());
        modules.add(new FullBright());
        modules.add(new HUD());
        modules.add(new Nametags());
        modules.add(new NoHurtCam());
        modules.add(new NoRender());
        modules.add(new Radar());
        modules.add(new StorageESP());
        modules.add(new TargetHUD());
        modules.add(new Tracer());
        modules.add(new Trajectories());
        modules.add(new Zoom());

        modules.add(new PacketMine());
        modules.add(new BedDestroyer());
        modules.add(new Scaffold());

        modules.add(new LegitMode());
    }

    private static void registerFullModules() {
        modules.add(new KillAura());
        modules.add(new AutoClicker());
        modules.add(new AutoMacer());
        modules.add(new AutoShield());
        modules.add(new AutoTotem());
        modules.add(new BowAimbot());
        modules.add(new BowSpam());
        modules.add(new Criticals());
        modules.add(new GhostHand());
        modules.add(new Hitboxes());
        modules.add(new KnockbackPlus());
        modules.add(new Reach());
        modules.add(new ShieldBypass());
        modules.add(new Velocity());

        modules.add(new SafeWalk());
        modules.add(new SafeMine());
        modules.add(new Sprint());
        modules.add(new Step());
        modules.add(new FastLadder());
        modules.add(new NoSlow());
        modules.add(new BaritoneModule());

        modules.add(new AirPlace());
        modules.add(new AntiAfk());
        modules.add(new AntiCrash());
        modules.add(new AntiHunger());
        modules.add(new AutoFish());
        modules.add(new AutoLog());
        modules.add(new AutoReconnect());
        modules.add(new AutoRespawn());
        modules.add(new AutoTool());
        modules.add(new ChestStealer());
        modules.add(new FastPlace());
        modules.add(new SpeedMine());
        modules.add(new InventoryWalk());
        modules.add(new PortalChat());

        modules.add(new Breadcrumbs());
        modules.add(new ESP());
        modules.add(new FullBright());
        modules.add(new HUD());
        modules.add(new Nametags());
        modules.add(new NoFire());
        modules.add(new NoHurtCam());
        modules.add(new NoRender());
        modules.add(new Radar());
        modules.add(new StorageESP());
        modules.add(new TargetHUD());
        modules.add(new Tracer());
        modules.add(new Trajectories());
        modules.add(new Waypoints());
        modules.add(new Zoom());

        modules.add(new AutoMine());
        modules.add(new BedDestroyer());
        modules.add(new CustomSky());
        modules.add(new PacketMine());
        modules.add(new Scaffold());
        modules.add(new VeinMiner());
        modules.add(new Weather());

        modules.add(new nv.navineclient.module.world.DevMod());
        modules.add(new DiscordRPC());
        modules.add(new LegitMode());
        modules.add(new PacketLogger());
    }
    
    public static List<Module> getModules() {
        return modules;
    }
    
    public static List<Module> getModulesByCategory(Module.Category category) {
        List<Module> categoryModules = new ArrayList<>();
        for (Module m : modules) {
            if (!m.usesCustomCategory() && m.getCategory() == category && isModuleAccessible(m)) {
                categoryModules.add(m);
            }
        }
        return categoryModules;
    }

    public static List<Module> getModulesByCategoryKey(CategoryKey key) {
        if (key == null) return List.of();
        if (key.isCustom()) {
            List<Module> categoryModules = new ArrayList<>();
            for (Module m : modules) {
                if (m.usesCustomCategory() && key.getCustomId().equals(m.getCustomCategoryId()) && isModuleAccessible(m)) {
                    categoryModules.add(m);
                }
            }
            return categoryModules;
        }
        return getModulesByCategory(key.getBuiltin());
    }

    public static boolean isModuleAccessible(Module module) {
        if (module == null) return false;
        if (!module.isVisibleInClickGui()) return false;
        return !AddonManager.isModuleFromDisabledAddon(module);
    }

    public static boolean canToggleModule(Module module) {
        if (!nv.navineclient.util.AuthManager.isVerified()) {
            return false;
        }
        return isModuleAccessible(module);
    }
    
    public static Module getModuleByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.equalsIgnoreCase("NavinePathfinder")
                || normalized.equalsIgnoreCase("Baritone")
                || normalized.equalsIgnoreCase("BaritoneModule")) {
            normalized = "Pathfinder";
        }
        String lookup = normalized;
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(lookup))
                .findFirst()
                .orElse(null);
    }
    
    public static void registerModule(Module module) {
        if (module == null) return;
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(module.getName())) {
                nv.navineclient.NavineClient.LOGGER.warn("Module " + module.getName() + " is already registered, skipping addon module");
                return;
            }
        }
        modules.add(module);
        modules.sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
    }
}
