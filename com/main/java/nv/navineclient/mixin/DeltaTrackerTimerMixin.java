package nv.navineclient.mixin;

import net.minecraft.client.DeltaTracker;
import nv.navineclient.module.world.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DeltaTracker.Timer.class)
public class DeltaTrackerTimerMixin {
    @ModifyVariable(method = "advanceGameTime", at = @At(value = "STORE"), ordinal = 0)
    private float navine$timerSpeed(float deltaTicks) {
        float multiplier = Timer.getMultiplier();
        if (multiplier > 0.0f && multiplier != 1.0f) {
            return deltaTicks * multiplier;
        }
        return deltaTicks;
    }
}
