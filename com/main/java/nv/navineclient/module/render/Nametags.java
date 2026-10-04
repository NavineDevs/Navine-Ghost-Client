package nv.navineclient.module.render;

import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Nametags extends Module {
    public static Nametags INSTANCE;

    private final NumberSetting scale = new NumberSetting("Scale", "Nametag scale", 1.4, 0.5, 5.0);
    private final BooleanSetting showHealth = new BooleanSetting("Health", "Show health", true);
    private final BooleanSetting showArmor = new BooleanSetting("Armor", "Show armor value", true);
    private final BooleanSetting showDistance = new BooleanSetting("Distance", "Show distance", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Walls", "Show through walls", true);
    private final BooleanSetting background = new BooleanSetting("Background", "Draw name background", true);

    public Nametags() {
        super("Nametags", "Enhanced player nametags", Category.RENDER);
        INSTANCE = this;
        addSetting(scale);
        addSetting(showHealth);
        addSetting(showArmor);
        addSetting(showDistance);
        addSetting(throughWalls);
        addSetting(background);
    }

    public String getNametagText(Player player) {
        if (mc.player == null) {
            return player.getName().getString();
        }

        StringBuilder sb = new StringBuilder(player.getName().getString());
        if (showHealth.getValue()) {
            float health = player.getHealth();
            String color = health > 14f ? "a" : health > 8f ? "e" : "c";
            sb.append(" \u00A7").append(color).append(String.format("%.1f", health)).append(" HP");
        }
        if (showArmor.getValue()) {
            int armor = player.getArmorValue();
            if (armor > 0) {
                sb.append(" \u00A79").append(armor).append(" AR");
            }
        }
        if (showDistance.getValue()) {
            double dist = mc.player.distanceTo(player);
            sb.append(" \u00A77").append(String.format("%.1fm", dist));
        }
        return sb.toString();
    }

    public float getScale() {
        return scale.getValue().floatValue();
    }

    public boolean showHealth() {
        return showHealth.getValue();
    }

    public boolean showDistance() {
        return showDistance.getValue();
    }

    public boolean throughWalls() {
        return throughWalls.getValue();
    }

    public boolean drawBackground() {
        return background.getValue();
    }

    public static boolean isNametags() {
        Module module = ModuleManager.getModuleByName("Nametags");
        return module != null && module.isEnabled();
    }
}
