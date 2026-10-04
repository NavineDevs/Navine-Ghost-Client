package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class SafeWalk extends Module {
    public static SafeWalk INSTANCE;
    private final BooleanSetting onlyForward = new BooleanSetting("OnlyForward", "Only when moving forward", true);
    private final BooleanSetting sneakEdges = new BooleanSetting("SneakEdges", "Sneak at edges", false);
    private final NumberSetting edgeDistance = new NumberSetting("EdgeDist", "Edge check distance", 0.3, 0.1, 1.0);

    public SafeWalk() {
        super("SafeWalk", "Prevents walking off edges", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(onlyForward);
        addSetting(sneakEdges);
        addSetting(edgeDistance);
    }

    public boolean onlyForward() { return onlyForward.getValue(); }
    public boolean sneakEdges() { return sneakEdges.getValue(); }
    public double edgeDistance() { return edgeDistance.getValue(); }

    public static boolean shouldSafeWalk() {
        Module module = ModuleManager.getModuleByName("SafeWalk");
        return module != null && module.isEnabled();
    }
}
