package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.combat.Reach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true, require = 0)
    private void navine$entityReach(CallbackInfoReturnable<Double> cir) {
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }
        if (Minecraft.getInstance().player != (Object) this) {
            return;
        }
        Reach reach = Reach.INSTANCE;
        if (reach != null && reach.isEnabled()) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), reach.getReachDistance()));
        }
    }

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true, require = 0)
    private void navine$blockReach(CallbackInfoReturnable<Double> cir) {
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }
        if (Minecraft.getInstance().player != (Object) this) {
            return;
        }
        Reach reach = Reach.INSTANCE;
        if (reach != null && reach.isEnabled()) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), reach.getBlockReachDistance()));
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true, require = 0)
    private void navine$speedMine(net.minecraft.world.level.block.state.BlockState state, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }
        if (Minecraft.getInstance().player != (Object) this) {
            return;
        }
        float mult = nv.navineclient.module.player.SpeedMine.getBreakSpeedMultiplier();
        if (mult > 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * mult);
        }
    }
}
