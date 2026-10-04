package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;

public class NoSlow extends Module {
    public static NoSlow INSTANCE;
    private final BooleanSetting items = new BooleanSetting("Items", "No slow from items", true);
    private final BooleanSetting webs = new BooleanSetting("Webs", "No slow from webs", true);
    private final BooleanSetting water = new BooleanSetting("Water", "No slow from water", false);
    private final BooleanSetting honeySlime = new BooleanSetting("Honey/Slime", "No slow from honey/slime", true);

    public NoSlow() {
        super("NoSlow", "Removes slowdowns from various sources", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(items);
        addSetting(webs);
        addSetting(water);
        addSetting(honeySlime);
    }
    
    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        
        if (!isEnabled()) return;
        
        // Reset fall distance if in web/honey (prevents fall damage from slow blocks)
        if ((webs.getValue() || honeySlime.getValue()) && mc.player.onGround()) {
            net.minecraft.core.BlockPos pos = mc.player.blockPosition();
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.COBWEB) || 
                state.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK) ||
                state.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK)) {
                // Movement speed is handled in mixin
            }
        }
    }
    
    public boolean noItemSlow() { return items.getValue(); }
    public boolean noWebSlow() { return webs.getValue(); }
    public boolean noWaterSlow() { return water.getValue(); }
    public boolean noHoneySlow() { return honeySlime.getValue(); }

    public static boolean shouldNoSlow() {
        Module module = ModuleManager.getModuleByName("NoSlow");
        return module != null && module.isEnabled();
    }
    
    public static boolean shouldBypass() {
        return shouldNoSlow();
    }
}
