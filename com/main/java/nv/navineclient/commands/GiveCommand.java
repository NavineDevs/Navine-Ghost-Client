package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import nv.navineclient.util.ChatUtils;

public class GiveCommand extends Command {
    private static final Minecraft mc = Minecraft.getInstance();
    
    public GiveCommand() {
        super("give", "Gives you an item", ".give <item> [amount]", "g", "i");
    }
    
    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            showUsage();
            return;
        }
        
        if (mc.player == null) {
            ChatUtils.message("§cYou must be in a world!");
            return;
        }
        
        String itemName = args[0];
        int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
        
        Identifier id = Identifier.tryParse(itemName);
        if (id == null) {
            // Try with minecraft: prefix
            id = Identifier.tryParse("minecraft:" + itemName);
        }
        
        if (id == null) {
            ChatUtils.message("§cInvalid item: " + itemName);
            return;
        }
        
        Item item = BuiltInRegistries.ITEM.get(id).map(net.minecraft.core.Holder::value).orElse(null);
        if (item == null) {
            ChatUtils.message("§cItem not found: " + itemName);
            return;
        }
        
        ItemStack stack = new ItemStack(item, amount);
        mc.player.getInventory().add(stack);
        ChatUtils.message("§aGave you " + amount + "x " + itemName);
    }
    
    private int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
