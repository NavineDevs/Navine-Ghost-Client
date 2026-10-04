package nv.navineclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public class InvseeCommand extends Command {
    public InvseeCommand() {
        super("invsee", "View a player's visible inventory", ".invsee <player>");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        if (args.length == 0) {
            ChatUtils.message("§b=== Invsee ===");
            ChatUtils.message("§fUsage: §7.invsee <player>");
            ChatUtils.message("");
            ChatUtils.message("§6Nearby Players:");
            int count = 0;
            for (Player player : mc.level.players()) {
                if (player != mc.player) {
                    double dist = mc.player.distanceTo(player);
                    ChatUtils.message(String.format("  §f%s §7- %.1f blocks", player.getName().getString(), dist));
                    count++;
                }
            }
            if (count == 0) {
                ChatUtils.message("  §7No players nearby");
            }
            return;
        }
        
        String targetName = args[0].toLowerCase();
        Player target = null;
        
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.getName().getString().toLowerCase().equals(targetName)) {
                target = player;
                break;
            }
        }
        
        if (target == null) {
            for (Player player : mc.level.players()) {
                if (player != mc.player && player.getName().getString().toLowerCase().startsWith(targetName)) {
                    target = player;
                    break;
                }
            }
        }
        
        if (target == null) {
            ChatUtils.message("§cPlayer not found: " + args[0]);
            return;
        }
        
        ChatUtils.message("§b=== " + target.getName().getString() + "'s Inventory ===");
        
        ChatUtils.message("§6Armor:");
        ItemStack helmet = target.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chestplate = target.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack leggings = target.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack boots = target.getItemBySlot(EquipmentSlot.FEET);
        
        if (!helmet.isEmpty()) {
            ChatUtils.message("  §fHelmet: §7" + formatItem(helmet));
        }
        if (!chestplate.isEmpty()) {
            ChatUtils.message("  §fChestplate: §7" + formatItem(chestplate));
        }
        if (!leggings.isEmpty()) {
            ChatUtils.message("  §fLeggings: §7" + formatItem(leggings));
        }
        if (!boots.isEmpty()) {
            ChatUtils.message("  §fBoots: §7" + formatItem(boots));
        }
        if (helmet.isEmpty() && chestplate.isEmpty() && leggings.isEmpty() && boots.isEmpty()) {
            ChatUtils.message("  §7(No armor equipped)");
        }
        
        ChatUtils.message("§6Hands:");
        ItemStack mainHand = target.getMainHandItem();
        ItemStack offHand = target.getOffhandItem();
        
        if (!mainHand.isEmpty()) {
            ChatUtils.message("  §fMain Hand: §7" + formatItem(mainHand));
        } else {
            ChatUtils.message("  §fMain Hand: §7(Empty)");
        }
        if (!offHand.isEmpty()) {
            ChatUtils.message("  §fOff Hand: §7" + formatItem(offHand));
        } else {
            ChatUtils.message("  §fOff Hand: §7(Empty)");
        }
        
        ChatUtils.message("§6Stats:");
        ChatUtils.message("  §cHealth: §f" + (int)target.getHealth() + "/" + (int)target.getMaxHealth());
        ChatUtils.message("  §9Distance: §f" + String.format("%.1f", mc.player.distanceTo(target)) + " blocks");
    }
    
    private String formatItem(ItemStack stack) {
        String name = stack.getHoverName().getString();
        int count = stack.getCount();
        
        StringBuilder info = new StringBuilder(name);
        if (count > 1) {
            info.append(" x").append(count);
        }
        
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            int durability = maxDamage - currentDamage;
            int percent = (int)((durability / (float)maxDamage) * 100);
            info.append(" §8(").append(percent).append("%)");
        }
        
        if (stack.isEnchanted()) {
            info.append(" §d[Enchanted]");
        }
        
        return info.toString();
    }
    
    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return completions;
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (Player player : mc.level.players()) {
                if (player != mc.player) {
                    String name = player.getName().getString();
                    if (name.toLowerCase().startsWith(input)) {
                        completions.add(name);
                    }
                }
            }
        }
        return completions;
    }
}
