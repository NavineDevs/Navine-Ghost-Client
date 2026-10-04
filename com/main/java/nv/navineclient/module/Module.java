package nv.navineclient.module;

import net.minecraft.client.Minecraft;
import nv.navineclient.NavineClient;
import nv.navineclient.module.settings.Setting;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String description;
    private final Category category;
    private final String customCategoryId;
    private boolean enabled;
    private int key = 0;
    public boolean keyHeld = false;
    private long suppressKeybindUntil;
    private final List<Setting> settings = new ArrayList<>();

    public void refreshClient() {
        mc = Minecraft.getInstance();
    }

    protected Minecraft mc() {
        return Minecraft.getInstance();
    }

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.customCategoryId = null;
    }

    public Module(String name, String description, String customCategoryId) {
        this.name = name;
        this.description = description;
        this.category = null;
        this.customCategoryId = customCategoryId;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean usesCustomCategory() { return customCategoryId != null; }
    public String getCustomCategoryId() { return customCategoryId; }
    public boolean isVisibleInClickGui() { return true; }
    public boolean isEnabled() { return enabled; }
    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }

    public void setEnabled(boolean enabled) {
        runOnClientThread(() -> applyEnabled(enabled, true));
    }

    public void setEnabledSilent(boolean enabled) {
        runOnClientThread(() -> applyEnabled(enabled, false));
    }

    private void runOnClientThread(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(action);
            return;
        }
        action.run();
    }

    private void applyEnabled(boolean enabled, boolean notify) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        try {
            if (enabled) {
                onEnable();
                if (notify) {
                    nv.navineclient.util.ToggleNotifier.show(name + " Enabled");
                }
            } else {
                onDisable();
                if (notify) {
                    nv.navineclient.util.ToggleNotifier.show(name + " Disabled");
                }
            }
        } catch (Throwable error) {
            this.enabled = false;
            NavineClient.LOGGER.error("Module {} failed to {}", name, enabled ? "enable" : "disable", error);
        }
    }

    public void toggle() {
        boolean next = !enabled;
        if (nv.navineclient.NavineClient.GHOST) {
            setEnabledSilent(next);
            nv.navineclient.config.ConfigManager.save();
            if (nv.navineclient.util.AuthManager.isVerified()) {
                nv.navineclient.util.ChatUtils.message("§7" + name + " §8» §"
                        + (next ? "a" : "c") + (next ? "ON" : "OFF"));
            }
        } else {
            setEnabled(next);
        }
    }

    public void suppressKeybind(long ms) {
        suppressKeybindUntil = System.currentTimeMillis() + Math.max(0L, ms);
        keyHeld = true;
    }

    public boolean isKeybindSuppressed() {
        return System.currentTimeMillis() < suppressKeybindUntil;
    }

    protected void setDefaultEnabled() { this.enabled = true; }
    public void onTick() {}
    public void onEnable() {}
    public void onDisable() {}

    protected void addSetting(Setting setting) {
        settings.add(setting);
    }

    public List<Setting> getSettings() { return settings; }

    public boolean isSettingVisible(Setting<?> setting) {
        return true;
    }

    public enum Category {
        COMBAT, MOVEMENT, PLAYER, RENDER, WORLD, MISC;

        public String getDisplayName() {
            return switch (this) {
                case COMBAT -> "Combat";
                case MOVEMENT -> "Movement";
                case PLAYER -> "Utility";
                case RENDER -> "Render";
                case WORLD -> "World";
                case MISC -> "Misc";
            };
        }
        
        public static Category[] orderedValues() {
            return new Category[]{COMBAT, MOVEMENT, PLAYER, RENDER, WORLD, MISC};
        }
    }
}
