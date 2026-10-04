package nv.navineclient.module.movement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Jesus extends Module {
    public static Jesus INSTANCE;
    private final ModeSetting mode = new ModeSetting("Mode", "Walk on water mode", "Solid", "Solid", "Dolphin", "Trampoline");
    private final BooleanSetting water = new BooleanSetting("Water", "Walk on water", true);
    private final BooleanSetting lava = new BooleanSetting("Lava", "Walk on lava", true);
    private final NumberSetting dolphinBoost = new NumberSetting("Boost", "Dolphin boost speed", 0.3, 0.1, 1.0);

    public Jesus() {
        super("Jesus", "Walk on water, lava, and powdered snow", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(water);
        addSetting(lava);
        addSetting(dolphinBoost);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        String m = mode.getValue();
        boolean inWater = water.getValue() && mc.player.isInWater();

        boolean inPowderedSnow = false;
        BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(playerPos);
        if (state.is(Blocks.POWDER_SNOW)) {
            inPowderedSnow = true;
        }

        boolean inLava = lava.getValue() && (mc.player.isInLava() || state.is(Blocks.LAVA));

        if (!inWater && !inPowderedSnow && !inLava) return;

        if (m.equals("Solid")) {
            if (!mc.player.isCrouching()) {
                double playerY = mc.player.getY();
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.0, mc.player.getDeltaMovement().z);
                if (playerY % 1 > 0.9 || playerY % 1 < 0.1) {
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.1, mc.player.getDeltaMovement().z);
                }
            }
        } else if (m.equals("Dolphin")) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.05, mc.player.getDeltaMovement().z);
            if (mc.options.keyJump.isDown()) {
                double boost = dolphinBoost.getValue();
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, boost, mc.player.getDeltaMovement().z);
            }
        } else if (m.equals("Trampoline")) {
            if (mc.player.getDeltaMovement().y < 0) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.5, mc.player.getDeltaMovement().z);
            }
        }
    }

    public static boolean isJesus() {
        Module module = ModuleManager.getModuleByName("Jesus");
        return module != null && module.isEnabled();
    }
}
