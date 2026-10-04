package nv.navineclient.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.NoFire;
import nv.navineclient.module.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void navine$skipFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoFire.shouldHideFire()) {
            ci.cancel();
            return;
        }
        NoRender noRender = (NoRender) ModuleManager.getModuleByName("NoRender");
        if (noRender != null && noRender.isEnabled() && noRender.hidesFire()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderScreenEffect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isOnFire()Z")
    )
    private boolean navine$hideFireOverlay(Player player) {
        if (NoFire.shouldHideFire()) {
            return false;
        }
        NoRender noRender = (NoRender) ModuleManager.getModuleByName("NoRender");
        if (noRender != null && noRender.isEnabled() && noRender.hidesFire()) {
            return false;
        }
        return player.isOnFire();
    }

    @Redirect(
            method = "renderScreenEffect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z")
    )
    private boolean navine$hideFluidOverlay(Player player, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag) {
        if (tag == net.minecraft.tags.FluidTags.WATER) {
            NoRender noRender = (NoRender) ModuleManager.getModuleByName("NoRender");
            if (noRender != null && noRender.isEnabled() && noRender.hidesWater()) {
                return false;
            }
        }
        if (tag == net.minecraft.tags.FluidTags.LAVA) {
            NoFire noFire = (NoFire) ModuleManager.getModuleByName("NoFire");
            if (noFire != null && noFire.hideLava()) {
                return false;
            }
        }
        return player.isEyeInFluid(tag);
    }
}
