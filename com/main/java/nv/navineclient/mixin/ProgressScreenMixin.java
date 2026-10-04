package nv.navineclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import nv.navineclient.util.ScreenBackgroundHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgressScreen.class)
public class ProgressScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void navineProgressBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ProgressScreen screen = (ProgressScreen) (Object) this;
        ScreenBackgroundHelper.renderNavineBackground(context, screen.width, screen.height);
    }
}
