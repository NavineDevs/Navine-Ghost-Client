package nv.navineclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.world.CustomSky;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void navine$customSkyFog(
        Camera camera,
        float partialTicks,
        ClientLevel level,
        int renderDistance,
        float darkenWorldAmount,
        Vector4f dest,
        CallbackInfo ci
    ) {
        CustomSky customSky = (CustomSky) ModuleManager.getModuleByName("CustomSky");
        if (customSky != null && customSky.isEnabled() && customSky.shouldDisableFog()) {
            int skyColor = customSky.getSkyColor();
            dest.set(
                Mth.clamp(((skyColor >> 16) & 0xFF) / 255.0F, 0.0F, 1.0F),
                Mth.clamp(((skyColor >> 8) & 0xFF) / 255.0F, 0.0F, 1.0F),
                Mth.clamp((skyColor & 0xFF) / 255.0F, 0.0F, 1.0F),
                1.0F
            );
            return;
        }
        nv.navineclient.module.render.NoRender noRender = (nv.navineclient.module.render.NoRender) ModuleManager.getModuleByName("NoRender");
        if (noRender != null && noRender.isEnabled() && noRender.removesFog()) {
            dest.set(0.75F, 0.85F, 1.0F, 1.0F);
        }
    }
}
