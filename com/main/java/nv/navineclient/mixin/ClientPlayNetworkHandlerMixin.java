package nv.navineclient.mixin;

import nv.navineclient.commands.CommandManager;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.player.AntiCrash;
import nv.navineclient.module.combat.Velocity;
import nv.navineclient.util.AuthManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (interceptClientCommand(message, ci)) {
            return;
        }

        if (!AuthManager.isVerified()) return;
        
        AntiCrash antiCrash = (AntiCrash) ModuleManager.getModuleByName("AntiCrash");
        nv.navineclient.module.misc.Crashers crashers =
                (nv.navineclient.module.misc.Crashers) ModuleManager.getModuleByName("Crashers");
        if (antiCrash != null && antiCrash.isEnabled() && antiCrash.shouldPreventPacketCrash()
                && (crashers == null || !crashers.isEnabled())) {
            if (message.length() > 256) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSendCommandMessage(String command, CallbackInfo ci) {
        if (command == null) {
            return;
        }
        String prefix = CommandManager.getPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        interceptClientCommand(prefix + command, ci);
    }

    private boolean interceptClientCommand(String message, CallbackInfo ci) {
        String prefix = CommandManager.getPrefix();
        if (message != null && prefix != null && message.startsWith(prefix)) {
            if (nv.navineclient.ChatCommandBridge.tryHandleClientCommand(message, true)) {
                nv.navineclient.ChatCommandBridge.markMixinChannel();
                ci.cancel();
                return true;
            }
        }
        return false;
    }
    
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true, require = 0)
    private void onEntityVelocityUpdate(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        Velocity velocity = (Velocity) ModuleManager.getModuleByName("Velocity");
        if (velocity == null || !velocity.isEnabled()) return;
        
        if (packet.id() != mc.player.getId()) return;

        velocity.rollChance();
        double hMult = velocity.getHorizontalMultiplier();
        double vMult = velocity.getVerticalMultiplier();
        if (hMult >= 1.0 && vMult >= 1.0) return;
        
        Vec3 motion = packet.movement();
        if (hMult == 0.0 && vMult == 0.0) {
            ci.cancel();
            return;
        }
        
        mc.player.setDeltaMovement(motion.x * hMult, motion.y * vMult, motion.z * hMult);
        ci.cancel();
    }
}
