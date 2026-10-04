package nv.navineclient.commands;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.XRay;
import nv.navineclient.util.ChatUtils;
import java.util.HashSet;
import java.util.Set;

public class XRayCommand extends Command {
    public XRayCommand() {
        super("xray", "Manage XRay block list", ".xray <add|remove|list> [block]", "xr");
    }
    
    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            showUsage();
            return;
        }
        
        XRay xray = (XRay) ModuleManager.getModuleByName("XRay");
        if (xray == null) {
            ChatUtils.message("§cXRay module not found!");
            return;
        }
        
        String action = args[0].toLowerCase();
        
        if (action.equals("list")) {
            Set<Block> visible = xray.getVisibleBlocks();
            if (visible.isEmpty()) {
                ChatUtils.message("§7No blocks in XRay list.");
            } else {
                ChatUtils.message("§bXRay blocks (" + visible.size() + "):");
                for (Block block : visible) {
                    Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                    ChatUtils.message("§7- §f" + formatId(id));
                }
            }
            return;
        }
        
        if (args.length < 2) {
            showUsage();
            return;
        }
        
        String blockName = args[1];
        Identifier id = Identifier.tryParse(blockName);
        if (id == null) {
            id = Identifier.tryParse("minecraft:" + blockName);
        }
        
        if (id == null) {
            ChatUtils.message("§cInvalid block: " + blockName);
            return;
        }
        
        Block block = BuiltInRegistries.BLOCK.get(id).map(net.minecraft.core.Holder::value).orElse(null);
        if (block == null || block == Blocks.AIR) {
            ChatUtils.message("§cBlock not found: " + blockName);
            return;
        }
        
        if (action.equals("add")) {
            xray.addBlock(block);
            ChatUtils.message("§aAdded §f" + formatId(id) + "§a to XRay");
        } else if (action.equals("remove")) {
            xray.removeBlock(block);
            ChatUtils.message("§aRemoved §f" + formatId(id) + "§a from XRay");
        } else {
            showUsage();
        }
    }

    private static String formatId(Identifier id) {
        if (id == null) {
            return "unknown";
        }
        if ("minecraft".equals(id.getNamespace())) {
            return id.getPath();
        }
        return id.toString();
    }
}
