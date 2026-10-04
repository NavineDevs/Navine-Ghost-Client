package nv.navineclient.module.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AutoShield extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Threat detection range", 5.0, 2.0, 10.0);
    private final NumberSetting meleeRange = new NumberSetting("MeleeRange", "Melee threat range", 3.0, 1.0, 5.0);
    private final BooleanSetting blockArrows = new BooleanSetting("Arrows", "Block arrows", true);
    private final BooleanSetting blockMobs = new BooleanSetting("Mobs", "Block mobs", true);

    public AutoShield() {
        super("AutoShield", "Automatically blocks with shield", Category.COMBAT);
        addSetting(range);
        addSetting(meleeRange);
        addSetting(blockArrows);
        addSetting(blockMobs);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        boolean hasShield = mc.player.getOffhandItem().getItem() == Items.SHIELD
                || mc.player.getMainHandItem().getItem() == Items.SHIELD;
        if (!hasShield) return;

        boolean threatNearby = false;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            if (blockArrows.getValue() && entity instanceof Arrow arrow) {
                if (arrow.getOwner() != mc.player) {
                    threatNearby = true;
                    break;
                }
            }

            if (blockMobs.getValue() && entity instanceof Monster) {
                if (mc.player.distanceTo(entity) < meleeRange.getValue()) {
                    threatNearby = true;
                    break;
                }
            }

            if (entity instanceof Player && entity != mc.player) {
                if (mc.player.distanceTo(entity) < meleeRange.getValue()) {
                    threatNearby = true;
                    break;
                }
            }
        }

        mc.options.keyUse.setDown(threatNearby);
    }
}
