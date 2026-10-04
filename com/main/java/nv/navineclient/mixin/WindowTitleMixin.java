package nv.navineclient.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import nv.navineclient.NavineClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class WindowTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void navineCreateTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(navineBuildTitle());
    }

    @Inject(method = "updateTitle", at = @At("TAIL"))
    private void navineUpdateTitle(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        Window window = self.getWindow();
        if (window != null) {
            window.setTitle(navineBuildTitle());
        }
    }

    @Redirect(
            method = "updateTitle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"
            )
    )
    private void navineRedirectSetTitle(Window window, String ignored) {
        window.setTitle(navineBuildTitle());
    }

    private static String navineBuildTitle() {
        return NavineClient.buildWindowTitle();
    }
}
