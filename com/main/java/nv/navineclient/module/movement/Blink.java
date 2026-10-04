package nv.navineclient.module.movement;

import net.minecraft.client.CameraType;
import nv.navineclient.module.Module;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Blink extends Module {
    private final NumberSetting maxPackets = new NumberSetting("MaxPackets", "Maximum packets to store", 100.0, 10.0, 500.0);
    private final NumberSetting autoRelease = new NumberSetting("AutoRelease", "Auto release after ticks", 0.0, 0.0, 200.0);
    private final NumberSetting releaseRate = new NumberSetting("ReleaseRate", "Packets released per tick", 3.0, 1.0, 20.0);
    private final BooleanSetting warnThreshold = new BooleanSetting("Warn", "Warn near packet limit", true);
    private final BooleanSetting showSelf = new BooleanSetting("ShowSelf", "Show your player model while blinking", true);

    private static final java.util.List<net.minecraft.network.protocol.game.ServerboundMovePlayerPacket> storedPackets = new java.util.ArrayList<>();
    private static boolean isBlinking = false;
    private int blinkTicks = 0;
    private boolean warned = false;
    private boolean releasing = false;
    private int releaseIndex = 0;
    private CameraType savedCameraType;

    public Blink() {
        super("Blink", "Freezes your position on the server", Category.MOVEMENT);
        addSetting(maxPackets);
        addSetting(autoRelease);
        addSetting(releaseRate);
        addSetting(warnThreshold);
        addSetting(showSelf);
    }

    public boolean shouldShowSelf() {
        return showSelf.getValue();
    }

    @Override
    public void onEnable() {
        storedPackets.clear();
        isBlinking = true;
        blinkTicks = 0;
        warned = false;
        releasing = false;
        releaseIndex = 0;
        if (mc.options != null && showSelf.getValue() && mc.options.getCameraType().isFirstPerson()) {
            savedCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            savedCameraType = null;
        }
        nv.navineclient.util.ChatUtils.message("Blink enabled - position frozen on server");
    }

    @Override
    public void onDisable() {
        int count = storedPackets.size();
        isBlinking = false;
        if (mc.options != null && savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
        if (count > 0) {
            releasing = true;
            releaseIndex = 0;
            nv.navineclient.util.ChatUtils.message("Blink disabled - releasing " + count + " packets");
        } else {
            releasing = false;
            nv.navineclient.util.ChatUtils.message("Blink disabled");
        }
    }

    @Override
    public void onTick() {
        if (releasing) {
            releaseBatch();
            return;
        }
        if (!isBlinking) {
            return;
        }

        blinkTicks++;
        int max = AcBypassUtil.legitBlinkMaxPackets(maxPackets.getValue().intValue());
        if (warnThreshold.getValue() && !warned && storedPackets.size() >= max * 0.9) {
            nv.navineclient.util.ChatUtils.message("Blink near packet limit: " + storedPackets.size() + "/" + max);
            warned = true;
        }

        int releaseAfter = autoRelease.getValue().intValue();
        if (releaseAfter > 0 && blinkTicks >= releaseAfter) {
            setEnabled(false);
        }
    }

    public static boolean shouldCancelPacket() {
        return isBlinking && !isReleasing();
    }

    private static boolean isReleasing() {
        Blink blink = (Blink) nv.navineclient.module.ModuleManager.getModuleByName("Blink");
        return blink != null && blink.releasing;
    }

    public static boolean isActive() {
        return isBlinking || isReleasing();
    }

    public static boolean shouldRenderLocalPlayer() {
        Blink blink = (Blink) nv.navineclient.module.ModuleManager.getModuleByName("Blink");
        return blink != null && blink.isEnabled() && blink.shouldShowSelf();
    }

    public static void storePacket(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet) {
        if (!isBlinking) {
            return;
        }

        Blink blink = (Blink) nv.navineclient.module.ModuleManager.getModuleByName("Blink");
        if (blink == null || !blink.isEnabled()) {
            return;
        }

        int max = AcBypassUtil.legitBlinkMaxPackets(blink.maxPackets.getValue().intValue());
        if (storedPackets.size() >= max) {
            storedPackets.remove(0);
        }
        storedPackets.add(packet);
    }

    private void releaseBatch() {
        if (mc.getConnection() == null) {
            storedPackets.clear();
            releasing = false;
            return;
        }

        int rate = AcBypassUtil.legitBlinkReleaseRate(releaseRate.getValue().intValue());
        int sent = 0;
        while (releaseIndex < storedPackets.size() && sent < rate) {
            try {
                mc.getConnection().send(storedPackets.get(releaseIndex));
            } catch (Exception ignored) {
            }
            releaseIndex++;
            sent++;
        }
        if (releaseIndex >= storedPackets.size()) {
            storedPackets.clear();
            releasing = false;
        }
    }

    public static int getStoredPacketCount() {
        return storedPackets.size();
    }

    public static void tickRelease() {
        Blink blink = (Blink) nv.navineclient.module.ModuleManager.getModuleByName("Blink");
        if (blink != null && blink.releasing) {
            blink.releaseBatch();
        }
    }
}
