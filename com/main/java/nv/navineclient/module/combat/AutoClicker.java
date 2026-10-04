package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AutoClicker extends Module {
    private final NumberSetting cps = new NumberSetting("CPS", "Clicks per second", 10.0, 1.0, 20.0);
    private final BooleanSetting leftClick = new BooleanSetting("Left", "Auto left click", true);
    private final BooleanSetting rightClick = new BooleanSetting("Right", "Auto right click", false);
    private final BooleanSetting requireHold = new BooleanSetting("Hold", "Require mouse hold", false);

    private long lastClick = 0;
    private int clickTickCounter = 0;

    public AutoClicker() {
        super("AutoClicker", "Automatically clicks", Category.COMBAT);
        addSetting(cps);
        addSetting(leftClick);
        addSetting(rightClick);
        addSetting(requireHold);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        
        clickTickCounter++;
        
        long time = System.currentTimeMillis();
        double baseDelay = 1000.0 / cps.getValue();
        // Human-like timing variation (±4%) for anticheat evasion
        double variation = baseDelay * 0.04 * (Math.sin(clickTickCounter * 0.12) + Math.cos(clickTickCounter * 0.08));
        long delay = (long) (baseDelay + variation);
        
        if (time - lastClick < delay) return;
        
        boolean shouldLeftClick = leftClick.getValue() && (!requireHold.getValue() || mc.options.keyAttack.isDown());
        boolean shouldRightClick = rightClick.getValue() && (!requireHold.getValue() || mc.options.keyUse.isDown());
        
        if (shouldLeftClick) {
            if (mc.crosshairPickEntity != null) {
                mc.gameMode.attack(mc.player, mc.crosshairPickEntity);
            }
            ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
            lastClick = time;
        }
        
        if (shouldRightClick) {
            if (mc.hitResult != null) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            lastClick = time;
        }
    }
}
