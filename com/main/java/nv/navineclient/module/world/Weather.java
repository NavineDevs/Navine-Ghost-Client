package nv.navineclient.module.world;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Weather extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Weather mode", "Clear", "Clear", "Rain", "Thunder");
    private final NumberSetting rainStrength = new NumberSetting("RainStrength", "Rain intensity", 1.0, 0.0, 1.0);
    private final BooleanSetting clientOnly = new BooleanSetting("ClientOnly", "Override server weather locally", true);

    private float savedRainLevel;
    private float savedThunderLevel;

    public Weather() {
        super("Weather", "Controls the world weather", Category.WORLD);
        addSetting(mode);
        addSetting(rainStrength);
        addSetting(clientOnly);
    }

    @Override
    public void onEnable() {
        if (mc.level != null) {
            savedRainLevel = mc.level.getRainLevel(1.0f);
            savedThunderLevel = mc.level.getThunderLevel(1.0f);
        }
    }

    @Override
    public void onDisable() {
        if (mc.level != null) {
            mc.level.setRainLevel(savedRainLevel);
            mc.level.setThunderLevel(savedThunderLevel);
        }
    }

    public String getWeatherMode() {
        return mode.getValue();
    }

    public float getRainStrength() {
        return rainStrength.getValue().floatValue();
    }

    public boolean isClientOnly() {
        return clientOnly.getValue();
    }
}
