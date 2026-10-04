package nv.navineclient.util;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.misc.LegitMode;

public final class AcBypassUtil {
    private static long lastMovePacketMs;
    private static int movePacketsThisSecond;
    private static long movePacketWindowStart;

    private AcBypassUtil() {
    }

    public static boolean isLegitActive() {
        return LegitMode.isActive();
    }

    public static double jitter(double base, double percent) {
        double factor = 1.0 + (percent * (Math.sin(System.nanoTime() * 0.0000001) * 0.5 + Math.cos(System.nanoTime() * 0.00000007) * 0.5));
        return base * factor;
    }

    public static int humanDelayMs(int base) {
        int spread = isLegitActive() ? 35 : 15;
        return Math.max(0, base + ThreadLocalRandom.current().nextInt(-spread, spread + 1));
    }

    public static float[] smoothRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float maxDelta) {
        while (currentYaw > targetYaw + 180) {
            currentYaw -= 360;
        }
        while (currentYaw < targetYaw - 180) {
            currentYaw += 360;
        }
        float yawDiff = targetYaw - currentYaw;
        float pitchDiff = targetPitch - currentPitch;
        float factor = rotationBlendFactor();
        float newYaw = currentYaw + Math.max(-maxDelta, Math.min(maxDelta, yawDiff * factor));
        float newPitch = currentPitch + Math.max(-maxDelta, Math.min(maxDelta, pitchDiff * factor));
        newPitch = Math.max(-90, Math.min(90, newPitch));
        newYaw = snapYawToMouseGcd(currentYaw, newYaw);
        newPitch = snapPitchToMouseGcd(currentPitch, newPitch);
        return new float[]{newYaw, newPitch};
    }

    private static float rotationBlendFactor() {
        LegitMode.Profile profile = LegitMode.getProfile();
        if (profile == null) {
            return 0.35f;
        }
        return switch (profile) {
            case VULCAN, WATCHDOG, STRICT -> 0.18f;
            default -> 0.28f;
        };
    }

    public static float snapYawToMouseGcd(float current, float target) {
        float delta = target - current;
        float step = snapAngleToMouseGcd(delta);
        return current + step;
    }

    public static float snapPitchToMouseGcd(float current, float target) {
        float delta = target - current;
        float step = snapAngleToMouseGcd(delta);
        float value = current + step;
        return Math.max(-90.0f, Math.min(90.0f, value));
    }

    private static float snapAngleToMouseGcd(float angle) {
        double gcd = mouseGcd();
        if (gcd < 1.0E-4) {
            return angle;
        }
        return (float) (Math.round(angle / gcd) * gcd);
    }

    private static double mouseGcd() {
        Minecraft mc = Minecraft.getInstance();
        double sens = 0.5;
        if (mc != null && mc.options != null) {
            sens = mc.options.sensitivity().get();
        }
        double f = sens * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    public static void applySmoothLook(LocalPlayer player, float targetYaw, float maxDelta) {
        float[] rotated = smoothRotation(player.getYRot(), player.getXRot(), targetYaw, 0, maxDelta);
        player.setYRot(rotated[0]);
    }

    public static void sendRotationPacket(float yaw, float pitch) {
        if (!canSendMovePacket()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        recordMovePacket();
    }

    public static boolean canSendMovePacket() {
        if (!isLegitActive()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - movePacketWindowStart >= 1000L) {
            movePacketWindowStart = now;
            movePacketsThisSecond = 0;
        }
        int limit = movePacketLimit();
        if (movePacketsThisSecond >= limit) {
            return false;
        }
        if (now - lastMovePacketMs < movePacketSpacingMs()) {
            return false;
        }
        return true;
    }

    private static int movePacketLimit() {
        LegitMode.Profile profile = LegitMode.getProfile();
        if (profile == null) {
            return 19;
        }
        return switch (profile) {
            case VULCAN -> 14;
            case WATCHDOG -> 15;
            case STRICT -> 16;
            default -> 19;
        };
    }

    private static long movePacketSpacingMs() {
        LegitMode.Profile profile = LegitMode.getProfile();
        if (profile == null) {
            return 45L;
        }
        return switch (profile) {
            case VULCAN -> 55L;
            case WATCHDOG, STRICT -> 50L;
            default -> 45L;
        };
    }

    public static void recordMovePacket() {
        lastMovePacketMs = System.currentTimeMillis();
        movePacketsThisSecond++;
    }

    public static boolean canReachEntity(Entity entity, double maxReach) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entity == null) {
            return false;
        }
        double effectiveReach = clampAttackReach(maxReach);
        if (mc.player.distanceTo(entity) > effectiveReach) {
            return false;
        }
        Vec3 eyes = mc.player.getEyePosition(1.0f);
        Vec3 target = entity.getBoundingBox().getCenter();
        ClipContext context = new ClipContext(eyes, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        HitResult hit = mc.level.clip(context);
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eyes) >= target.distanceToSqr(eyes) - 0.25;
    }

    public static boolean isInFov(Entity entity, float fovDegrees) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == null) {
            return false;
        }
        Vec3 look = mc.player.getViewVector(1.0f).normalize();
        Vec3 toTarget = entity.getBoundingBox().getCenter().subtract(mc.player.getEyePosition(1.0f)).normalize();
        double dot = look.dot(toTarget);
        double threshold = Math.cos(Math.toRadians(fovDegrees * 0.5));
        return dot >= threshold;
    }

    public static boolean canPlaceAt(Minecraft mc, net.minecraft.core.BlockPos pos) {
        if (mc.level == null || mc.player == null || pos == null) {
            return false;
        }
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }
        AABB box = new AABB(pos);
        return !box.intersects(mc.player.getBoundingBox());
    }

    public static double clampAttackReach(double requested) {
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
        if (creative) {
            return Math.min(requested, 5.0);
        }
        if (!isLegitActive()) {
            double cap = nv.navineclient.NavineClient.GHOST ? 10.0 : 6.0;
            return Math.min(requested, cap);
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        if (profile == LegitMode.Profile.VULCAN) {
            return Math.min(requested, 3.0);
        }
        if (profile == LegitMode.Profile.WATCHDOG) {
            return Math.min(requested, 3.05);
        }
        if (profile == LegitMode.Profile.STRICT) {
            return Math.min(requested, 3.0);
        }
        return Math.min(requested, 3.12);
    }

    public static double clampBlockReach(double requested) {
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
        double vanilla = creative ? 5.0 : 4.5;
        if (!isLegitActive()) {
            double cap = nv.navineclient.NavineClient.GHOST ? 10.0 : 6.0;
            return Math.min(requested, cap);
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        if (profile == LegitMode.Profile.VULCAN || profile == LegitMode.Profile.STRICT) {
            return vanilla;
        }
        if (profile == LegitMode.Profile.WATCHDOG) {
            return Math.min(requested, vanilla + 0.05);
        }
        return Math.min(requested, vanilla + 0.1);
    }

    @Deprecated
    public static double clampReach(double requested, boolean creative) {
        return clampAttackReach(requested);
    }

    public static double clampSpeedMultiplier(double multiplier) {
        if (!isLegitActive()) {
            return multiplier;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return switch (profile) {
            case VULCAN -> Math.min(multiplier, 1.04);
            case WATCHDOG -> Math.min(multiplier, 1.05);
            case STRICT -> Math.min(multiplier, 1.06);
            default -> Math.min(multiplier, 1.14);
        };
    }

    public static float clampTimer(float timer) {
        if (!isLegitActive()) {
            return timer;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return switch (profile) {
            case VULCAN -> Math.min(timer, 1.02f);
            case WATCHDOG -> Math.min(timer, 1.03f);
            case STRICT -> Math.min(timer, 1.03f);
            default -> Math.min(timer, 1.08f);
        };
    }

    public static float clampStepHeight(float height) {
        if (!isLegitActive()) {
            return height;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return Math.min(height, profile == LegitMode.Profile.STRICT ? 0.6f : 1.0f);
    }

    public static double clampCps(double cps) {
        if (!isLegitActive()) {
            return cps;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return switch (profile) {
            case VULCAN -> Math.min(cps, 9.0);
            case WATCHDOG -> Math.min(cps, 10.0);
            case STRICT -> Math.min(cps, 10.0);
            default -> Math.min(cps, 13.0);
        };
    }

    public static double minVelocityPercent(double percent) {
        if (!isLegitActive()) {
            return percent;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        double floor = switch (profile) {
            case VULCAN -> 96.0;
            case WATCHDOG -> 88.0;
            case STRICT -> 92.0;
            default -> 82.0;
        };
        return Math.max(percent, floor);
    }

    public static double maxHorizontalBps() {
        if (!isLegitActive()) {
            return 40.0;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return switch (profile) {
            case VULCAN -> 7.8;
            case WATCHDOG -> 8.0;
            case STRICT -> 8.2;
            default -> 9.8;
        };
    }

    public static double maxTeleportSegment() {
        if (!isLegitActive()) {
            return 12.0;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return profile == LegitMode.Profile.STRICT ? 2.8 : 5.5;
    }

    public static Vec3 clampHorizontalSpeed(Vec3 motion, double maxBps) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal <= maxBps || horizontal <= 0.0001) {
            return motion;
        }
        double scale = maxBps / horizontal;
        return new Vec3(motion.x * scale, motion.y, motion.z * scale);
    }

    public static Vec3 blendHorizontalMotion(Vec3 current, Vec3 target, double blend) {
        return new Vec3(
                current.x + (target.x - current.x) * blend,
                current.y,
                current.z + (target.z - current.z) * blend
        );
    }

    public static int legitMiningDelayTicks() {
        if (!isLegitActive()) {
            return 0;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return profile == LegitMode.Profile.STRICT ? 2 : 1;
    }

    public static boolean allowVanillaFlight() {
        return !isLegitActive();
    }

    public static boolean allowPacketCriticals() {
        if (!isLegitActive()) {
            return true;
        }
        LegitMode.Profile profile = LegitMode.getProfile();
        return profile != LegitMode.Profile.STRICT && profile != LegitMode.Profile.VULCAN;
    }

    public static int legitBlinkReleaseRate(int requested) {
        if (!isLegitActive()) {
            return requested;
        }
        return Math.min(requested, LegitMode.getProfile() == LegitMode.Profile.STRICT ? 1 : 2);
    }

    public static int legitBlinkMaxPackets(int requested) {
        if (!isLegitActive()) {
            return requested;
        }
        return Math.min(requested, LegitMode.getProfile() == LegitMode.Profile.STRICT ? 40 : 80);
    }
}
