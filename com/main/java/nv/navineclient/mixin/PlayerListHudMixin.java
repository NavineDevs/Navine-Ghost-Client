package nv.navineclient.mixin;

import com.mojang.authlib.GameProfile;
import nv.navineclient.util.AuthManager;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetPlayerName(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        GameProfile profile = entry.getProfile();
        if (profile == null) return;
        
        String username = profile.name();
        if (username == null || username.isEmpty()) return;
        
        String tag = AuthManager.getTagForPlayer(username);
        if (!tag.isEmpty()) {
            Component originalText = cir.getReturnValue();
            String originalString = originalText.getString();
            
            if (!originalString.contains("[Navine")) {
                Component taggedText = Component.literal(tag).append(originalText);
                cir.setReturnValue(taggedText);
            }
        }
    }
}
