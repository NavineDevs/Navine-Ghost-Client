package nv.navineclient.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import nv.navineclient.module.render.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockFaceMixin {

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void navine$xrayFace(BlockState state, BlockState neighborState, Direction direction,
                                        CallbackInfoReturnable<Boolean> cir) {
        XRay xray = XRay.INSTANCE;
        if (xray == null || !xray.isEnabled()) {
            return;
        }
        if (xray.isVisible(state.getBlock())) {
            return;
        }
        if (xray.showCaveSilhouette() && neighborState.isAir()) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }
}
