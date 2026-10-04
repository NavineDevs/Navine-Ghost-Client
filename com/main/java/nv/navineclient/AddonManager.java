package nv.navineclient;

import nv.navineclient.commands.Command;
import nv.navineclient.commands.CommandManager;
import nv.navineclient.config.ConfigManager;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddonManager {
    private static final List<NavineAddon> addons = new ArrayList<>();
    private static final Set<String> disabledAddonPackages = new HashSet<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        addons.clear();
        disabledAddonPackages.clear();

        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Method getContainers = loaderClass.getMethod("getEntrypointContainers", String.class, Class.class);
            @SuppressWarnings("unchecked")
            List<?> containers = (List<?>) getContainers.invoke(loader, "navine", NavineAddon.class);

            for (Object container : containers) {
                try {
                    Object addonObj = container.getClass().getMethod("getEntrypoint").invoke(container);
                    if (!(addonObj instanceof NavineAddon addon)) {
                        continue;
                    }
                    addons.add(addon);
                    Object provider = container.getClass().getMethod("getProvider").invoke(container);
                    Object metadata = provider.getClass().getMethod("getMetadata").invoke(provider);
                    Object id = metadata.getClass().getMethod("getId").invoke(metadata);
                    NavineClient.LOGGER.info("Loaded addon: {} from {}", addon.getClass().getSimpleName(), id);
                } catch (Exception e) {
                    NavineClient.LOGGER.error("Failed to load addon container", e);
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError missing) {
            NavineClient.LOGGER.info("FabricLoader unavailable — skipping addon entrypoints (vanilla mode)");
            return;
        } catch (Throwable error) {
            NavineClient.LOGGER.warn("Addon discovery failed: {}", error.toString());
            return;
        }

        for (NavineAddon addon : addons) {
            try {
                addon.onRegisterCategories();
            } catch (Exception e) {
                NavineClient.LOGGER.error("Error registering categories for addon: " + addon.getClass().getSimpleName(), e);
            }
        }

        for (NavineAddon addon : addons) {
            try {
                addon.onInitialize();

                for (Command command : addon.getCommands()) {
                    CommandManager.registerCommand(command);
                }

                for (Module module : addon.getModules()) {
                    ModuleManager.registerModule(module);
                }

                NavineClient.LOGGER.info("Initialized addon: " + addon.getClass().getSimpleName()
                        + " (Commands: " + addon.getCommands().size() + ", Modules: " + addon.getModules().size() + ")");
            } catch (Exception e) {
                NavineClient.LOGGER.error("Error initializing addon: " + addon.getClass().getSimpleName(), e);
            }
        }
    }

    public static void registerCategories() {
        for (NavineAddon addon : addons) {
            try {
                addon.onRegisterCategories();
            } catch (Exception e) {
                NavineClient.LOGGER.error("Error registering categories for addon: " + addon.getClass().getSimpleName(), e);
            }
        }
    }

    public static List<NavineAddon> getAddons() {
        return new ArrayList<>(addons);
    }

    public static NavineAddon getAddon(String packageName) {
        return addons.stream()
                .filter(addon -> addon.getPackage().equals(packageName))
                .findFirst()
                .orElse(null);
    }

    public static boolean hasAddons() {
        return !addons.isEmpty();
    }

    public static boolean isAddonEnabled(NavineAddon addon) {
        if (addon == null) {
            return false;
        }
        return !disabledAddonPackages.contains(addon.getPackage());
    }

    public static void loadEnabledState(String packageName, boolean enabled) {
        if (enabled) {
            disabledAddonPackages.remove(packageName);
        } else {
            disabledAddonPackages.add(packageName);
            NavineAddon addon = getAddon(packageName);
            if (addon != null) {
                for (Module module : addon.getModules()) {
                    if (module.isEnabled()) {
                        module.setEnabled(false);
                    }
                }
            }
        }
    }

    public static void setAddonEnabled(NavineAddon addon, boolean enabled) {
        if (addon == null) {
            return;
        }
        if (enabled) {
            disabledAddonPackages.remove(addon.getPackage());
        } else {
            disabledAddonPackages.add(addon.getPackage());
            for (Module module : addon.getModules()) {
                if (module.isEnabled()) {
                    module.setEnabled(false);
                }
            }
        }
        ConfigManager.save();
    }

    public static boolean isModuleFromDisabledAddon(Module module) {
        for (NavineAddon addon : addons) {
            if (addon.getModules().contains(module) && !isAddonEnabled(addon)) {
                return true;
            }
        }
        return false;
    }
}
