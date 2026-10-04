package nv.navineclient.module.player;

import nv.navineclient.util.ClientScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class ChestStealer extends Module {
    private final NumberSetting delay = new NumberSetting("Delay", "Delay between takes (ticks)", 2.0, 0.0, 10.0);
    private final BooleanSetting stealStack = new BooleanSetting("StealStack", "Quick-move entire stacks", true);
    private final NumberSetting closeDelay = new NumberSetting("CloseDelay", "Delay before closing (ticks)", 0.0, 0.0, 20.0);

    private int ticks = 0;
    private int emptyTicks = 0;

    public ChestStealer() {
        super("ChestStealer", "Automatically takes items from chests", Category.PLAYER);
        addSetting(delay);
        addSetting(stealStack);
        addSetting(closeDelay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        if (!(ClientScreens.get(mc) instanceof ContainerScreen container) || !(container.getMenu() instanceof ChestMenu chestMenu)) {
            emptyTicks = 0;
            return;
        }

        ticks++;
        if (ticks < delay.getValue()) return;
        ticks = 0;

        int containerSlots = chestMenu.getRowCount() * 9;
        boolean foundItem = false;

        for (int i = 0; i < containerSlots; i++) {
            if (!chestMenu.getSlot(i).getItem().isEmpty()) {
                mc.gameMode.handleContainerInput(
                        chestMenu.containerId,
                        i,
                        0,
                        stealStack.getValue() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP,
                        mc.player
                );
                foundItem = true;
                emptyTicks = 0;
                return;
            }
        }

        if (!foundItem) {
            emptyTicks++;
            if (emptyTicks >= closeDelay.getValue().intValue()) {
                mc.player.closeContainer();
                emptyTicks = 0;
            }
        }
    }
}
