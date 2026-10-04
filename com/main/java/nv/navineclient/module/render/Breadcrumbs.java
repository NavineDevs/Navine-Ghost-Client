package nv.navineclient.module.render;

import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.ColorSetting;
import java.util.ArrayList;
import java.util.List;

public class Breadcrumbs extends Module {
    public static Breadcrumbs INSTANCE;
    private final NumberSetting maxPoints = new NumberSetting("MaxPoints", "Maximum trail points", 500.0, 100.0, 2000.0);
    private final ColorSetting color = new ColorSetting("Color", "Trail color", 0xFF00FFFF);
    private final NumberSetting minDistance = new NumberSetting("MinDist", "Min distance between points", 0.5, 0.1, 2.0);
    
    private final List<Vec3> positions = new ArrayList<>();
    private Vec3 lastPos = null;

    public Breadcrumbs() {
        super("Breadcrumbs", "Shows trail of where you've been", Category.RENDER);
        INSTANCE = this;
        addSetting(maxPoints);
        addSetting(color);
        addSetting(minDistance);
    }
    
    public int getColor() { return color.getValue(); }

    @Override
    public void onEnable() {
        positions.clear();
        lastPos = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        
        Vec3 currentPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        
        if (lastPos == null || currentPos.distanceTo(lastPos) > minDistance.getValue()) {
            positions.add(currentPos);
            lastPos = currentPos;
            
            while (positions.size() > maxPoints.getValue()) {
                positions.remove(0);
            }
        }
    }

    public List<Vec3> getPositions() {
        return positions;
    }
}
