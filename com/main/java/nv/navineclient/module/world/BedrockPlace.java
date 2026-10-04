package nv.navineclient.module.world;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.PlaceConflictGuard;

public class BedrockPlace extends Module {
    private final BooleanSetting swing = new BooleanSetting("Swing", "Swing arm when placing", true);
    private final BooleanSetting rotate = new BooleanSetting("Rotate", "Face the placement spot", false);
    private final BooleanSetting autoSlot = new BooleanSetting("AutoSlot", "Use any hotbar block slot", true);
    private final BooleanSetting hold = new BooleanSetting("Hold", "Hold right-click to keep placing", true);
    private final NumberSetting delay = new NumberSetting("Delay", "Ticks between placements", 2.0, 0.0, 10.0);

    private boolean wasUsing;
    private int placeCooldown;

    public BedrockPlace() {
        super("BedrockPlace", "Place blocks 1 block in front like Bedrock Minecraft (right-click)", Category.WORLD);
        addSetting(swing);
        addSetting(rotate);
        addSetting(autoSlot);
        addSetting(hold);
        addSetting(delay);
    }

    public boolean shouldBlockVanillaUse() {
        if (!isEnabled() || mc.player == null || mc.options == null) {
            return false;
        }
        if (!mc.options.keyUse.isDown()) {
            return false;
        }
        return findBlockSlot() != -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || mc.level == null || ClientScreens.get(mc) != null) {
            return;
        }
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        boolean using = mc.options != null && mc.options.keyUse.isDown();
        if (!using) {
            wasUsing = false;
            return;
        }
        if (!hold.getValue() && wasUsing) {
            return;
        }
        wasUsing = true;

        int slot = findBlockSlot();
        if (slot == -1) {
            return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
        }

        BlockPos targetPos = resolveTargetPos();
        if (targetPos == null) {
            restoreSlot(oldSlot);
            return;
        }

        BlockHitResult hitResult = buildPlacementHit(targetPos);
        if (hitResult == null) {
            restoreSlot(oldSlot);
            return;
        }

        Vec3 hitVec = hitResult.getLocation();
        if (rotate.getValue()) {
            float yaw = (float) Math.toDegrees(Math.atan2(-(hitVec.x - mc.player.getX()), hitVec.z - mc.player.getZ()));
            float pitch = (float) -Math.toDegrees(Math.atan2(
                    hitVec.y - mc.player.getEyeY(),
                    Math.sqrt(Math.pow(hitVec.x - mc.player.getX(), 2) + Math.pow(hitVec.z - mc.player.getZ(), 2))
            ));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }

        PlaceConflictGuard.beginModPlacement();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        if (swing.getValue()) {
            ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
        }
        placeCooldown = delay.getValue().intValue();
        restoreSlot(oldSlot);
    }

    private void restoreSlot(int oldSlot) {
        if (autoSlot.getValue() && mc.player.getInventory().getSelectedSlot() != oldSlot) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
    }

    private BlockPos resolveTargetPos() {
        Direction facing = Direction.fromYRot(mc.player.getYRot());
        BlockPos feet = mc.player.blockPosition();
        BlockPos groundAhead = feet.below().relative(facing, 1);
        if (canPlaceAt(groundAhead)) {
            return groundAhead;
        }
        BlockPos feetAhead = feet.relative(facing, 1);
        if (canPlaceAt(feetAhead)) {
            return feetAhead;
        }
        return null;
    }

    private boolean canPlaceAt(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private BlockHitResult buildPlacementHit(BlockPos targetPos) {
        Direction facing = Direction.fromYRot(mc.player.getYRot());

        BlockPos below = targetPos.below();
        if (isSolid(below)) {
            return faceHit(below, Direction.UP);
        }

        BlockPos behind = targetPos.relative(facing.getOpposite());
        if (isSolid(behind)) {
            return faceHit(behind, facing);
        }

        BlockPos feet = mc.player.blockPosition();
        if (isSolid(feet.below())) {
            Direction towardTarget = Direction.fromYRot(
                    (float) Math.toDegrees(Math.atan2(
                            targetPos.getX() - feet.getX(),
                            targetPos.getZ() - feet.getZ()
                    ))
            );
            if (towardTarget.getAxis().isHorizontal()) {
                BlockPos side = feet.below().relative(towardTarget);
                if (isSolid(side)) {
                    return faceHit(side, towardTarget.getOpposite());
                }
            }
        }

        for (Direction dir : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos neighbor = targetPos.relative(dir);
            if (isSolid(neighbor)) {
                return faceHit(neighbor, dir.getOpposite());
            }
        }

        return null;
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    private BlockHitResult faceHit(BlockPos neighbor, Direction face) {
        Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );
        return new BlockHitResult(hitVec, face, neighbor, false);
    }

    private int findBlockSlot() {
        int selected = mc.player.getInventory().getSelectedSlot();
        ItemStack selectedStack = mc.player.getInventory().getItem(selected);
        if (selectedStack.getItem() instanceof BlockItem) {
            return selected;
        }
        if (!autoSlot.getValue()) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }
}
