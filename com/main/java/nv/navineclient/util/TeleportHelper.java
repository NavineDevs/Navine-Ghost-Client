package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.misc.LegitMode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportHelper {
    private static final Deque<Step> queue = new ArrayDeque<>();
    private static long nextStepAtMs;

    private TeleportHelper() {
    }

    public static void teleportTo(double x, double y, double z, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        double maxSegment = AcBypassUtil.maxTeleportSegment();
        double dx = x - mc.player.getX();
        double dy = y - mc.player.getY();
        double dz = z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= maxSegment || maxSegment <= 0) {
            sendPosition(mc, x, y, z, yaw, pitch, true);
            mc.player.setPos(x, y, z);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.fallDistance = 0;
            return;
        }

        queue.clear();
        int steps = (int) Math.ceil(dist / maxSegment);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            queue.add(new Step(
                    mc.player.getX() + dx * t,
                    mc.player.getY() + dy * t,
                    mc.player.getZ() + dz * t,
                    yaw,
                    pitch,
                    i == steps
            ));
        }
    }

    public static void tick() {
        if (queue.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextStepAtMs) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            queue.clear();
            return;
        }
        Step step = queue.poll();
        if (step == null) {
            return;
        }
        sendPosition(mc, step.x, step.y, step.z, step.yaw, step.pitch, step.onGround);
        mc.player.setPos(step.x, step.y, step.z);
        if (step.onGround) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.fallDistance = 0;
        }
        nextStepAtMs = now + (LegitMode.isActive() ? AcBypassUtil.humanDelayMs(85) : 35);
    }

    public static boolean isBusy() {
        return !queue.isEmpty();
    }

    private static void sendPosition(Minecraft mc, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        AcBypassUtil.sendRotationPacket(yaw, pitch);
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                x, y, z, yaw, pitch, onGround, mc.player.horizontalCollision));
        if (onGround) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
        }
    }

    private record Step(double x, double y, double z, float yaw, float pitch, boolean onGround) {
    }
}
