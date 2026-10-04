package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;

public class Criticals extends Module {
    public static Criticals INSTANCE;
    private final ModeSetting mode = new ModeSetting("Mode", "Criticals mode", "Packet", "Packet", "Jump", "MiniJump");
    private final BooleanSetting requireGround = new BooleanSetting("Ground", "Require on ground", true);
    private final NumberSetting packetOffset = new NumberSetting("Offset", "Packet Y offset", 0.0625, 0.01, 0.2);
    private final BooleanSetting tickAssist = new BooleanSetting("TickAssist", "Crit assist when mixins missing", false);
    private boolean attackHeld;

    public Criticals() {
        super("Criticals", "Always deal critical hits", Category.COMBAT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(requireGround);
        addSetting(packetOffset);
        addSetting(tickAssist);
    }

    public void doCritical() {
        refreshClient();
        if (mc.player == null || mc.getConnection() == null) return;
        if (requireGround.getValue() && !mc.player.onGround()) return;

        String m = mode.getValue();
        if (m.equals("Packet") && !AcBypassUtil.allowPacketCriticals()) {
            m = "MiniJump";
        }
        if (m.equals("Packet")) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            double offset = packetOffset.getValue();
            AcBypassUtil.sendRotationPacket(mc.player.getYRot(), mc.player.getXRot());
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    x, y + offset, z, mc.player.getYRot(), mc.player.getXRot(), false, true));
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    x, y, z, mc.player.getYRot(), mc.player.getXRot(), false, true));
        } else if (m.equals("Jump")) {
            mc.player.jumpFromGround();
        } else if (m.equals("MiniJump")) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, packetOffset.getValue(), mc.player.getDeltaMovement().z);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || !tickAssist.getValue()) {
            attackHeld = false;
            return;
        }
        refreshClient();
        if (mc == null || mc.player == null || mc.options == null || ClientScreens.get(mc) != null) {
            attackHeld = false;
            return;
        }
        boolean attack = mc.options.keyAttack.isDown();
        if (!attack) {
            attackHeld = false;
            return;
        }
        if (attackHeld) {
            return;
        }
        if (mc.player.getAttackStrengthScale(0.0f) < 0.92f) {
            return;
        }
        attackHeld = true;
        doCritical();
        try {
            ClientAccess.swing(mc.player, InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldCrit() {
        Module module = ModuleManager.getModuleByName("Criticals");
        return module != null && module.isEnabled();
    }
}
