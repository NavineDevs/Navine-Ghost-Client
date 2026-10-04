package nv.navineclient.module.player;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.util.PlaceConflictGuard;

public class AirPlace extends Module {
    private final NumberSetting reachDistance = new NumberSetting("Reach", "Placement reach distance", 4.5, 3.0, 6.0);
    private final BooleanSetting holdToPlace = new BooleanSetting("Hold", "Hold right-click to place", false);
    private final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate toward placement", false);

    private boolean wasUsing;
    private int placeCooldown;

    public AirPlace() {
        super("AirPlace", "Place blocks on air without support", Category.PLAYER);
        addSetting(reachDistance);
        addSetting(holdToPlace);
        addSetting(rotate);
    }

    public boolean canPlaceAt(BlockPos pos) {
        return AcBypassUtil.canPlaceAt(mc, pos);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || ClientScreens.get(mc) != null) {
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
        if (!holdToPlace.getValue() && wasUsing) {
            return;
        }
        wasUsing = true;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem)) {
            return;
        }

        BlockPos targetPos;
        Direction face;

        BlockHitResult hit = raycastTarget();
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            BlockState targetState = mc.level.getBlockState(hitPos);
            if (!targetState.isAir() && !targetState.canBeReplaced()) {
                targetPos = hitPos.relative(hit.getDirection());
                face = hit.getDirection().getOpposite();
            } else {
                targetPos = hitPos;
                face = hit.getDirection();
            }
        } else {
            Vec3 eyes = mc.player.getEyePosition(1.0f);
            Vec3 look = mc.player.getViewVector(1.0f);
            Vec3 placePoint = eyes.add(look.scale(reachDistance.getValue()));
            targetPos = BlockPos.containing(placePoint);
            face = Direction.getNearest(
                    (int) Math.round(look.x),
                    (int) Math.round(look.y),
                    (int) Math.round(look.z),
                    Direction.UP
            ).getOpposite();
        }

        if (!canPlaceAt(targetPos)) {
            return;
        }

        BlockPos neighbor = targetPos.relative(face);
        BlockState neighborState = mc.level.getBlockState(neighbor);
        if (neighborState.isAir() || neighborState.canBeReplaced()) {
            for (Direction dir : Direction.values()) {
                BlockPos check = targetPos.relative(dir);
                BlockState checkState = mc.level.getBlockState(check);
                if (!checkState.isAir() && !checkState.canBeReplaced()) {
                    neighbor = check;
                    face = dir.getOpposite();
                    break;
                }
            }
        }

        Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );
        BlockHitResult placeHit = new BlockHitResult(hitVec, face, neighbor, false);

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
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, placeHit);
        ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
        placeCooldown = 2;
    }

    private BlockHitResult raycastTarget() {
        Vec3 eyes = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eyes.add(look.scale(reachDistance.getValue()));
        ClipContext context = new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);
        return mc.level.clip(context);
    }
}
