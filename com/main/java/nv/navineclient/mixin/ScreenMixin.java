package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.player.InventoryWalk;
import nv.navineclient.util.ScreenBackgroundHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void onShouldPause(CallbackInfoReturnable<Boolean> cir) {
        InventoryWalk inventoryWalk = (InventoryWalk) ModuleManager.getModuleByName("InventoryWalk");
        if (inventoryWalk != null && inventoryWalk.isEnabled() && (Object) this instanceof AbstractContainerScreen) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void navineBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !ScreenBackgroundHelper.shouldUseNavineBackground(self, minecraft)) {
            return;
        }
        ScreenBackgroundHelper.renderNavineBackground(context, self.width, self.height);
        ci.cancel();
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void navineBlockPanorama(GuiGraphicsExtractor context, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && ScreenBackgroundHelper.shouldUseNavineBackground(self, minecraft)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void navineBlockBlurredBackground(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && ScreenBackgroundHelper.shouldUseNavineBackground(self, minecraft)) {
            ci.cancel();
        }
    }
}
