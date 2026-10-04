package nv.navineclient.module.world;

import nv.navineclient.util.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Nuker extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Nuker range", 4.0, 1.0, 15.0);
    private final NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Blocks broken per tick", 1.0, 1.0, 10.0);
    private final NumberSetting throttle = new NumberSetting("Throttle", "Rescan interval (ticks)", 5.0, 1.0, 20.0);
    private final BooleanSetting ignoreOres = new BooleanSetting("IgnoreOres", "Skip ore blocks", false);

    private final java.util.List<BlockPos> blocksToBreak = new java.util.ArrayList<>();
    private BlockPos currentBlock;
    private int tickCounter;

    public Nuker() {
        super("Nuker", "Breaks all blocks around you", Category.WORLD);
        addSetting(range);
        addSetting(blocksPerTick);
        addSetting(throttle);
        addSetting(ignoreOres);
    }

    @Override
    public void onDisable() {
        blocksToBreak.clear();
        currentBlock = null;
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        tickCounter++;
        BlockPos playerPos = mc.player.blockPosition();
        double r = range.getValue();
        int throttleTicks = throttle.getValue().intValue();

        if (currentBlock != null) {
            if (mc.level.getBlockState(currentBlock).isAir()) {
                currentBlock = null;
            } else {
                mc.gameMode.continueDestroyBlock(currentBlock, Direction.UP);
                ClientAccess.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                return;
            }
        }

        if (tickCounter % throttleTicks == 0) {
            blocksToBreak.clear();

            for (int x = (int) -r; x <= (int) r; x++) {
                for (int y = (int) -r; y <= (int) r; y++) {
                    for (int z = (int) -r; z <= (int) r; z++) {
                        BlockPos pos = playerPos.offset(x, y, z);

                        double dist = Math.sqrt(Math.pow(mc.player.getX() - (pos.getX() + 0.5), 2)
                                + Math.pow(mc.player.getY() - (pos.getY() + 0.5), 2)
                                + Math.pow(mc.player.getZ() - (pos.getZ() + 0.5), 2));
                        if (dist > r) continue;

                        var state = mc.level.getBlockState(pos);
                        if (state.isAir() || state.getBlock().defaultDestroyTime() < 0) continue;
                        if (ignoreOres.getValue() && isOre(state)) continue;

                        blocksToBreak.add(pos);
                    }
                }
            }

            blocksToBreak.sort((a, b) -> {
                double distA = mc.player.distanceToSqr(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
                double distB = mc.player.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
                return Double.compare(distA, distB);
            });
        }

        int maxBreak = blocksPerTick.getValue().intValue();
        int started = 0;
        for (BlockPos pos : new java.util.ArrayList<>(blocksToBreak)) {
            if (started >= maxBreak) break;
            if (mc.level.getBlockState(pos).isAir()) {
                blocksToBreak.remove(pos);
                continue;
            }
            currentBlock = pos;
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            ClientAccess.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            started++;
            break;
        }
    }

    private boolean isOre(net.minecraft.world.level.block.state.BlockState state) {
        String name = state.getBlock().getDescriptionId().toLowerCase();
        return name.contains("ore") || name.contains("ancient_debris");
    }
}
