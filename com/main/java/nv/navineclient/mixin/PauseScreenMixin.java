package nv.navineclient.mixin;

import nv.navineclient.util.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class PauseScreenMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void navine$inGamePauseBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PauseScreen screen = (PauseScreen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || !screen.showsPauseMenu()) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        if (minecraft.options.getMenuBackgroundBlurriness() >= 1) {
            context.blurBeforeThisStratum();
        }
        ClientAccess.extractDeferredSubtitles(minecraft);
        ci.cancel();
    }
}
