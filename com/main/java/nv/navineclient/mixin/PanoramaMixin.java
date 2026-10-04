package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Panorama;
import nv.navineclient.util.ClientScreens;
import nv.navineclient.util.ScreenBackgroundHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Panorama.class)
public class PanoramaMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void navineBlockPanorama(GuiGraphicsExtractor context, int width, int height, boolean spin, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Screen screen = ClientScreens.get(minecraft);
        if (screen != null && ScreenBackgroundHelper.shouldUseNavineBackground(screen, minecraft)) {
            ci.cancel();
        }
    }
}
