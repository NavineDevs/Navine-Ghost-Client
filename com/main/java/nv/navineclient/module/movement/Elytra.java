package nv.navineclient.module.movement;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import net.minecraft.world.inventory.InventoryMenu;

public class Elytra extends Module {
    private final BooleanSetting autoEquip = new BooleanSetting("AutoEquip", "Auto equip elytra", true);
    private final BooleanSetting autoBoost = new BooleanSetting("AutoBoost", "Auto boost with fireworks", true);
    private final NumberSetting boostDelay = new NumberSetting("BoostDelay", "Delay between boosts (ticks)", 10.0, 1.0, 50.0);
    private final BooleanSetting infiniteDuration = new BooleanSetting("Infinite", "Infinite elytra duration", false);

    private int boostTicks = 0;

    public Elytra() {
        super("Elytra", "Enhanced elytra flight with auto boost", Category.MOVEMENT);
        addSetting(autoEquip);
        addSetting(autoBoost);
        addSetting(boostDelay);
        addSetting(infiniteDuration);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Auto equip elytra from inventory
        if (autoEquip.getValue() && !hasElytra()) {
            equipElytra();
        }

        // Auto boost with fireworks
        if (autoBoost.getValue() && hasElytra() && mc.player.getDeltaMovement().y < 0 && !mc.player.onGround()) {
            boostTicks++;
            if (boostTicks >= boostDelay.getValue().intValue()) {
                // Find and use firework rocket
                useFirework();
                boostTicks = 0;
            }
        }

        // Infinite duration
        if (infiniteDuration.getValue() && hasElytra()) {
            ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.getItem() == Items.ELYTRA && chest.isDamaged()) {
                chest.setDamageValue(0);
            }
        }
    }

    private boolean hasElytra() {
        return mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private void equipElytra() {
        if (mc.player == null || mc.gameMode == null) return;
        int chestSlot = InventoryMenu.ARMOR_SLOT_START + 1;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.ELYTRA) {
                int containerId = mc.player.containerMenu.containerId;
                int containerSlot = i < 9 ? InventoryMenu.USE_ROW_SLOT_START + i : i;
                mc.gameMode.handleContainerInput(containerId, containerSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(containerId, chestSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                if (!mc.player.getInventory().getItem(i).isEmpty()) {
                    mc.gameMode.handleContainerInput(containerId, containerSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                }
                break;
            }
        }
    }

    private void useFirework() {
        if (mc.player == null || mc.gameMode == null) return;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.FIREWORK_ROCKET) {
                int oldSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(i);
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.player.getInventory().setSelectedSlot(oldSlot);
                break;
            }
        }
    }
}
