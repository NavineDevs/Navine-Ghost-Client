package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;

public class Sprint extends Module {
    private final BooleanSetting omniDirection = new BooleanSetting("Omni", "Sprint in all directions", false);
    private final BooleanSetting whileUsing = new BooleanSetting("WhileUsing", "Sprint while using items", false);
    private final BooleanSetting hungerCheck = new BooleanSetting("HungerCheck", "Require enough hunger", true);

    public Sprint() {
        super("Sprint", "Automatically sprints when moving", Category.MOVEMENT);
        addSetting(omniDirection);
        addSetting(whileUsing);
        addSetting(hungerCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null) {
            return;
        }
        if (mc.player.isSwimming() || mc.player.isFallFlying()) {
            return;
        }
        if (mc.player.isUsingItem() && !whileUsing.getValue()) {
            return;
        }
        if (hungerCheck.getValue() && mc.player.getFoodData().getFoodLevel() <= 6) {
            return;
        }
        if (mc.player.isCrouching() || mc.player.isShiftKeyDown()) {
            return;
        }

        boolean moving = mc.options.keyUp.isDown();
        if (omniDirection.getValue()) {
            moving = moving || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        }

        if (moving && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }
}
