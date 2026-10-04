package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AutoMacer extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Attack range", 4.0, 1.0, 6.0);
    private final NumberSetting fallDist = new NumberSetting("FallDist", "Min fall distance", 1.5, 0.5, 5.0);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Target mobs", false);

    public AutoMacer() {
        super("AutoMacer", "Auto attacks with mace when falling", Category.COMBAT);
        addSetting(range);
        addSetting(fallDist);
        addSetting(mobs);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.player.getMainHandItem().getItem() != Items.MACE) return;
        if (mc.player.onGround()) return;
        if (mc.player.getDeltaMovement().y >= 0) return;
        if (mc.player.fallDistance < fallDist.getValue()) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == mc.player) continue;
            if (!(entity instanceof Player) && !(mobs.getValue() && entity instanceof Monster)) continue;
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            mc.gameMode.attack(mc.player, entity);
            ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
            break;
        }
    }
}
