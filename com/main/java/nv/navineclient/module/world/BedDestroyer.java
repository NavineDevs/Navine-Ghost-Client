package nv.navineclient.module.world;

import nv.navineclient.util.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.MiningUtil;

import java.util.ArrayList;
import java.util.List;

public class BedDestroyer extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Bed search radius", 5.0, 1.0, 20.0);
    private final NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Beds broken per tick", 1.0, 1.0, 5.0);

    private BlockPos currentBed;

    public BedDestroyer() {
        super("BedDestroyer", "Breaks beds within range", Category.WORLD);
        addSetting(range);
        addSetting(blocksPerTick);
    }

    @Override
    public void onDisable() {
        currentBed = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        int maxBreak = blocksPerTick.getValue().intValue();
        int broken = 0;

        if (currentBed != null) {
            BlockState state = mc.level.getBlockState(currentBed);
            if (!state.is(BlockTags.BEDS) || state.isAir()) {
                currentBed = null;
            } else if (MiningUtil.advanceBreak(mc, currentBed, Direction.UP)) {
                currentBed = null;
                broken++;
            } else {
                return;
            }
        }

        while (broken < maxBreak) {
            BlockPos bed = findNearestBed();
            if (bed == null) {
                break;
            }
            currentBed = bed;
            mc.gameMode.startDestroyBlock(currentBed, Direction.UP);
            ClientAccess.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (MiningUtil.advanceBreak(mc, currentBed, Direction.UP)) {
                currentBed = null;
                broken++;
            } else {
                break;
            }
        }
    }

    private BlockPos findNearestBed() {
        double searchRange = range.getValue();
        BlockPos playerPos = mc.player.blockPosition();
        int rangeInt = (int) searchRange;
        BlockPos nearestBed = null;
        double nearestDist = searchRange;
        for (int x = -rangeInt; x <= rangeInt; x++) {
            for (int y = -rangeInt; y <= rangeInt; y++) {
                for (int z = -rangeInt; z <= rangeInt; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (!state.is(BlockTags.BEDS)) {
                        continue;
                    }
                    double dist = mc.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos));
                    if (dist < nearestDist) {
                        nearestBed = pos;
                        nearestDist = dist;
                    }
                }
            }
        }
        return nearestBed;
    }
}
