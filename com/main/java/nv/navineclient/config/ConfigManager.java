package nv.navineclient.config;

import nv.navineclient.AddonManager;
import nv.navineclient.NavineAddon;
import nv.navineclient.NavineClient;
import nv.navineclient.util.AuthManager;
import nv.navineclient.module.CategoryKey;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.Setting;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.ColorSetting;
import net.minecraft.client.Minecraft;
import nv.navineclient.util.NavineInput;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private static File configDir;
    private static File moduleConfig;
    private static final Map<String, int[]> clickGuiTabPositions = new HashMap<>();
    private static final LinkedHashSet<String> favoriteModules = new LinkedHashSet<>();
    private static boolean autoLogin = true;
    private static int modulesVisible = 11;
    private static boolean hudTopLeft = true;
    private static boolean notificationCenterHotbar = true;
    private static String cachedSessionUser;

    private static File resolveGameRoot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameDirectory != null) {
            return mc.gameDirectory;
        }
        return new File(".");
    }

    private static void refreshConfigPaths() {
        File root = resolveGameRoot();
        File nextDir = new File(root, "navine");
        File nextConfig = new File(nextDir, "modules.txt");
        File legacyConfig = new File(new File(root, "navuryx"), "modules.txt");
        if (!nextConfig.exists() && legacyConfig.exists()) {
            try {
                nextDir.mkdirs();
                Files.copy(legacyConfig.toPath(), nextConfig.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                nextDir = legacyConfig.getParentFile();
                nextConfig = legacyConfig;
            }
        }
        if (configDir == null || !configDir.equals(nextDir)) {
            configDir = nextDir;
            moduleConfig = nextConfig;
        }
    }

    private static File configDir() {
        refreshConfigPaths();
        return configDir;
    }

    private static File moduleConfig() {
        refreshConfigPaths();
        return moduleConfig;
    }

    public static void save() {
        if (!configDir().exists()) {
            configDir().mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(moduleConfig()))) {
            writer.println("GUIKEY:" + NavineClient.getClickGuiKey());
            writer.println("AUTOLOGIN:" + autoLogin);
            writer.println("MODULESVISIBLE:" + modulesVisible);
            writer.println("HUDTOPLEFT:" + hudTopLeft);
            writer.println("NOTIFCENTER:" + notificationCenterHotbar);
            if (AuthManager.isVerified() && AuthManager.getLoggedInUser() != null) {
                cachedSessionUser = AuthManager.getLoggedInUser();
                writer.println("SESSION:" + cachedSessionUser);
            }

            for (NavineAddon addon : AddonManager.getAddons()) {
                writer.println("ADDON:" + addon.getPackage() + ":" + AddonManager.isAddonEnabled(addon));
            }

            for (Map.Entry<String, int[]> entry : clickGuiTabPositions.entrySet()) {
                int[] pos = entry.getValue();
                if (pos != null && pos.length >= 2) {
                    writer.println("TABPOS:" + entry.getKey() + ":" + pos[0] + ":" + pos[1]);
                }
            }

            for (String favorite : favoriteModules) {
                writer.println("FAVORITE:" + favorite);
            }

            for (Module m : ModuleManager.getModules()) {
                writer.println(m.getName() + ":" + m.isEnabled() + ":" + m.getKey());
                for (Setting<?> s : m.getSettings()) {
                    if (s instanceof ModeSetting ms) {
                        writer.println("S:" + m.getName() + ":" + s.getName() + ":" + ms.getValue());
                    } else {
                        writer.println("S:" + m.getName() + ":" + s.getName() + ":" + s.getValue());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        File file = moduleConfig();
        if (!file.exists()) {
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String pendingSession = null;
        for (String line : lines) {
            String[] parts = line.split(":");
            if (parts[0].equals("SESSION") && parts.length >= 2) {
                pendingSession = parts[1];
                cachedSessionUser = parts[1];
            } else if (parts[0].equals("AUTOLOGIN") && parts.length >= 2) {
                autoLogin = Boolean.parseBoolean(parts[1]);
            }
        }
        if (autoLogin && pendingSession != null) {
            AuthManager.restoreSession(pendingSession);
        }

        for (String line : lines) {
            String[] parts = line.split(":");
            if (parts[0].equals("S") && parts.length >= 4) {
                Module module = ModuleManager.getModuleByName(parts[1]);
                if (module != null) {
                    for (Setting<?> s : module.getSettings()) {
                        if (s.getName().equalsIgnoreCase(parts[2])) {
                            if (s instanceof BooleanSetting bs) {
                                bs.setValue(Boolean.parseBoolean(parts[3]));
                            } else if (s instanceof NumberSetting ns) {
                                ns.setValue(Double.parseDouble(parts[3]));
                            } else if (s instanceof ModeSetting ms) {
                                ms.setValue(parts[3]);
                            } else if (s instanceof ColorSetting cs) {
                                try {
                                    cs.setValue(Integer.parseInt(parts[3]));
                                } catch (NumberFormatException e) {
                                }
                            }
                        }
                    }
                }
            } else if (parts[0].equals("TABPOS") && parts.length >= 4) {
                try {
                    clickGuiTabPositions.put(parts[1], new int[]{
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3])
                    });
                } catch (NumberFormatException ignored) {
                }
            } else if (parts[0].equals("ADDON") && parts.length >= 3) {
                AddonManager.loadEnabledState(parts[1], Boolean.parseBoolean(parts[2]));
            } else if (parts[0].equals("GUIKEY") && parts.length >= 2) {
                try {
                    int loadedKey = NavineInput.migrateLegacyKey(Integer.parseInt(parts[1]));
                    if (loadedKey == NavineInput.KEY_I || !NavineClient.isValidClickGuiKey(loadedKey)) {
                        loadedKey = NavineInput.KEY_RIGHT_SHIFT;
                    }
                    NavineClient.setClickGuiKey(loadedKey, false);
                } catch (NumberFormatException e) {
                }
            } else if (parts[0].equals("MODULESVISIBLE") && parts.length >= 2) {
                try {
                    modulesVisible = clampModulesVisible(Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            } else if (parts[0].equals("HUDTOPLEFT") && parts.length >= 2) {
                hudTopLeft = Boolean.parseBoolean(parts[1]);
            } else if (parts[0].equals("NOTIFCENTER") && parts.length >= 2) {
                notificationCenterHotbar = Boolean.parseBoolean(parts[1]);
            } else if (parts[0].equals("FAVORITE") && parts.length >= 2) {
                favoriteModules.add(parts[1]);
            } else if (parts[0].equals("SESSION") || parts[0].equals("AUTOLOGIN")) {
                continue;
            } else if (parts.length >= 3) {
                Module m = ModuleManager.getModuleByName(parts[0]);
                if (m != null) {
                    boolean wantEnabled = Boolean.parseBoolean(parts[1]);
                    m.setEnabledSilent(AuthManager.isVerified() && wantEnabled);
                    try {
                        m.setKey(Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public static void reloadEnabledModules() {
        if (!AuthManager.isVerified()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(ConfigManager::reloadEnabledModules);
            return;
        }
        File file = moduleConfig();
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 3 && !parts[0].equals("S") && !parts[0].equals("TABPOS")
                        && !parts[0].equals("ADDON") && !parts[0].equals("GUIKEY")
                        && !parts[0].equals("MODULESVISIBLE") && !parts[0].equals("HUDTOPLEFT")
                        && !parts[0].equals("NOTIFCENTER") && !parts[0].equals("FAVORITE")
                        && !parts[0].equals("SESSION") && !parts[0].equals("AUTOLOGIN")) {
                    Module m = ModuleManager.getModuleByName(parts[0]);
                    if (m != null) {
                        boolean wantEnabled = Boolean.parseBoolean(parts[1]);
                        try {
                            m.setEnabledSilent(wantEnabled);
                            m.setKey(Integer.parseInt(parts[2]));
                        } catch (Throwable error) {
                            NavineClient.LOGGER.error("Failed to reload module {}", m.getName(), error);
                        }
                    }
                } else if (parts[0].equals("S") && parts.length >= 4) {
                    Module module = ModuleManager.getModuleByName(parts[1]);
                    if (module != null) {
                        for (Setting<?> s : module.getSettings()) {
                            if (s.getName().equalsIgnoreCase(parts[2])) {
                                if (s instanceof BooleanSetting bs) {
                                    bs.setValue(Boolean.parseBoolean(parts[3]));
                                } else if (s instanceof NumberSetting ns) {
                                    ns.setValue(Double.parseDouble(parts[3]));
                                } else if (s instanceof ModeSetting ms) {
                                    ms.setValue(parts[3]);
                                } else if (s instanceof ColorSetting cs) {
                                    try {
                                        cs.setValue(Integer.parseInt(parts[3]));
                                    } catch (NumberFormatException e) {
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void restoreSavedSessionIfNeeded() {
        if (AuthManager.isVerified() || !autoLogin) {
            return;
        }
        if (cachedSessionUser != null && !cachedSessionUser.isEmpty()) {
            AuthManager.restoreSession(cachedSessionUser);
            return;
        }
        File file = moduleConfig();
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("AUTOLOGIN:")) {
                    autoLogin = Boolean.parseBoolean(line.substring("AUTOLOGIN:".length()));
                } else if (line.startsWith("SESSION:")) {
                    cachedSessionUser = line.substring("SESSION:".length()).trim();
                }
            }
        } catch (IOException ignored) {
        }
        if (autoLogin && cachedSessionUser != null && !cachedSessionUser.isEmpty()) {
            AuthManager.restoreSession(cachedSessionUser);
        }
    }

    public static void clearSavedSession() {
        cachedSessionUser = null;
    }

    public static boolean isAutoLoginEnabled() {
        return autoLogin;
    }

    public static void setAutoLoginEnabled(boolean enabled) {
        autoLogin = enabled;
        save();
    }

    public static void saveSession() {
        if (!AuthManager.isVerified() || AuthManager.getLoggedInUser() == null) {
            return;
        }
        cachedSessionUser = AuthManager.getLoggedInUser();
        try {
            if (!configDir().exists()) {
                configDir().mkdirs();
            }
            File file = moduleConfig();
            List<String> lines = new ArrayList<>();
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("SESSION:") || line.startsWith("AUTOLOGIN:")) {
                            continue;
                        }
                        lines.add(line);
                    }
                }
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("SESSION:" + cachedSessionUser);
                writer.println("AUTOLOGIN:" + autoLogin);
                for (String line : lines) {
                    writer.println(line);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static int[] getClickGuiTabPosition(String tabId) {
        return clickGuiTabPositions.get(tabId);
    }

    public static void setClickGuiTabPosition(String tabId, int x, int y) {
        clickGuiTabPositions.put(tabId, new int[]{x, y});
    }

    public static int getModulesVisible() {
        return modulesVisible;
    }

    public static void setModulesVisible(int count) {
        modulesVisible = clampModulesVisible(count);
        save();
    }

    public static int getModuleListMaxHeight() {
        return getModulesVisible() * 15 + 2;
    }

    public static boolean isHudTopLeft() {
        return hudTopLeft;
    }

    public static void setHudTopLeft(boolean topLeft) {
        hudTopLeft = topLeft;
        save();
    }

    public static boolean isNotificationCenterHotbar() {
        return notificationCenterHotbar;
    }

    public static void setNotificationCenterHotbar(boolean centerHotbar) {
        notificationCenterHotbar = centerHotbar;
        save();
    }

    private static int clampModulesVisible(int count) {
        return Math.max(8, Math.min(15, count));
    }

    public static boolean isFavorite(Module module) {
        return module != null && favoriteModules.contains(module.getName());
    }

    public static void setFavorite(Module module, boolean favorite) {
        if (module == null) {
            return;
        }
        if (favorite) {
            favoriteModules.add(module.getName());
        } else {
            favoriteModules.remove(module.getName());
        }
        save();
    }

    public static void toggleFavorite(Module module) {
        setFavorite(module, !isFavorite(module));
    }

    public static List<Module> getFavoriteModules() {
        List<Module> favorites = new ArrayList<>();
        for (String name : favoriteModules) {
            Module module = ModuleManager.getModuleByName(name);
            if (module != null && ModuleManager.isModuleAccessible(module)) {
                favorites.add(module);
            }
        }
        return favorites;
    }

    public static int countEnabledModules(CategoryKey key) {
        if (key == null) {
            return 0;
        }
        int count = 0;
        for (Module module : ModuleManager.getModulesByCategoryKey(key)) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }
}
