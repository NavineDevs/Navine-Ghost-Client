package nv.navineclient.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import nv.navineclient.module.render.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateMixin {

    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void navine$xrayShape(CallbackInfoReturnable<RenderShape> cir) {
        XRay xray = XRay.INSTANCE;
        if (xray == null || !xray.isEnabled()) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        if (!xray.isVisible(state.getBlock())) {
            if (xray.getOpacity() >= 0.95) {
                cir.setReturnValue(RenderShape.INVISIBLE);
            }
        }
    }

    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true, require = 0)
    private void navine$xraySkipRendering(net.minecraft.world.level.block.state.BlockState neighborState,
                                          net.minecraft.core.Direction direction,
                                          CallbackInfoReturnable<Boolean> cir) {
        XRay xray = XRay.INSTANCE;
        if (xray == null || !xray.isEnabled()) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        if (!xray.isVisible(state.getBlock()) && xray.getOpacity() >= 0.95) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true, require = 0)
    private void navine$xrayOcclude(CallbackInfoReturnable<Boolean> cir) {
        XRay xray = XRay.INSTANCE;
        if (xray == null || !xray.isEnabled()) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        if (!xray.isVisible(state.getBlock()) && xray.getOpacity() >= 0.95) {
            cir.setReturnValue(false);
        }
    }
}
