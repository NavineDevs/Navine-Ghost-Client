package nv.navineclient.module.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class ESP extends Module {
    public static ESP INSTANCE;

    private final BooleanSetting playersSetting = new BooleanSetting("Players", "Show players", true);
    private final BooleanSetting mobsSetting = new BooleanSetting("Mobs", "Show hostile mobs", true);
    private final BooleanSetting animalsSetting = new BooleanSetting("Animals", "Show animals", false);
    private final NumberSetting rangeSetting = new NumberSetting("Range", "ESP range", 64.0, 16.0, 256.0);
    private final NumberSetting red = new NumberSetting("Red", "Red component", 65.0, 0.0, 255.0);
    private final NumberSetting green = new NumberSetting("Green", "Green component", 105.0, 0.0, 255.0);
    private final NumberSetting blue = new NumberSetting("Blue", "Blue component", 225.0, 0.0, 255.0);

    public ESP() {
        super("ESP", "Highlights entities through walls", Category.RENDER);
        INSTANCE = this;
        addSetting(playersSetting);
        addSetting(mobsSetting);
        addSetting(animalsSetting);
        addSetting(rangeSetting);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
    }

    @Override
    public void onTick() {
        refreshClient();
        if (!isEnabled() || mc.level == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            try {
                boolean glow = shouldGlow(entity);
                if (entity.hasGlowingTag() != glow) {
                    entity.setGlowingTag(glow);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        clearGlowTags();
    }

    private void clearGlowTags() {
        if (mc.level == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            try {
                if (entity.hasGlowingTag()) {
                    entity.setGlowingTag(false);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public boolean shouldHighlight(Entity entity) {
        return shouldGlow(entity);
    }

    public boolean shouldGlow(Entity entity) {
        if (!isEnabled()) {
            return false;
        }
        if (mc.player == null) {
            return false;
        }
        if (mc.player.distanceTo(entity) > rangeSetting.getValue()) {
            return false;
        }

        if (entity instanceof Player && entity != mc.player && playersSetting.getValue()) {
            return true;
        }
        if (entity instanceof Monster && mobsSetting.getValue()) {
            return true;
        }
        if (entity instanceof Animal && animalsSetting.getValue()) {
            return true;
        }

        return false;
    }

    public boolean showPlayers() { return playersSetting.getValue(); }
    public boolean showMobs() { return mobsSetting.getValue(); }
    public boolean showAnimals() { return animalsSetting.getValue(); }
    public double getRange() { return rangeSetting.getValue(); }

    public int getColor() {
        int r = (int) red.getValue().doubleValue();
        int g = (int) green.getValue().doubleValue();
        int b = (int) blue.getValue().doubleValue();
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    public static boolean isESP() {
        Module module = ModuleManager.getModuleByName("ESP");
        return module != null && module.isEnabled();
    }
}
