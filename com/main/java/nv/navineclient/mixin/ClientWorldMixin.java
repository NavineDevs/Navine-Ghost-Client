package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.world.Weather;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientWorldMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(CallbackInfo ci) {
        ClientLevel world = (ClientLevel) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level != world) {
            return;
        }

        Weather weather = (Weather) ModuleManager.getModuleByName("Weather");
        if (weather == null || !weather.isEnabled() || !weather.isClientOnly()) {
            return;
        }

        String mode = weather.getWeatherMode();
        float rainStrength = weather.getRainStrength();
        if (mode.equals("Clear")) {
            world.setRainLevel(0.0f);
            world.setThunderLevel(0.0f);
        } else if (mode.equals("Rain")) {
            world.setRainLevel(rainStrength);
            world.setThunderLevel(0.0f);
        } else if (mode.equals("Thunder")) {
            world.setRainLevel(rainStrength);
            world.setThunderLevel(rainStrength);
        }
    }
}
