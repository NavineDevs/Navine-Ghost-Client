package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import nv.navineclient.util.MenuMusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void navine$blockMenuMusic(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && MenuMusicManager.shouldSuppressVanillaMusic()) {
            ci.cancel();
        }
    }

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void navine$blockMenuMusicStart(Music music, CallbackInfo ci) {
        if (MenuMusicManager.shouldSuppressVanillaMusic()) {
            ci.cancel();
        }
    }
}
