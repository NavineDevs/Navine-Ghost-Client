package nv.navineclient.module.movement;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import nv.navineclient.module.Module;
import nv.navineclient.module.misc.LegitMode;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.BooleanSetting;

public class NoFall extends Module {
    public static NoFall INSTANCE;
    private final ModeSetting mode = new ModeSetting("Mode", "NoFall mode", "Hybrid", "Packet", "OnGround", "Hybrid");
    private final NumberSetting minFallDist = new NumberSetting("MinFall", "Minimum fall distance", 2.5, 1.0, 10.0);
    private final NumberSetting packetDelay = new NumberSetting("Delay", "Packet delay ticks", 1.0, 1.0, 10.0);

    private int noFallTickCounter = 0;
    private int lastPacketTick = 0;

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(minFallDist);
        addSetting(packetDelay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null || mc.level == null) return;

        noFallTickCounter++;

        if (mc.player.onGround()) {
            mc.player.fallDistance = 0;
            lastPacketTick = 0;
            return;
        }

        double y = mc.player.getY();
        double velocityY = mc.player.getDeltaMovement().y;
        boolean willHitGround = false;
        if (velocityY < 0) {
            for (int i = 0; i < 5; i++) {
                net.minecraft.core.BlockPos checkPos = new net.minecraft.core.BlockPos(
                        (int) Math.floor(mc.player.getX()),
                        (int) Math.floor(y - i),
                        (int) Math.floor(mc.player.getZ())
                );
                net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(checkPos);
                if (!state.isAir() && !state.canBeReplaced()) {
                    willHitGround = true;
                    break;
                }
            }
        }

        String m = mode.getValue();
        if (AcBypassUtil.isLegitActive() && m.equals("Packet")) {
            m = "Hybrid";
        }
        float minFall = minFallDist.getValue().floatValue();

        if (mc.player.fallDistance > minFall || (willHitGround && mc.player.fallDistance > minFall * 0.6f)) {
            if (m.equals("Packet") || m.equals("Hybrid")) {
                int delay = Math.max(2, packetDelay.getValue().intValue());
                if (AcBypassUtil.isLegitActive()) {
                    LegitMode.Profile profile = LegitMode.getProfile();
                    delay = Math.max(delay, profile == LegitMode.Profile.VULCAN ? 6
                            : profile == LegitMode.Profile.WATCHDOG ? 5
                            : profile == LegitMode.Profile.STRICT ? 5 : 3);
                }
                if (noFallTickCounter - lastPacketTick >= delay) {
                    if (AcBypassUtil.canSendMovePacket()) {
                        mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
                        AcBypassUtil.recordMovePacket();
                    }
                    lastPacketTick = noFallTickCounter;
                    if (mc.player.fallDistance > minFall) {
                        mc.player.fallDistance = Math.max(0, mc.player.fallDistance - minFall * 0.5f);
                    }
                }
            }
            if (m.equals("OnGround") || m.equals("Hybrid")) {
                double floorY = Math.floor(y);
                double distanceToFloor = y - floorY;
                if ((distanceToFloor < 0.8 && velocityY < 0.15) || (willHitGround && mc.player.fallDistance > minFall * 0.5f)) {
                    mc.player.setOnGround(true);
                    mc.player.fallDistance = 0;
                }
            }
        }
    }

    public boolean shouldCancelFallDamage() {
        if (!isEnabled()) return false;
        String m = mode.getValue();
        return m.equals("OnGround") || m.equals("Hybrid");
    }

    public static boolean shouldNoFall() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }
}
