package nv.navineclient.module.world;

import nv.navineclient.util.ClientAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;

public class Scaffold extends Module {
    private final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate when placing blocks", true);
    private final BooleanSetting noLook = new BooleanSetting("NoLook", "Do not rotate view when placing", false);
    private final BooleanSetting tower = new BooleanSetting("Tower", "Build up when jumping", true);
    private final BooleanSetting extender = new BooleanSetting("Extender", "Place blocks forward too", true);
    private final NumberSetting extenderRange = new NumberSetting("Range", "Extender distance", 1.0, 1.0, 5.0);

    private final BooleanSetting smoothRotate = new BooleanSetting("SmoothRot", "Smooth rotation when placing", true);

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under you with extender", Category.WORLD);
        addSetting(rotate);
        addSetting(noLook);
        addSetting(smoothRotate);
        addSetting(tower);
        addSetting(extender);
        addSetting(extenderRange);
    }

    private int placeCooldown = 0;
    
    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        
        BlockPos below = mc.player.blockPosition().below();
        BlockState belowState = mc.level.getBlockState(below);
        
        // Only place if block below is air
        if (!belowState.isAir() && !belowState.canBeReplaced()) return;
        
        int slot = findBlockSlot();
        if (slot == -1) return;
        
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        
        // Priority: DOWN (block below), then horizontal neighbors
        Direction[] directions = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        
        boolean placed = false;
        for (Direction dir : directions) {
            BlockPos neighbor = below.relative(dir);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            
            if (!neighborState.isAir() && !neighborState.canBeReplaced()) {
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                    dir.getOpposite().getStepX() * 0.5,
                    dir.getOpposite().getStepY() * 0.5,
                    dir.getOpposite().getStepZ() * 0.5
                );
                BlockHitResult hitResult = new BlockHitResult(hitVec, dir.getOpposite(), neighbor, false);
                
                if (rotate.getValue() && !noLook.getValue()) {
                    float targetYaw = getRotationYaw(hitVec);
                    float targetPitch = dir == Direction.DOWN ? 90 : 80;
                    if (smoothRotate.getValue()) {
                        float[] rotated = AcBypassUtil.smoothRotation(
                                mc.player.getYRot(), mc.player.getXRot(), targetYaw, targetPitch, 25f);
                        mc.player.setYRot(rotated[0]);
                        mc.player.setXRot(rotated[1]);
                    } else {
                        mc.player.setYRot(targetYaw);
                        mc.player.setXRot(targetPitch);
                    }
                }
                
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
                ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
                placeCooldown = 2 + (int) (Math.random() * 3);
                placed = true;
                break;
            }
        }
        
        mc.player.getInventory().setSelectedSlot(oldSlot);
        
        // Tower mode - jump when placing blocks below
        if (tower.getValue() && placed && mc.options.keyJump.isDown() && mc.player.onGround()) {
            mc.player.jumpFromGround();
        }

        // Extender - place blocks forward
        if (extender.getValue() && !placed) {
            float yaw = mc.player.getYRot();
            int range = extenderRange.getValue().intValue();

            for (int dist = 1; dist <= range; dist++) {
                // Calculate forward direction
                double dx = -Math.sin(Math.toRadians(yaw)) * dist;
                double dz = Math.cos(Math.toRadians(yaw)) * dist;
                
                BlockPos extendPos = mc.player.blockPosition().offset(
                    (int) Math.round(dx),
                    -1,
                    (int) Math.round(dz)
                );

                BlockState extendState = mc.level.getBlockState(extendPos);
                if (extendState.isAir() || extendState.canBeReplaced()) {
                    // Find adjacent block to place against
                    for (Direction dir : directions) {
                        BlockPos neighbor = extendPos.relative(dir);
                        BlockState neighborState = mc.level.getBlockState(neighbor);
                        
                        if (!neighborState.isAir() && !neighborState.canBeReplaced()) {
                            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                                dir.getOpposite().getStepX() * 0.5,
                                dir.getOpposite().getStepY() * 0.5,
                                dir.getOpposite().getStepZ() * 0.5
                            );
                            BlockHitResult hitResult = new BlockHitResult(hitVec, dir.getOpposite(), neighbor, false);

                            int extSlot = findBlockSlot();
                            if (extSlot != -1) {
                                int extOldSlot = mc.player.getInventory().getSelectedSlot();
                                mc.player.getInventory().setSelectedSlot(extSlot);
                                
                                if (rotate.getValue() && !noLook.getValue()) {
                                    float extYaw = getRotationYaw(hitVec);
                                    mc.player.setYRot(extYaw);
                                    mc.player.setXRot(dir == Direction.DOWN ? 90 : 80);
                                }
                                
                                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
                                ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
                                mc.player.getInventory().setSelectedSlot(extOldSlot);
                                placeCooldown = 2 + (int) (Math.random() * 3);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block != Blocks.SAND && block != Blocks.GRAVEL && block != Blocks.ANVIL) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private float getRotationYaw(Vec3 target) {
        double dx = target.x - mc.player.getX();
        double dz = target.z - mc.player.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    }
}
