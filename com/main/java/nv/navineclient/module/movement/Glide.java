package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Glide extends Module {
    private final NumberSetting fallSpeed = new NumberSetting("Speed", "Fall speed", 0.1, 0.01, 0.5);
    private final NumberSetting minFallDist = new NumberSetting("MinFall", "Min fall distance", 0.5, 0.0, 5.0);
    private final BooleanSetting disableInWater = new BooleanSetting("NoWater", "Disable in water", true);

    public Glide() {
        super("Glide", "Slowly fall down", Category.MOVEMENT);
        addSetting(fallSpeed);
        addSetting(minFallDist);
        addSetting(disableInWater);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (disableInWater.getValue() && mc.player.isInWater()) return;
        if (mc.player.onGround()) return;
        if (mc.player.getDeltaMovement().y >= 0) return;
        if (mc.player.fallDistance < minFallDist.getValue()) return;

        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -fallSpeed.getValue(), mc.player.getDeltaMovement().z);
    }
}
