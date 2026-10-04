package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import nv.navineclient.TickPipeline;
import nv.navineclient.commands.ServerCommand;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.player.FastPlace;
import nv.navineclient.module.player.PortalChat;
import nv.navineclient.util.ClientScreens;
import nv.navineclient.util.TeleportHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow
    private int rightClickDelay;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(CallbackInfo ci) {
        ServerCommand.recordTick();
        if (!TickPipeline.isFabricEventsActive() || nv.navineclient.NavineClient.GHOST) {
            TickPipeline.onStartTick((Minecraft) (Object) this);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void navine$tickEnd(CallbackInfo ci) {
        TeleportHelper.tick();
        FastPlace fastPlace = (FastPlace) ModuleManager.getModuleByName("FastPlace");
        if (fastPlace != null && fastPlace.isEnabled() && fastPlace.canPlace()) {
            rightClickDelay = 0;
        }
        if (!TickPipeline.isFabricEventsActive() || nv.navineclient.NavineClient.GHOST) {
            TickPipeline.onEndTick((Minecraft) (Object) this);
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"), require = 0)
    private void navine$portalChat(CallbackInfo ci) {
        if (!PortalChat.shouldAllowChat()) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || ClientScreens.get(mc) != null) {
            return;
        }
        boolean inNether = isInPortal(mc, net.minecraft.world.level.block.Blocks.NETHER_PORTAL);
        boolean inEnd = isInPortal(mc, net.minecraft.world.level.block.Blocks.END_PORTAL);
        PortalChat portalChat = PortalChat.getInstance();
        if (portalChat != null && !portalChat.matchesPortal(inNether, inEnd)) {
            return;
        }
        if ((inNether || inEnd) && mc.options.keyChat.consumeClick()) {
            ClientScreens.set(mc, new ChatScreen("", false));
        }
    }

    private static boolean isInPortal(Minecraft mc, net.minecraft.world.level.block.Block block) {
        if (mc.level == null) {
            return false;
        }
        net.minecraft.core.BlockPos pos = mc.player.blockPosition();
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
        return state.is(block);
    }
}
