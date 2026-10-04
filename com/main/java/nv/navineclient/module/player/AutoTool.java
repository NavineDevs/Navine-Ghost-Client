package nv.navineclient.module.player;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;

public class AutoTool extends Module {
    private final BooleanSetting switchBack = new BooleanSetting("SwitchBack", "Switch back after mining", true);
    private final BooleanSetting onlyHotbar = new BooleanSetting("Hotbar", "Only use hotbar slots", true);
    private final BooleanSetting preferSilkTouch = new BooleanSetting("SilkTouch", "Prefer silk touch tools", false);

    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", "Automatically selects the best tool", Category.PLAYER);
        addSetting(switchBack);
        addSetting(onlyHotbar);
        addSetting(preferSilkTouch);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (!(mc.hitResult instanceof BlockHitResult hit)) {
            restoreSlot();
            return;
        }

        if (!mc.options.keyAttack.isDown()) {
            restoreSlot();
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        int best = findBestTool(state);
        if (best < 0) return;

        int current = mc.player.getInventory().getSelectedSlot();
        if (best == current) return;

        if (previousSlot < 0) {
            previousSlot = current;
        }
        mc.player.getInventory().setSelectedSlot(best);
    }

    private void restoreSlot() {
        if (!switchBack.getValue() || previousSlot < 0 || mc.player == null) return;
        if (previousSlot != mc.player.getInventory().getSelectedSlot()) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }

    private int findBestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        int start = 0;
        int end = onlyHotbar.getValue() ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (preferSilkTouch.getValue() && stack.getEnchantments().toString().toLowerCase().contains("silk_touch")) {
                speed += 0.5f;
            }
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
