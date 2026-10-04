package nv.navineclient.module.player;

import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AutoFish extends Module {
    private final NumberSetting delay = new NumberSetting("Delay", "Recast delay (ticks)", 10.0, 5.0, 40.0);
    private final NumberSetting timeout = new NumberSetting("Timeout", "Recast timeout (ticks)", 200.0, 50.0, 600.0);
    private final NumberSetting biteSensitivity = new NumberSetting("Sensitivity", "Bite detection threshold", 0.03, 0.01, 0.15);
    private final BooleanSetting antiAfk = new BooleanSetting("AntiAFK", "Small camera movement", false);

    private int waitTicks;
    private int castTicks;
    private boolean caughtFish;
    private double lastBobberY;

    public AutoFish() {
        super("AutoFish", "Automatically catches fish", Category.PLAYER);
        addSetting(delay);
        addSetting(timeout);
        addSetting(biteSensitivity);
        addSetting(antiAfk);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        if (antiAfk.getValue() && castTicks % 100 == 0) {
            mc.player.setYRot(mc.player.getYRot() + 1.0f);
        }

        if (!(mc.player.getMainHandItem().getItem() instanceof FishingRodItem)) {
            return;
        }

        FishingHook bobber = mc.player.fishing;
        castTicks++;

        if (bobber == null) {
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            waitTicks = delay.getValue().intValue();
            caughtFish = false;
            castTicks = 0;
            lastBobberY = 0;
            return;
        }

        if (castTicks > timeout.getValue().intValue()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            waitTicks = delay.getValue().intValue();
            caughtFish = false;
            castTicks = 0;
            return;
        }

        double yDelta = bobber.getY() - lastBobberY;
        lastBobberY = bobber.getY();
        boolean bite = bobber.isInWater()
                && (bobber.getDeltaMovement().y < -biteSensitivity.getValue()
                || yDelta < -biteSensitivity.getValue());

        if (bite && !caughtFish) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            caughtFish = true;
            waitTicks = delay.getValue().intValue();
            castTicks = 0;
        }
    }
}
