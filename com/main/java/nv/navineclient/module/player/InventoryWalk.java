package nv.navineclient.module.player;

import nv.navineclient.util.ClientScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.util.NavineInput;

public class InventoryWalk extends Module {
    private final BooleanSetting sneak = new BooleanSetting("Sneak", "Allow sneaking in inventory", true);
    private final BooleanSetting jump = new BooleanSetting("Jump", "Allow jumping in inventory", true);
    private final BooleanSetting sprint = new BooleanSetting("Sprint", "Allow sprinting in inventory", true);

    public InventoryWalk() {
        super("InventoryWalk", "Move while in inventory", Category.PLAYER);
        addSetting(sneak);
        addSetting(jump);
        addSetting(sprint);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null || mc.getWindow() == null) return;
        if (!(ClientScreens.get(mc) instanceof AbstractContainerScreen)) return;

        mc.options.keyUp.setDown(NavineInput.isKeyDown(NavineInput.KEY_W));
        mc.options.keyDown.setDown(NavineInput.isKeyDown(NavineInput.KEY_S));
        mc.options.keyLeft.setDown(NavineInput.isKeyDown(NavineInput.KEY_A));
        mc.options.keyRight.setDown(NavineInput.isKeyDown(NavineInput.KEY_D));

        if (jump.getValue()) {
            mc.options.keyJump.setDown(NavineInput.isKeyDown(NavineInput.KEY_SPACE));
        }
        if (sneak.getValue()) {
            mc.options.keyShift.setDown(NavineInput.isKeyDown(NavineInput.KEY_LEFT_SHIFT)
                || NavineInput.isKeyDown(NavineInput.KEY_RIGHT_SHIFT));
        }
        if (sprint.getValue()) {
            mc.options.keySprint.setDown(NavineInput.isKeyDown(NavineInput.KEY_LEFT_CONTROL)
                || NavineInput.isKeyDown(NavineInput.KEY_RIGHT_CONTROL));
        }
    }
}
