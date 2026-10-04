package nv.navineclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import nv.navineclient.module.render.NoHurtCam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void onBobView(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        nv.navineclient.module.render.NoHurtCam noHurtCam = (nv.navineclient.module.render.NoHurtCam)
            nv.navineclient.module.ModuleManager.getModuleByName("NoHurtCam");
        if (noHurtCam != null && noHurtCam.shouldCancelBob()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        nv.navineclient.module.render.NoHurtCam noHurtCam = (nv.navineclient.module.render.NoHurtCam)
            nv.navineclient.module.ModuleManager.getModuleByName("NoHurtCam");
        if (noHurtCam != null && noHurtCam.shouldClearHurtTime()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null && mc.player.hurtTime > 0) {
                mc.player.hurtTime = 0;
            }
        }
    }

    @Inject(
            method = "extractGui",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0,
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void navine$ghostOverlayFallback(
            DeltaTracker deltaTracker,
            boolean shouldRenderLevel,
            boolean resourcesLoaded,
            CallbackInfo ci,
            GuiGraphicsExtractor graphics) {
        if (shouldRenderLevel && graphics != null) {
            InGameHudMixin.renderOverlaysOnce(graphics, deltaTracker);
        }
    }
}
