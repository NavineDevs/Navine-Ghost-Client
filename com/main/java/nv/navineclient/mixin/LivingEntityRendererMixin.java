package nv.navineclient.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import nv.navineclient.module.movement.Blink;
import nv.navineclient.module.render.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void navine$showLocalPlayerModel(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || entity != mc.player) {
            return;
        }
        Freecam freecam = Freecam.INSTANCE;
        if ((freecam != null && freecam.isCameraActive()) || Blink.shouldRenderLocalPlayer()) {
            state.isInvisible = false;
            state.isInvisibleToPlayer = false;
        }
    }
}
