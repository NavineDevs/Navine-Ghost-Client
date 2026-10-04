package nv.navineclient.module.world;

import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.ModeSetting;

public class Ambience extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Ambience effect", "Bright", "Bright", "Dark", "Twilight");
    private final NumberSetting intensity = new NumberSetting("Intensity", "Effect intensity", 1.0, 0.0, 1.0);
    private final BooleanSetting affectFog = new BooleanSetting("Fog", "Affect fog density", true);

    public Ambience() {
        super("Ambience", "Change world ambience and lighting", Category.WORLD);
        addSetting(mode);
        addSetting(intensity);
        addSetting(affectFog);
    }

    public boolean affectsFog() {
        return affectFog.getValue();
    }

    public float getIntensity() {
        return intensity.getValue().floatValue();
    }

    public String getMode() {
        return mode.getValue();
    }
}
