package nv.navineclient.commands;

import nv.navineclient.util.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import nv.navineclient.util.ChatUtils;

public class DupeCommand extends Command {
    private static final Minecraft mc = Minecraft.getInstance();
    
    public DupeCommand() {
        super("dupe", "Duplicates the item in your hand", ".dupe [amount]", "d");
    }
    
    @Override
    public void onCommand(String[] args) {
        if (mc.player == null) {
            ChatUtils.message("§cYou must be in a world!");
            return;
        }
        
        ItemStack heldStack = mc.player.getMainHandItem();
        if (heldStack.isEmpty()) {
            ChatUtils.message("§cYou must be holding an item!");
            return;
        }
        
        int amount = args.length > 0 ? parseInt(args[0], 1) : 1;
        if (amount < 1) amount = 1;
        if (amount > 64) amount = 64;
        
        // Create copies of the item
        int duplicated = 0;
        for (int i = 0; i < amount; i++) {
            ItemStack copy = heldStack.copy();
            copy.setCount(Math.min(copy.getCount(), heldStack.getMaxStackSize()));
            
            // Try to add to inventory
            if (mc.player.getInventory().add(copy)) {
                duplicated++;
            } else {
                // If inventory is full, try dropping
                if (mc.gameMode != null) {
                    ClientAccess.drop(mc.player, copy, false);
                    duplicated++;
                }
            }
        }
        
        if (duplicated > 0) {
            ChatUtils.message("§aDuplicated " + duplicated + "x " + heldStack.getHoverName().getString());
        } else {
            ChatUtils.message("§cFailed to duplicate item! Inventory might be full.");
        }
    }
    
    private int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
