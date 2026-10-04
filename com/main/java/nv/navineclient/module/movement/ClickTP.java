package nv.navineclient.module.movement;

import nv.navineclient.util.ClientScreens;
import nv.navineclient.util.AcBypassUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.ChatUtils;
import nv.navineclient.util.TeleportHelper;
import nv.navineclient.util.NavineInput;

public class ClickTP extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Input mode", "ClickThenShift", "ClickThenShift", "ShiftThenClick");
    private final BooleanSetting onLeftClick = new BooleanSetting("LeftClick", "Teleport on left click", true);
    private final BooleanSetting onRightClick = new BooleanSetting("RightClick", "Teleport on right click", false);
    private final NumberSetting reach = new NumberSetting("Reach", "Raycast reach", 200.0, 10.0, 500.0);

    private boolean leftWasDown;
    private boolean rightWasDown;
    private boolean shiftWasDown;
    private BlockHitResult storedTarget;
    private long storedTargetTime;

    public ClickTP() {
        super("ClickTP", "Click a block then press Shift to teleport", Category.MOVEMENT);
        addSetting(mode);
        addSetting(onLeftClick);
        addSetting(onRightClick);
        addSetting(reach);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || ClientScreens.get(mc) != null) {
            return;
        }
        if (mc.getWindow() == null) {
            return;
        }

        long window = mc.getWindow().handle();
        boolean shiftDown = isShiftDown(window);
        boolean leftDown = NavineInput.isMouseDown(NavineInput.MOUSE_BUTTON_LEFT);
        boolean rightDown = NavineInput.isMouseDown(NavineInput.MOUSE_BUTTON_RIGHT);

        if (mode.getValue().equals("ClickThenShift")) {
            handleClickThenShift(leftDown, rightDown, shiftDown);
        } else {
            handleShiftThenClick(leftDown, rightDown, shiftDown);
        }

        if (storedTarget != null && System.currentTimeMillis() - storedTargetTime > 3000) {
            storedTarget = null;
        }

        leftWasDown = leftDown;
        rightWasDown = rightDown;
        shiftWasDown = shiftDown;
    }

    private void handleClickThenShift(boolean leftDown, boolean rightDown, boolean shiftDown) {
        if (leftDown && !leftWasDown && onLeftClick.getValue()) {
            storeTarget();
        }
        if (rightDown && !rightWasDown && onRightClick.getValue()) {
            storeTarget();
        }
        if (shiftDown && !shiftWasDown && storedTarget != null) {
            teleportToStored();
        }
    }

    private void handleShiftThenClick(boolean leftDown, boolean rightDown, boolean shiftDown) {
        if (!shiftDown) {
            return;
        }
        if (leftDown && !leftWasDown && onLeftClick.getValue()) {
            teleportToClick();
        }
        if (rightDown && !rightWasDown && onRightClick.getValue()) {
            teleportToClick();
        }
    }

    private void storeTarget() {
        BlockHitResult hitResult = raycastBlock();
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        storedTarget = hitResult;
        storedTargetTime = System.currentTimeMillis();
        ChatUtils.message("§7[ClickTP] Target set — press Shift");
    }

    private void teleportToStored() {
        if (storedTarget == null || storedTarget.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (System.currentTimeMillis() - storedTargetTime > 3000) {
            storedTarget = null;
            return;
        }
        applyTeleport(storedTarget);
        storedTarget = null;
    }

    public void teleportToClick() {
        BlockHitResult hitResult = raycastBlock();
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        applyTeleport(hitResult);
    }

    private void applyTeleport(BlockHitResult hitResult) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        double x = hitResult.getBlockPos().getX() + 0.5;
        double y = hitResult.getBlockPos().getY() + 1.0;
        double z = hitResult.getBlockPos().getZ() + 0.5;
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        TeleportHelper.teleportTo(x, y, z, yaw, pitch);
    }

    private BlockHitResult raycastBlock() {
        Vec3 eyes = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eyes.add(look.scale(AcBypassUtil.clampBlockReach(reach.getValue())));
        ClipContext context = new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);
        return mc.level.clip(context);
    }

    private static boolean isShiftDown(long window) {
        return NavineInput.isKeyDown(NavineInput.KEY_LEFT_SHIFT)
                || NavineInput.isKeyDown(NavineInput.KEY_RIGHT_SHIFT);
    }

    public boolean shouldTeleportOnLeftClick() {
        return isEnabled() && onLeftClick.getValue();
    }

    public boolean shouldTeleportOnRightClick() {
        return isEnabled() && onRightClick.getValue();
    }
}
