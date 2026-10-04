package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.StringSetting;

public class BetterChat extends Module {
    public static BetterChat INSTANCE;

    private final BooleanSetting navineTag = new BooleanSetting("NavineTag", "Show Navine tag prefix", true);
    private final BooleanSetting timestamps = new BooleanSetting("Timestamps", "Show message timestamps", false);
    private final StringSetting tagPrefix = new StringSetting("Prefix", "Custom tag prefix", "[Navine]");

    public BetterChat() {
        super("BetterChat", "Enhanced chat features with Navine tags", Category.PLAYER);
        INSTANCE = this;
        addSetting(navineTag);
        addSetting(timestamps);
        addSetting(tagPrefix);
    }

    public boolean showNavineTag() {
        return navineTag.getValue();
    }

    public boolean showTimestamps() {
        return timestamps.getValue();
    }

    public String getTagPrefix() {
        return tagPrefix.getValue();
    }

    public String formatMessage(String message) {
        if (!isEnabled()) return message;
        StringBuilder sb = new StringBuilder();
        if (showTimestamps()) {
            java.time.LocalTime now = java.time.LocalTime.now();
            sb.append(String.format("[%02d:%02d] ", now.getHour(), now.getMinute()));
        }
        if (showNavineTag()) {
            sb.append(getTagPrefix()).append(" ");
        }
        sb.append(message);
        return sb.toString();
    }

    public static boolean shouldModifyChat() {
        BetterChat mod = (BetterChat) nv.navineclient.module.ModuleManager.getModuleByName("BetterChat");
        return mod != null && mod.isEnabled();
    }
}
