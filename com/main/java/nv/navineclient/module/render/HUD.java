package nv.navineclient.module.render;

import nv.navineclient.NavineClient;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;

public class HUD extends Module {
    public static HUD INSTANCE;

    private final BooleanSetting arrayList = new BooleanSetting("ArrayList", "Show enabled modules", true);
    private final BooleanSetting fps = new BooleanSetting("FPS", "Show FPS counter", true);
    private final BooleanSetting coords = new BooleanSetting("Coords", "Show coordinates", false);
    private final BooleanSetting biome = new BooleanSetting("Biome", "Show current biome", false);
    private final BooleanSetting ping = new BooleanSetting("Ping", "Show connection ping", false);

    public HUD() {
        super("HUD", NavineClient.isGhost() ? "Toggle the info overlay" : "Toggle the Navine info overlay", Category.RENDER);
        INSTANCE = this;
        addSetting(arrayList);
        addSetting(fps);
        addSetting(coords);
        addSetting(biome);
        addSetting(ping);
        setDefaultEnabled();
    }

    public boolean showArrayList() {
        return arrayList.getValue();
    }

    public boolean showFps() {
        return fps.getValue();
    }

    public boolean showCoords() {
        return coords.getValue();
    }

    public boolean showBiome() {
        return biome.getValue();
    }

    public boolean showPing() {
        return ping.getValue();
    }

    public static boolean isShown() {
        Module module = ModuleManager.getModuleByName("HUD");
        return module != null && module.isEnabled();
    }
}
