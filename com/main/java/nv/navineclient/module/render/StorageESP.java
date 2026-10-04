package nv.navineclient.module.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.ColorSetting;
import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    public static StorageESP INSTANCE;
    
    private final BooleanSetting chests = new BooleanSetting("Chests", "Show chests", true);
    private final BooleanSetting enderChests = new BooleanSetting("EnderChests", "Show ender chests", true);
    private final BooleanSetting shulkers = new BooleanSetting("Shulkers", "Show shulker boxes", true);
    private final BooleanSetting barrels = new BooleanSetting("Barrels", "Show barrels", true);
    private final NumberSetting range = new NumberSetting("Range", "ESP range", 64.0, 16.0, 256.0);
    private final ColorSetting color = new ColorSetting("Color", "Storage ESP color", 0xFFFFFF00);

    private final List<BlockEntity> nearbyStorage = new ArrayList<>();
    private int scanTick = 0;

    public StorageESP() {
        super("StorageESP", "Highlights storage containers", Category.RENDER);
        INSTANCE = this;
        addSetting(chests);
        addSetting(enderChests);
        addSetting(shulkers);
        addSetting(barrels);
        addSetting(range);
        addSetting(color);
    }
    
    public int getColor() { return color.getValue(); }
    
    public boolean shouldRender(BlockEntity be) {
        if (!isEnabled()) return false;
        if (be instanceof ChestBlockEntity && chests.getValue()) return true;
        if (be instanceof EnderChestBlockEntity && enderChests.getValue()) return true;
        if (be instanceof ShulkerBoxBlockEntity && shulkers.getValue()) return true;
        if (be instanceof BarrelBlockEntity && barrels.getValue()) return true;
        return false;
    }
    
    public double getRange() { return range.getValue(); }

    public List<BlockEntity> getNearbyStorage() {
        return nearbyStorage;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        scanTick++;
        if (scanTick % 10 != 0) return;
        nearbyStorage.clear();
        BlockPos center = mc.player.blockPosition();
        int r = range.getValue().intValue();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x += 4) {
            for (int y = -r; y <= r; y += 4) {
                for (int z = -r; z <= r; z += 4) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (center.distSqr(pos) > r * r) continue;
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (be != null && shouldRender(be)) {
                        nearbyStorage.add(be);
                    }
                }
            }
        }
    }
    
    public static boolean isStorageESP() {
        Module module = ModuleManager.getModuleByName("StorageESP");
        return module != null && module.isEnabled();
    }
}
