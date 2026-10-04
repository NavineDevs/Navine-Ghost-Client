package nv.navineclient.module.render;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ColorSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Tracer extends Module {
    public static Tracer INSTANCE;

    private final BooleanSetting playersSetting = new BooleanSetting("Players", "Draw tracers to players", true);
    private final BooleanSetting mobsSetting = new BooleanSetting("Mobs", "Draw tracers to mobs", false);
    private final BooleanSetting animalsSetting = new BooleanSetting("Animals", "Draw tracers to animals", false);
    private final NumberSetting rangeSetting = new NumberSetting("Range", "Maximum tracer range", 64.0, 16.0, 256.0);
    private final ColorSetting color = new ColorSetting("Color", "Tracer line color", 0xFF4169E1);

    public Tracer() {
        super("Tracer", "Draws lines to entities", Category.RENDER);
        INSTANCE = this;
        addSetting(playersSetting);
        addSetting(mobsSetting);
        addSetting(animalsSetting);
        addSetting(rangeSetting);
        addSetting(color);
    }

    public boolean showPlayers() { return playersSetting.getValue(); }
    public boolean showMobs() { return mobsSetting.getValue(); }
    public boolean showAnimals() { return animalsSetting.getValue(); }
    public double getRange() { return rangeSetting.getValue(); }
    public int getColor() { return color.getValue(); }

    public static boolean isTracer() {
        Module module = ModuleManager.getModuleByName("Tracer");
        return module != null && module.isEnabled();
    }

    public static boolean matchesEntity(LivingEntity entity) {
        Tracer tracer = INSTANCE;
        if (tracer == null || !tracer.isEnabled()) return false;
        if (entity instanceof Player) return tracer.showPlayers();
        if (entity instanceof Monster) return tracer.showMobs();
        if (entity instanceof Animal) return tracer.showAnimals();
        return false;
    }
}
