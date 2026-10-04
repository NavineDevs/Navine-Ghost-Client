package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;

public class PortalChat extends Module {
    public static PortalChat INSTANCE;

    private final BooleanSetting allowCommands = new BooleanSetting("Commands", "Allow commands in portals", true);
    private final ModeSetting portalType = new ModeSetting("Portal", "Portal type", "Any", "Any", "Nether", "End");
    private final BooleanSetting showIndicator = new BooleanSetting("Indicator", "Show HUD indicator", false);

    public PortalChat() {
        super("PortalChat", "Allows chatting while in portals", Category.PLAYER);
        INSTANCE = this;
        addSetting(allowCommands);
        addSetting(portalType);
        addSetting(showIndicator);
    }

    public boolean allowsCommands() {
        return allowCommands.getValue();
    }

    public boolean matchesPortal(boolean inNether, boolean inEnd) {
        String type = portalType.getValue();
        if (type.equals("Any")) return inNether || inEnd;
        if (type.equals("Nether")) return inNether;
        if (type.equals("End")) return inEnd;
        return false;
    }

    public boolean showIndicator() {
        return showIndicator.getValue();
    }

    public static boolean shouldAllowChat() {
        Module module = ModuleManager.getModuleByName("PortalChat");
        return module != null && module.isEnabled();
    }

    public static PortalChat getInstance() {
        return (PortalChat) ModuleManager.getModuleByName("PortalChat");
    }
}
