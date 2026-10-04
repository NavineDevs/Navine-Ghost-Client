package nv.navineclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import nv.navineclient.module.player.AntiCrash;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(
        method = "submit(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void navine$skipBrokenEntity(
        net.minecraft.world.entity.Entity entity,
        net.minecraft.world.entity.Entity renderStateEntity,
        com.mojang.blaze3d.vertex.PoseStack poseStack,
        net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector,
        net.minecraft.client.renderer.state.level.CameraRenderState camera,
        int packedLight,
        CallbackInfo ci
    ) {
        AntiCrash antiCrash = (AntiCrash) ModuleManager.getModuleByName("AntiCrash");
        if (antiCrash == null || !antiCrash.isEnabled() || !antiCrash.shouldPreventRenderCrash()) {
            return;
        }
        if (Double.isNaN(entity.getX()) || Double.isNaN(entity.getY()) || Double.isNaN(entity.getZ())
                || Double.isInfinite(entity.getX()) || Double.isInfinite(entity.getY()) || Double.isInfinite(entity.getZ())) {
            ci.cancel();
        }
    }

    @Inject(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void navine$hideVanillaNametag(
        S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, int offset, CallbackInfo ci
    ) {
        Nametags nametags = (Nametags) ModuleManager.getModuleByName("Nametags");
        if (nametags != null && nametags.isEnabled() && state.nameTag != null) {
            ci.cancel();
        }
    }
}
