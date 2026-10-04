package nv.navineclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void navine$hideBossBar(GuiGraphicsExtractor context, CallbackInfo ci) {
        NoRender noRender = (NoRender) ModuleManager.getModuleByName("NoRender");
        if (noRender != null && noRender.isEnabled() && noRender.hidesBossBar()) {
            ci.cancel();
        }
    }
}
