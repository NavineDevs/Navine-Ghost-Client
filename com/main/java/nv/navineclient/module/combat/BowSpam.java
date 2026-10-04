package nv.navineclient.module.combat;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class BowSpam extends Module {
    private final NumberSetting chargeTime = new NumberSetting("Charge", "Charge ticks", 3.0, 1.0, 20.0);
    private final BooleanSetting requireHold = new BooleanSetting("Hold", "Require attack key held", false);
    private final BooleanSetting crossbow = new BooleanSetting("Crossbow", "Include crossbows", false);

    private int ticks = 0;

    public BowSpam() {
        super("BowSpam", "Rapidly fires bow with minimal charge", Category.COMBAT);
        addSetting(chargeTime);
        addSetting(requireHold);
        addSetting(crossbow);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        if (requireHold.getValue() && !mc.options.keyAttack.isDown()) {
            ticks = 0;
            return;
        }

        boolean holdingBow = mc.player.getMainHandItem().getItem() instanceof BowItem
                || (crossbow.getValue() && mc.player.getMainHandItem().getItem() instanceof CrossbowItem);
        if (!holdingBow) {
            ticks = 0;
            return;
        }

        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            ticks = 0;
            return;
        }

        ticks++;
        if (ticks >= chargeTime.getValue()) {
            mc.gameMode.releaseUsingItem(mc.player);
            ticks = 0;
        }
    }
}
