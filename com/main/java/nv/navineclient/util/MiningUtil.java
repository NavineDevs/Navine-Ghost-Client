package nv.navineclient.util;

import nv.navineclient.util.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MiningUtil {
    private MiningUtil() {
    }

    public static boolean isBreakable(Minecraft mc, BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        float hardness = state.getDestroySpeed(mc.level, pos);
        return hardness >= 0 && hardness < 50;
    }

    public static boolean isOre(Minecraft mc, BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("ore") || path.contains("ancient_debris");
    }

    public static BlockPos getLookedAtBlock(Minecraft mc, double maxRange) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= maxRange && isBreakable(mc, pos)) {
                return pos;
            }
        }
        return null;
    }

    public static boolean advanceBreak(Minecraft mc, BlockPos pos, Direction face) {
        if (mc.player == null || mc.gameMode == null || mc.level == null || pos == null) {
            return false;
        }
        int delay = AcBypassUtil.legitMiningDelayTicks();
        if (delay > 0 && mc.player.tickCount % (delay + 1) != 0) {
            return false;
        }
        if (mc.level.getBlockState(pos).isAir()) {
            return true;
        }
        MultiPlayerGameMode mode = mc.gameMode;
        if (mode.continueDestroyBlock(pos, face)) {
            ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
            return false;
        }
        mode.startDestroyBlock(pos, face);
        ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
        return false;
    }

    public static BlockPos findNearestBreakable(Minecraft mc, double range, boolean oresOnly) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        BlockPos playerPos = mc.player.blockPosition();
        int rangeInt = (int) Math.ceil(range);
        BlockPos nearest = null;
        double nearestDist = range;
        for (int x = -rangeInt; x <= rangeInt; x++) {
            for (int y = -rangeInt; y <= rangeInt; y++) {
                for (int z = -rangeInt; z <= rangeInt; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (!isBreakable(mc, pos)) {
                        continue;
                    }
                    if (oresOnly && !isOre(mc, pos)) {
                        continue;
                    }
                    double dist = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
                    if (dist <= range && dist < nearestDist) {
                        nearest = pos;
                        nearestDist = dist;
                    }
                }
            }
        }
        return nearest;
    }
}
