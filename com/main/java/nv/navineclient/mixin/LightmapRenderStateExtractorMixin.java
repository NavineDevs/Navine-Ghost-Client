package nv.navineclient.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import nv.navineclient.module.render.FullBright;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
    private static final Vector3f FULL_BRIGHT_WHITE = new Vector3f(1.0f, 1.0f, 1.0f);

    @Shadow
    private boolean needsUpdate;

    @Inject(method = "extract", at = @At("HEAD"), require = 0)
    private void navine$forceFullBrightUpdate(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (FullBright.isFullBright()) {
            needsUpdate = true;
            if (state != null) {
                state.needsUpdate = true;
            }
        }
    }

    @Inject(method = "extract", at = @At("TAIL"), require = 0)
    private void navine$fullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (!FullBright.isFullBright() || state == null) {
            return;
        }
        state.brightness = 16.0f;
        state.darknessEffectScale = 0.0f;
        state.bossOverlayWorldDarkening = 0.0f;
        state.nightVisionEffectIntensity = 1.0f;
        state.blockFactor = 1.0f;
        state.skyFactor = 1.0f;
        state.blockLightTint = FULL_BRIGHT_WHITE;
        state.skyLightColor = FULL_BRIGHT_WHITE;
        state.ambientColor = FULL_BRIGHT_WHITE;
        state.nightVisionColor = FULL_BRIGHT_WHITE;
        state.needsUpdate = true;
    }
}
