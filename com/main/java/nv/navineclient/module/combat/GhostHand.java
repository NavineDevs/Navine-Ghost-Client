package nv.navineclient.module.combat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class GhostHand extends Module {
    private final NumberSetting range = new NumberSetting("Range", "Reach through walls range", 5.0, 1.0, 10.0);
    private final BooleanSetting chests = new BooleanSetting("Chests", "Open chests", true);
    private final BooleanSetting shulkers = new BooleanSetting("Shulkers", "Open shulker boxes", true);
    private final NumberSetting stepSize = new NumberSetting("Step", "Ray step size", 0.5, 0.25, 1.0);

    public GhostHand() {
        super("GhostHand", "Open containers through walls", Category.COMBAT);
        addSetting(range);
        addSetting(chests);
        addSetting(shulkers);
        addSetting(stepSize);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!mc.options.keyUse.consumeClick()) return;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        double step = stepSize.getValue();

        for (double d = step; d <= range.getValue(); d += step) {
            Vec3 checkPos = eyePos.add(lookVec.scale(d));
            BlockPos blockPos = BlockPos.containing(checkPos);

            BlockEntity be = mc.level.getBlockEntity(blockPos);
            if (be == null) continue;

            boolean valid = false;
            if (chests.getValue() && (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity)) {
                valid = true;
            }
            if (shulkers.getValue() && be instanceof ShulkerBoxBlockEntity) {
                valid = true;
            }

            if (valid) {
                BlockHitResult hitResult = new BlockHitResult(checkPos, Direction.UP, blockPos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
                break;
            }
        }
    }
}
