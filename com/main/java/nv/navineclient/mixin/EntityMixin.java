package nv.navineclient.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.combat.Hitboxes;
import nv.navineclient.module.render.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetBoundingBox(CallbackInfoReturnable<AABB> cir) {
        Hitboxes hitboxes = (Hitboxes) ModuleManager.getModuleByName("Hitboxes");
        if (hitboxes != null && hitboxes.isEnabled()) {
            AABB original = cir.getReturnValue();
            double expansion = hitboxes.getExpansionAmount();
            double width = hitboxes.getHitboxWidth();
            double height = hitboxes.getHitboxHeight();
            double centerX = (original.minX + original.maxX) / 2.0;
            double centerZ = (original.minZ + original.maxZ) / 2.0;
            AABB resized = new AABB(
                centerX - width / 2.0, original.minY, centerZ - width / 2.0,
                centerX + width / 2.0, original.minY + height, centerZ + width / 2.0
            );
            if (expansion > 0) {
                resized = resized.inflate(expansion, expansion, expansion);
            }
            cir.setReturnValue(resized);
        }
    }
    
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (entity instanceof LivingEntity living) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            nv.navineclient.module.player.AntiCrash antiCrash =
                (nv.navineclient.module.player.AntiCrash) ModuleManager.getModuleByName("AntiCrash");
            if (antiCrash != null && antiCrash.isEnabled()) {
                boolean invalid = Double.isNaN(living.getX()) || Double.isNaN(living.getY()) || Double.isNaN(living.getZ())
                        || Double.isInfinite(living.getX()) || Double.isInfinite(living.getY()) || Double.isInfinite(living.getZ());
                if (invalid) {
                    if (living == client.player && antiCrash.shouldProtectSelf()) {
                        antiCrash.recordPreventedCrash("self-entity");
                        living.setPos(living.xo, living.yo, living.zo);
                    } else if (living != client.player && antiCrash.shouldPreventEntityCrash()) {
                        antiCrash.recordPreventedCrash("entity");
                        living.setPos(0, 64, 0);
                    }
                }
            }
        }

        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (player != net.minecraft.client.Minecraft.getInstance().player) {
                Nametags nametags = (Nametags) ModuleManager.getModuleByName("Nametags");
                if (nametags != null && nametags.isEnabled()) {
                    try {
                        player.setCustomName(null);
                        player.setCustomNameVisible(false);
                    } catch (Exception ignored) {
                    }
                } else {
                    try {
                        if (player.getCustomName() != null && player.getCustomName().getString().contains("HP")) {
                            player.setCustomName(null);
                            player.setCustomNameVisible(false);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
