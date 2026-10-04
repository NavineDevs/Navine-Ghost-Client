package nv.navineclient.module.world;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.ChatUtils;

public class DevMod extends Module {
    public static BooleanSetting showPacketLog = new BooleanSetting("Packet Log", "Log packets to console", false);
    public static BooleanSetting showEventTimings = new BooleanSetting("Event Timings", "Show event timings in HUD", false);
    public static BooleanSetting uiOutlines = new BooleanSetting("UI Outlines", "Show debug outlines for UI", false);

    private long lastTickTime = System.nanoTime();
    public static float lastTickMs = 0f;

    @Override
    public boolean isVisibleInClickGui() {
        return AuthManager.isDev() || AuthManager.isOwner();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && !AuthManager.isDev() && !AuthManager.isOwner()) {
            return;
        }
        super.setEnabled(enabled);
    }

    public DevMod() {
        super("DevMod", "Developer debug tools (restricted)", Category.MISC);
        addSetting(showPacketLog);
        addSetting(showEventTimings);
        addSetting(uiOutlines);
    }

    @Override
    public void onEnable() {
        if (AuthManager.isOwner()) {
            ChatUtils.message("§d[OWNER] §aDevMod enabled - You have owner privileges!");
        } else if (AuthManager.isDev()) {
            ChatUtils.message("§b[DEV] §aDevMod enabled - You have developer privileges!");
        } else {
            ChatUtils.message("§cYou are not an owner/dev. DevMod disabled.");
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (AuthManager.isOwner() || AuthManager.isDev()) {
            ChatUtils.message("§7DevMod disabled.");
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        long now = System.nanoTime();
        lastTickMs = (now - lastTickTime) / 1_000_000f;
        lastTickTime = now;
    }

    public static boolean isDeveloper() {
        return AuthManager.isDev();
    }

    public static boolean shouldLogPackets() {
        DevMod dev = getInstance();
        return dev != null && dev.isEnabled() && showPacketLog.getValue();
    }

    public static boolean shouldShowTimings() {
        DevMod dev = getInstance();
        return dev != null && dev.isEnabled() && showEventTimings.getValue();
    }

    public static boolean shouldShowUiOutlines() {
        DevMod dev = getInstance();
        return dev != null && dev.isEnabled() && uiOutlines.getValue();
    }

    private static DevMod getInstance() {
        return (DevMod) nv.navineclient.module.ModuleManager.getModuleByName("DevMod");
    }
}
