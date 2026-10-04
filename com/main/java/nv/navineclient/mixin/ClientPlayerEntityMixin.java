package nv.navineclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nv.navineclient.util.NavinePathfinder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.combat.Criticals;
import nv.navineclient.module.combat.KnockbackPlus;
import nv.navineclient.module.combat.Reach;
import nv.navineclient.module.combat.ShieldBypass;
import nv.navineclient.module.movement.NoSlow;
import nv.navineclient.module.movement.SafeWalk;
import nv.navineclient.module.player.FastPlace;
import nv.navineclient.module.player.SpeedMine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
    // Note: getBlockInteractionRange, getEntityInteractionRange, and attack methods don't exist in 1.21.10
    // Reach and attack functionality handled via module tick handlers
    
    @Inject(method = "aiStep", at = @At("HEAD"), require = 0)
    private void onTickMovementHead(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        
        SafeWalk safeWalk = SafeWalk.INSTANCE;
        if (safeWalk != null && safeWalk.isEnabled() && mc != null && mc.player != null && mc.level != null) {
            if (player.onGround()) {
                double edgeDist = safeWalk.edgeDistance();
                float yaw = player.getYRot();
                double dx = -Math.sin(Math.toRadians(yaw)) * edgeDist;
                double dz = Math.cos(Math.toRadians(yaw)) * edgeDist;
                net.minecraft.core.BlockPos below = player.blockPosition().below();
                net.minecraft.core.BlockPos frontBelow = net.minecraft.core.BlockPos.containing(
                    player.getX() + dx, player.getY() - 1.0, player.getZ() + dz);
                boolean edgeAhead = mc.level.getBlockState(frontBelow).isAir();
                boolean noGround = mc.level.getBlockState(below).isAir();

                if (edgeAhead || noGround) {
                    boolean forward = mc.options != null && mc.options.keyUp.isDown();
                    if (!safeWalk.onlyForward() || forward) {
                        if (safeWalk.sneakEdges()) {
                            player.setShiftKeyDown(true);
                        } else if (forward) {
                            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                        }
                    }
                }
            }
        }
    }
    
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER), require = 0)
    private void navine$applyPathfinderInput(CallbackInfo ci) {
        if (NavinePathfinder.isActive()) {
            NavinePathfinder.applyInput((LocalPlayer) (Object) this);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"), require = 0)
    private void onTickMovementTail(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        
        // NoSlow - restore movement speed after movement calculation
        NoSlow noSlow = (NoSlow) ModuleManager.getModuleByName("NoSlow");
        if (noSlow != null && noSlow.isEnabled() && mc != null && mc.options != null && mc.level != null) {
            boolean shouldBypass = false;
            
            // Check if in web/honey/slime
            net.minecraft.core.BlockPos pos = player.blockPosition();
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
            if (noSlow.noWebSlow() && state.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
                shouldBypass = true;
            }
            if (noSlow.noHoneySlow() && (state.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK) || 
                                         state.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK))) {
                shouldBypass = true;
            }
            
            // Check if in water
            if (noSlow.noWaterSlow() && player.isInWater()) {
                shouldBypass = true;
            }
            
            // Check if using item
            if (noSlow.noItemSlow() && player.isUsingItem()) {
                shouldBypass = true;
            }
            
            if (shouldBypass) {
                Vec3 velocity = player.getDeltaMovement();
                // Maintain movement speed
                if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || 
                    mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
                    float yaw = player.getYRot();
                    // Get base speed from player's movement attributes
                    double baseSpeed = player.getSpeed();
                    if (baseSpeed <= 0) baseSpeed = 0.1; // Fallback
                    
                    // Apply sprint multiplier if sprinting
                    if (player.isSprinting()) {
                        baseSpeed *= 1.3;
                    }
                    
                    double dx = 0, dz = 0;
                    
                    if (mc.options.keyUp.isDown()) {
                        dx -= Math.sin(Math.toRadians(yaw)) * baseSpeed;
                        dz += Math.cos(Math.toRadians(yaw)) * baseSpeed;
                    }
                    if (mc.options.keyDown.isDown()) {
                        dx += Math.sin(Math.toRadians(yaw)) * baseSpeed * 0.5; // Slower backwards
                        dz -= Math.cos(Math.toRadians(yaw)) * baseSpeed * 0.5;
                    }
                    if (mc.options.keyLeft.isDown()) {
                        dx -= Math.cos(Math.toRadians(yaw)) * baseSpeed * 0.8; // Slightly slower sideways
                        dz -= Math.sin(Math.toRadians(yaw)) * baseSpeed * 0.8;
                    }
                    if (mc.options.keyRight.isDown()) {
                        dx += Math.cos(Math.toRadians(yaw)) * baseSpeed * 0.8;
                        dz += Math.sin(Math.toRadians(yaw)) * baseSpeed * 0.8;
                    }
                    
                    if (dx != 0 || dz != 0) {
                        // Preserve vertical velocity
                        player.setDeltaMovement(dx, velocity.y, dz);
                    }
                }
            }
        }
    }
    
}
