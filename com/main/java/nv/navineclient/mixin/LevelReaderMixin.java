package nv.navineclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelReader;
import nv.navineclient.module.world.BuildHeight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelReader.class)
public interface LevelReaderMixin {
    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void navine$buildHeight(CallbackInfoReturnable<Integer> cir) {
        LevelReader self = (LevelReader) (Object) this;
        if (!(self instanceof ClientLevel)) {
            return;
        }
        BuildHeight buildHeight = BuildHeight.INSTANCE;
        if (buildHeight != null && buildHeight.isActive()) {
            cir.setReturnValue(self.dimensionType().height() + buildHeight.getExtraHeight());
        }
    }
}
