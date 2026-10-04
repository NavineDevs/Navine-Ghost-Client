package nv.navineclient.mixin;

import nv.navineclient.util.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import nv.navineclient.ui.CoolButton;
import nv.navineclient.util.NavineButtonRenderer;
import nv.navineclient.util.UiAnim;
import nv.navineclient.util.ScreenBackgroundHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class MenuButtonMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void navineStyleButton(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractWidget self = (AbstractWidget) (Object) this;

        if (self instanceof CoolButton) {
            return;
        }
        if (self instanceof ImageButton || self instanceof LockIconButton) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || ClientScreens.get(mc) == null) {
            return;
        }

        Screen screen = ClientScreens.get(mc);
        if (screen instanceof AbstractContainerScreen) {
            return;
        }
        if (!ScreenBackgroundHelper.shouldStyleNavineButtons(screen, mc)) {
            return;
        }

        float hoverAnim = UiAnim.trackHover(self, self.isHoveredOrFocused() && self.active, delta);
        NavineButtonRenderer.renderMenuStyle(context, self, -1, hoverAnim);
        ci.cancel();
    }
}
