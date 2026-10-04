package nv.navineclient.module.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.MiningUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class VeinMiner extends Module {
    private final NumberSetting maxBlocks = new NumberSetting("MaxBlocks", "Max blocks per vein", 64.0, 1.0, 256.0);
    private final BooleanSetting holdAttack = new BooleanSetting("HoldAttack", "Only mine while holding attack", true);
    private final BooleanSetting diagonal = new BooleanSetting("Diagonal", "Include diagonal neighbors", true);

    private final List<BlockPos> vein = new ArrayList<>();
    private BlockPos currentBlock;
    private Block currentType;

    public VeinMiner() {
        super("VeinMiner", "Mine connected ore veins while looking at ore", Category.WORLD);
        addSetting(maxBlocks);
        addSetting(holdAttack);
        addSetting(diagonal);
    }

    @Override
    public void onDisable() {
        vein.clear();
        currentBlock = null;
        currentType = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (holdAttack.getValue() && !mc.options.keyAttack.isDown()) {
            currentBlock = null;
            vein.clear();
            currentType = null;
            return;
        }

        if (currentBlock != null) {
            if (mc.level.getBlockState(currentBlock).isAir()) {
                if (!vein.isEmpty()) {
                    currentBlock = vein.remove(0);
                } else {
                    currentBlock = null;
                    currentType = null;
                }
            } else {
                MiningUtil.advanceBreak(mc, currentBlock, Direction.UP);
                return;
            }
        }

        BlockPos looked = MiningUtil.getLookedAtBlock(mc, 6.0);
        if (looked != null) {
            BlockState state = mc.level.getBlockState(looked);
            if (isOreBlock(state) && mc.options.keyAttack.isDown()) {
                startVeinMining(looked, state.getBlock());
            }
        }
    }

    private void startVeinMining(BlockPos startPos, Block blockType) {
        currentBlock = startPos;
        currentType = blockType;
        vein.clear();

        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos);

        int limit = maxBlocks.getValue().intValue();
        while (!queue.isEmpty() && vein.size() < limit) {
            BlockPos pos = queue.poll();
            for (BlockPos neighbor : getNeighbors(pos)) {
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);
                if (mc.level.getBlockState(neighbor).getBlock() == blockType) {
                    vein.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        if (diagonal.getValue()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        neighbors.add(pos.offset(dx, dy, dz));
                    }
                }
            }
            return neighbors;
        }
        neighbors.add(pos.north());
        neighbors.add(pos.south());
        neighbors.add(pos.east());
        neighbors.add(pos.west());
        neighbors.add(pos.above());
        neighbors.add(pos.below());
        return neighbors;
    }

    private boolean isOreBlock(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("ore") || path.contains("ancient_debris");
    }
}
