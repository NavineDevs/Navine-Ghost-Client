package nv.navineclient.module.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import java.util.ArrayList;
import java.util.List;

public class Radar extends Module {
    public static Radar INSTANCE;
    
    private final NumberSetting range = new NumberSetting("Range", "Radar range", 100.0, 20.0, 300.0);
    private final NumberSetting size = new NumberSetting("Size", "Radar size", 100.0, 50.0, 200.0);
    private final BooleanSetting showPlayers = new BooleanSetting("Players", "Show players", true);
    private final BooleanSetting showMobs = new BooleanSetting("Mobs", "Show hostile mobs", false);
    private final BooleanSetting showAnimals = new BooleanSetting("Animals", "Show animals", false);
    private final BooleanSetting showNames = new BooleanSetting("Names", "Show entity names", false);
    private final BooleanSetting bigRadar = new BooleanSetting("BigRadar", "Enable big radar (M key)", true);
    private final NumberSetting updateRate = new NumberSetting("UpdateRate", "Update rate (ticks)", 5.0, 1.0, 20.0);
    private final NumberSetting crowdThreshold = new NumberSetting("CrowdThreshold", "Dots-only threshold", 10.0, 4.0, 40.0);
    private final NumberSetting maxNameLabels = new NumberSetting("MaxLabels", "Max name labels", 6.0, 0.0, 20.0);

    public Radar() {
        super("Radar", "Shows entities around you on a mini-map", Category.RENDER);
        INSTANCE = this;
        addSetting(range);
        addSetting(size);
        addSetting(showPlayers);
        addSetting(showMobs);
        addSetting(showAnimals);
        addSetting(showNames);
        addSetting(bigRadar);
        addSetting(updateRate);
        addSetting(crowdThreshold);
        addSetting(maxNameLabels);
    }
    
    private int tickCounter = 0;
    private List<Player> cachedPlayers = new ArrayList<>();
    private List<LivingEntity> cachedEntities = new ArrayList<>();
    
    @Override
    public void onTick() {
        tickCounter++;
        if (tickCounter >= updateRate.getValue().intValue()) {
            tickCounter = 0;
            updateCache();
        }
    }
    
    private void updateCache() {
        cachedPlayers.clear();
        cachedEntities.clear();
        if (mc.level == null || mc.player == null) return;
        
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity != mc.player) {
                double distance = mc.player.distanceTo(entity);
                if (distance <= range.getValue()) {
                    if (entity instanceof Player && showPlayers.getValue()) {
                        cachedPlayers.add((Player) entity);
                    } else if (entity instanceof Monster && showMobs.getValue()) {
                        cachedEntities.add(living);
                    } else if (entity instanceof Animal && showAnimals.getValue()) {
                        cachedEntities.add(living);
                    }
                }
            }
        }
    }
    
    public List<Player> getNearbyPlayers() {
        return new ArrayList<>(cachedPlayers);
    }
    
    public List<LivingEntity> getNearbyEntities() {
        return new ArrayList<>(cachedEntities);
    }
    
    public double getRange() { return range.getValue(); }
    public double getSize() { return size.getValue(); }
    public boolean shouldShowNames() { return showNames.getValue(); }
    public boolean isBigRadarEnabled() { return bigRadar.getValue(); }
    public int getCrowdThreshold() { return crowdThreshold.getValue().intValue(); }
    public int getMaxNameLabels() { return maxNameLabels.getValue().intValue(); }
}
