package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import nv.navineclient.util.ScreenBackgroundHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericMessageScreen.class)
public class GenericMessageScreenMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void navineBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GenericMessageScreen screen = (GenericMessageScreen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !ScreenBackgroundHelper.shouldUseNavineBackground(screen, minecraft)) {
            return;
        }
        ScreenBackgroundHelper.renderNavineBackground(context, screen.width, screen.height);
        ci.cancel();
    }
}
