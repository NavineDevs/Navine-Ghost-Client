package nv.navineclient.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.combat.Velocity;
import nv.navineclient.module.movement.Step;
import nv.navineclient.module.movement.NoSlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // Removed isUsingItem cancellation - it doesn't prevent slowdown, just breaks item usage detection
    
    // NoSlow - getMovementSpeed override to prevent slowdown
    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetMovementSpeed(CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof Player)) return;
        if (Minecraft.getInstance().player != (Object) this) return;
        
        NoSlow noSlow = (NoSlow) ModuleManager.getModuleByName("NoSlow");
        if (noSlow != null && noSlow.isEnabled()) {
            float originalSpeed = cir.getReturnValue();
            Minecraft mc = Minecraft.getInstance();
            
            // Check if using item
            if (noSlow.noItemSlow() && ((LivingEntity)(Object)this).isUsingItem()) {
                // Return full speed (no slowdown from items)
                cir.setReturnValue(originalSpeed * 5.0f); // Multiply to counteract slowdown
            }
            
            // Check if in web/honey/slime/water
            if (mc != null && mc.level != null) {
                LivingEntity entity = (LivingEntity)(Object)this;
                net.minecraft.core.BlockPos pos = entity.blockPosition();
                net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
                
                if (noSlow.noWebSlow() && state.is(Blocks.COBWEB)) {
                    cir.setReturnValue(originalSpeed * 10.0f); // Strong boost for webs
                }
                if (noSlow.noHoneySlow() && (state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.SLIME_BLOCK))) {
                    cir.setReturnValue(originalSpeed * 5.0f);
                }
                if (noSlow.noWaterSlow() && entity.isInWater()) {
                    cir.setReturnValue(originalSpeed * 3.0f); // Boost for water
                }
            }
        }
    }
    
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true, require = 0)
    private void navine$stepHeight(CallbackInfoReturnable<Float> cir) {
        if (Minecraft.getInstance().player != (Object) this) {
            return;
        }
        Step step = Step.INSTANCE;
        if (step != null && step.isEnabled()) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), step.getStepHeight()));
        }
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true, require = 0)
    private void onTakeKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (!((Object) this instanceof Player)) return;
        if (Minecraft.getInstance().player != (Object) this) return;
        
        Velocity velocity = (Velocity) ModuleManager.getModuleByName("Velocity");
        if (velocity != null && velocity.isEnabled()) {
            velocity.rollChance();
            double hMult = velocity.getHorizontalMultiplier();
            double vMult = velocity.getVerticalMultiplier();
            if (hMult < 1.0 || vMult < 1.0) {
                LivingEntity self = (LivingEntity) (Object) this;
                double len = Math.sqrt(x * x + z * z);
                if (len > 0.0) {
                    double nx = x / len;
                    double nz = z / len;
                    self.setDeltaMovement(
                        self.getDeltaMovement().x + nx * strength * hMult,
                        self.getDeltaMovement().y + strength * 0.36 * vMult,
                        self.getDeltaMovement().z + nz * strength * hMult
                    );
                }
                ci.cancel();
            }
        }
        
        // KnockbackPlus applies its effect on attack, not on receiving knockback
        // (handled in attack mixin or module tick)
    }
    
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true, require = 0)
    private void onHandleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player)) return;
        if (Minecraft.getInstance().player != (Object) this) return;
        
        nv.navineclient.module.movement.NoFall noFall = nv.navineclient.module.movement.NoFall.INSTANCE;
        if (noFall != null && noFall.shouldCancelFallDamage()) {
            cir.setReturnValue(false);
        }
    }
    
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true, require = 0)
    private void onDamage(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player)) return;
        if (Minecraft.getInstance().player != (Object) this) return;
        
        nv.navineclient.module.combat.GodMode godMode = (nv.navineclient.module.combat.GodMode) ModuleManager.getModuleByName("GodMode");
        if (godMode != null && godMode.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
