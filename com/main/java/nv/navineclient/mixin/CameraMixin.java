package nv.navineclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.render.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void navine$freecamPosition(CameraRenderState state, float partialTick, CallbackInfo ci) {
        Freecam freecam = Freecam.INSTANCE;
        if (freecam == null || !freecam.isCameraActive()) {
            return;
        }
        freecam.applyRenderInterpolation(partialTick);
        state.pos = new Vec3(freecam.getCamX(), freecam.getCamY(), freecam.getCamZ());
        state.xRot = freecam.getCamPitch();
        state.yRot = freecam.getCamYaw();
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void navine$zoomFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        nv.navineclient.module.render.Zoom zoom = nv.navineclient.module.render.Zoom.INSTANCE;
        if (zoom == null) {
            return;
        }
        float multiplier = zoom.getZoomMultiplier();
        if (multiplier <= 1.001f) {
            return;
        }
        float fov = cir.getReturnValue();
        cir.setReturnValue((float) Math.max(zoom.getMinFov(), fov / multiplier));
    }
}
