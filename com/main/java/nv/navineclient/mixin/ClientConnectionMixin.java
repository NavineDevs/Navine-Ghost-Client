package nv.navineclient.mixin;

import nv.navineclient.module.misc.PacketLogger;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.module.movement.Blink;
import nv.navineclient.module.world.DevMod;
import nv.navineclient.NavineClient;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"), require = 0)
    private void navine$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketLogger logger = (PacketLogger) nv.navineclient.module.ModuleManager.getModuleByName("PacketLogger");
        if (logger != null && logger.isEnabled()) {
            logger.logIncoming(packet);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (DevMod.shouldLogPackets()) {
            NavineClient.LOGGER.info("[DevMod] Outgoing: {}", packet.getClass().getSimpleName());
        }

        PacketLogger logger = (PacketLogger) nv.navineclient.module.ModuleManager.getModuleByName("PacketLogger");
        if (logger != null && logger.isEnabled()) {
            logger.logOutgoing(packet);
        }

        if (packet instanceof ServerboundMovePlayerPacket movePacket) {
            if (Blink.shouldCancelPacket()) {
                Blink.storePacket(movePacket);
                ci.cancel();
            } else {
                AcBypassUtil.recordMovePacket();
            }
        }
    }
}
