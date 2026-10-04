package nv.navineclient.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.player.AirPlace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void navine$airPlace(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        AirPlace airPlace = (AirPlace) ModuleManager.getModuleByName("AirPlace");
        if (airPlace == null || !airPlace.isEnabled() || context == null) {
            return;
        }
        if (airPlace.canPlaceAt(context.getClickedPos())) {
            cir.setReturnValue(true);
        }
    }
}
