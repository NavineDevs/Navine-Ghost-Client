package nv.navineclient.module.combat;

import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AutoTotem extends Module {
    private final NumberSetting healthThreshold = new NumberSetting("Health", "Health threshold", 10.0, 1.0, 20.0);
    private final BooleanSetting always = new BooleanSetting("Always", "Always keep totem in offhand", false);
    private final NumberSetting fallDamage = new NumberSetting("FallDamage", "Equip on fall distance", 0.0, 0.0, 20.0);

    public AutoTotem() {
        super("AutoTotem", "Automatically equips totems", Category.COMBAT);
        addSetting(healthThreshold);
        addSetting(always);
        addSetting(fallDamage);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        if (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) return;

        boolean shouldEquip = always.getValue()
                || mc.player.getHealth() <= healthThreshold.getValue()
                || (fallDamage.getValue() > 0 && mc.player.fallDistance >= fallDamage.getValue());

        if (!shouldEquip) return;

        int invSlot = findTotemSlot();
        if (invSlot == -1) return;

        int containerId = mc.player.containerMenu.containerId;
        int containerSlot = toContainerSlot(invSlot);
        mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, InventoryMenu.SHIELD_SLOT, 0, ContainerInput.PICKUP, mc.player);
        if (!mc.player.containerMenu.getSlot(containerSlot).getItem().isEmpty()) {
            mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private int findTotemSlot() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    private int toContainerSlot(int invIndex) {
        if (invIndex >= 0 && invIndex < 9) {
            return InventoryMenu.USE_ROW_SLOT_START + invIndex;
        }
        return invIndex;
    }
}
