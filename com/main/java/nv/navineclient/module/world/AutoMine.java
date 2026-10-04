package nv.navineclient.module.world;

import nv.navineclient.util.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.MiningUtil;

public class AutoMine extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Mining range", 4.0, 1.0, 15.0);
    private final BooleanSetting oresOnly = new BooleanSetting("OresOnly", "Only mine ores", false);
    private final BooleanSetting pauseWhenFull = new BooleanSetting("PauseFull", "Pause when inventory full", true);

    private BlockPos currentBlock;

    public AutoMine() {
        super("AutoMine", "Automatically mines nearby blocks", Category.WORLD);
        addSetting(range);
        addSetting(oresOnly);
        addSetting(pauseWhenFull);
    }

    @Override
    public void onDisable() {
        currentBlock = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (pauseWhenFull.getValue() && mc.player.getInventory().getFreeSlot() == -1) {
            return;
        }

        if (currentBlock != null) {
            if (MiningUtil.advanceBreak(mc, currentBlock, Direction.UP)) {
                currentBlock = null;
            }
            return;
        }

        currentBlock = MiningUtil.findNearestBreakable(mc, range.getValue(), oresOnly.getValue());
        if (currentBlock != null) {
            mc.gameMode.startDestroyBlock(currentBlock, Direction.UP);
            ClientAccess.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
}
