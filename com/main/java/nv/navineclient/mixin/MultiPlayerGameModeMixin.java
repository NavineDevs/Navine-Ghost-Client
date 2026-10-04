package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.combat.Criticals;
import nv.navineclient.module.combat.KnockbackPlus;
import nv.navineclient.module.combat.ShieldBypass;
import nv.navineclient.module.combat.WTap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import nv.navineclient.module.movement.SafeMine;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.player.FastPlace;
import nv.navineclient.module.world.BedrockPlace;
import nv.navineclient.util.PlaceConflictGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void navine$onAttack(Player player, Entity target, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || player != mc.player) {
            return;
        }

        WTap wTap = WTap.INSTANCE;
        if (wTap != null && wTap.isEnabled()) {
            wTap.onPlayerAttack(target);
        }

        Criticals criticals = (Criticals) ModuleManager.getModuleByName("Criticals");
        if (criticals != null && criticals.isEnabled()) {
            criticals.doCritical();
        }

        if (target instanceof LivingEntity living && ShieldBypass.shouldBypass() && ShieldBypass.isBlocking(living)) {
            String mode = ShieldBypass.INSTANCE != null ? ShieldBypass.INSTANCE.getMode() : "Packet";
            if (mode.equals("Angle")) {
                mc.player.setYRot(mc.player.getYRot() + 90.0f);
            } else if (mode.equals("Timing")) {
                mc.player.setYRot(mc.player.getYRot() + 45.0f);
                mc.player.setXRot(mc.player.getXRot() + 15.0f);
            } else if (mode.equals("Packet")) {
                mc.player.setYRot(living.getYRot() + 180.0f);
                mc.player.setXRot(0.0f);
            }
        }

        KnockbackPlus knockbackPlus = (KnockbackPlus) ModuleManager.getModuleByName("KnockbackPlus");
        if (knockbackPlus != null && knockbackPlus.isEnabled() && target instanceof LivingEntity living) {
            knockbackPlus.doKnockbackBoost(living);
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void navine$safeMine(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        SafeMine safeMine = (SafeMine) ModuleManager.getModuleByName("SafeMine");
        if (safeMine != null && safeMine.shouldCancelMining(pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void navine$fastPlace(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (PlaceConflictGuard.shouldSuppressVanillaUse()) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }
        if (!PlaceConflictGuard.isModInitiated()) {
            BedrockPlace bedrockPlace = (BedrockPlace) ModuleManager.getModuleByName("BedrockPlace");
            if (bedrockPlace != null && bedrockPlace.shouldBlockVanillaUse()) {
                cir.setReturnValue(InteractionResult.PASS);
                return;
            }
        }
        FastPlace fastPlace = (FastPlace) ModuleManager.getModuleByName("FastPlace");
        if (fastPlace != null && fastPlace.isEnabled() && fastPlace.canPlace()) {
            fastPlace.onPlace();
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"), require = 0)
    private void navine$resetPlaceGuard(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        PlaceConflictGuard.endModPlacement();
    }
}
