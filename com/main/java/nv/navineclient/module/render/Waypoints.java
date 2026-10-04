package nv.navineclient.module.render;

import net.minecraft.core.BlockPos;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.ColorSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class Waypoints extends Module {
    public static Waypoints INSTANCE;

    private final NumberSetting maxWaypoints = new NumberSetting("MaxWaypoints", "Maximum waypoints", 20.0, 1.0, 100.0);
    private final NumberSetting renderDistance = new NumberSetting("RenderDist", "Render distance", 128.0, 16.0, 512.0);
    private final ColorSetting color = new ColorSetting("Color", "Waypoint color", 0xFFFFFF00);

    private final List<Waypoint> waypoints = new ArrayList<>();

    public Waypoints() {
        super("Waypoints", "Shows saved waypoints", Category.RENDER);
        INSTANCE = this;
        addSetting(maxWaypoints);
        addSetting(renderDistance);
        addSetting(color);
    }

    public void addWaypoint(String name, BlockPos pos) {
        if (waypoints.size() >= maxWaypoints.getValue().intValue()) {
            ChatUtils.message("Waypoint limit reached (" + maxWaypoints.getValue().intValue() + ")");
            return;
        }
        waypoints.add(new Waypoint(name, pos));
        ChatUtils.message("Added waypoint: " + name + " at " + pos.toShortString());
    }

    public void removeWaypoint(String name) {
        waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
        ChatUtils.message("Removed waypoint: " + name);
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public List<Waypoint> getVisibleWaypoints() {
        if (mc.player == null) return waypoints;
        double maxDist = renderDistance.getValue();
        List<Waypoint> visible = new ArrayList<>();
        for (Waypoint w : waypoints) {
            double dist = Math.sqrt(mc.player.distanceToSqr(w.pos.getX() + 0.5, w.pos.getY(), w.pos.getZ() + 0.5));
            if (dist <= maxDist) {
                visible.add(w);
            }
        }
        return visible;
    }

    public int getColor() {
        return color.getValue();
    }

    public double getRenderDistance() {
        return renderDistance.getValue();
    }

    public static class Waypoint {
        public final String name;
        public final BlockPos pos;

        public Waypoint(String name, BlockPos pos) {
            this.name = name;
            this.pos = pos;
        }
    }
}
