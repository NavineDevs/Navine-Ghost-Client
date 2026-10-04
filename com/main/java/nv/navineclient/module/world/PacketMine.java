package nv.navineclient.module.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.MiningUtil;

public class PacketMine extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Mining range", 5.0, 1.0, 20.0);
    private final BooleanSetting lookOnly = new BooleanSetting("LookOnly", "Only mine the block you look at", true);
    private final BooleanSetting holdAttack = new BooleanSetting("HoldAttack", "Only mine while holding attack", true);
    private final NumberSetting breakDelay = new NumberSetting("Delay", "Break delay ticks", 0.0, 0.0, 10.0);

    private BlockPos targetBlock;
    private int delayTicks;

    public PacketMine() {
        super("PacketMine", "Fast block mining on the block you target", Category.WORLD);
        addSetting(range);
        addSetting(lookOnly);
        addSetting(holdAttack);
        addSetting(breakDelay);
    }

    @Override
    public void onDisable() {
        targetBlock = null;
        delayTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (holdAttack.getValue() && !mc.options.keyAttack.isDown()) {
            targetBlock = null;
            return;
        }

        double r = range.getValue();
        if (lookOnly.getValue()) {
            targetBlock = MiningUtil.getLookedAtBlock(mc, r);
        } else if (targetBlock == null || mc.level.getBlockState(targetBlock).isAir()) {
            targetBlock = MiningUtil.findNearestBreakable(mc, r, false);
        }

        if (targetBlock == null) {
            return;
        }
        if (delayTicks < breakDelay.getValue().intValue()) {
            delayTicks++;
            return;
        }
        delayTicks = 0;
        if (MiningUtil.advanceBreak(mc, targetBlock, Direction.UP)) {
            targetBlock = null;
        }
    }
}
