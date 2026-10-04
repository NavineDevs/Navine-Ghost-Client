package nv.navineclient.module.misc;

import nv.navineclient.NavineClient;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.util.ChatUtils;

public class LegitMode extends Module {
    public static LegitMode INSTANCE;

    public enum Profile {
        BALANCED,
        STRICT,
        VULCAN,
        WATCHDOG
    }

    private final ModeSetting profile = new ModeSetting(
            "Profile",
            "Anticheat bypass strictness",
            NavineClient.isGhost() ? "Vulcan" : "Balanced",
            "Balanced",
            "Strict",
            "Vulcan",
            "Watchdog"
    );

    public LegitMode() {
        super("LegitMode", "Keeps modules within anticheat-safe limits on protected servers", Category.MISC);
        INSTANCE = this;
        addSetting(profile);
        if (NavineClient.isGhost()) {
            setDefaultEnabled();
        }
    }

    @Override
    public void onEnable() {
        if (!NavineClient.isGhost()) {
            ChatUtils.success("LegitMode on (" + profile.getValue() + ") — modules capped for anticheat servers.");
        }
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static Profile getProfile() {
        if (!isActive()) {
            return null;
        }
        String value = INSTANCE.profile.getValue();
        if (value == null) {
            return Profile.BALANCED;
        }
        return switch (value.toLowerCase()) {
            case "strict" -> Profile.STRICT;
            case "vulcan" -> Profile.VULCAN;
            case "watchdog" -> Profile.WATCHDOG;
            default -> Profile.BALANCED;
        };
    }
}
