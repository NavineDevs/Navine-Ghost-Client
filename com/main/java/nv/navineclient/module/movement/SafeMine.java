package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class SafeMine extends Module {
    private final BooleanSetting preventLava = new BooleanSetting("Prevent Lava", "Prevent mining into lava", true);
    private final BooleanSetting preventWater = new BooleanSetting("Prevent Water", "Prevent mining into water", true);
    private final BooleanSetting preventFall = new BooleanSetting("Prevent Fall", "Prevent mining blocks that would cause falls", true);

    public SafeMine() {
        super("SafeMine", "Prevents mining into dangerous blocks", Category.MOVEMENT);
        addSetting(preventLava);
        addSetting(preventWater);
        addSetting(preventFall);
    }

    public boolean shouldCancelMining(BlockPos pos) {
        if (!isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.relative(dir);
            BlockState state = mc.level.getBlockState(adjacent);

            if (preventLava.getValue() && state.is(Blocks.LAVA)) {
                return true;
            }
            if (preventWater.getValue() && state.is(Blocks.WATER)) {
                return true;
            }
        }

        if (preventFall.getValue()) {
            BlockPos below = pos.below();
            if (mc.level.getBlockState(below).isAir() && pos.equals(mc.player.blockPosition().below())) {
                return true;
            }
        }

        return false;
    }
}
