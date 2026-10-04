package nv.navineclient.module.render;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Freecam extends Module {
    public static Freecam INSTANCE;

    private final NumberSetting speed = new NumberSetting("Speed", "Camera speed", 0.75, 0.1, 3.0);
    private final NumberSetting smoothness = new NumberSetting("Smooth", "Movement smoothing", 0.35, 0.05, 1.0);
    private final BooleanSetting noClip = new BooleanSetting("NoClip", "Fly through blocks", true);
    private final BooleanSetting returnOnDisable = new BooleanSetting("Return", "Return player on disable", true);

    private double camX;
    private double camY;
    private double camZ;
    private double renderCamX;
    private double renderCamY;
    private double renderCamZ;
    private double playerX;
    private double playerY;
    private double playerZ;
    private float camYaw;
    private float camPitch;
    private float renderCamYaw;
    private float renderCamPitch;
    private boolean cameraActive;
    private CameraType savedCameraType;

    public Freecam() {
        super("Freecam", "Detach camera from player", Category.RENDER);
        INSTANCE = this;
        addSetting(speed);
        addSetting(smoothness);
        addSetting(noClip);
        addSetting(returnOnDisable);
    }

    public boolean isCameraActive() {
        return isEnabled() && cameraActive;
    }

    public boolean isNoClip() {
        return noClip.getValue();
    }

    public double getCamX() { return renderCamX; }
    public double getCamY() { return renderCamY; }
    public double getCamZ() { return renderCamZ; }
    public float getCamYaw() { return renderCamYaw; }
    public float getCamPitch() { return renderCamPitch; }

    public void applyRenderInterpolation(float partialTick) {
        if (!cameraActive) {
            return;
        }
        float blend = Mth.clamp(partialTick, 0f, 1f);
        renderCamX = Mth.lerp(blend, renderCamX, camX);
        renderCamY = Mth.lerp(blend, renderCamY, camY);
        renderCamZ = Mth.lerp(blend, renderCamZ, camZ);
        renderCamYaw = Mth.rotLerp(blend, renderCamYaw, camYaw);
        renderCamPitch = Mth.lerp(blend, renderCamPitch, camPitch);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.options == null) {
            return;
        }
        cameraActive = true;
        playerX = mc.player.getX();
        playerY = mc.player.getY();
        playerZ = mc.player.getZ();
        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();
        camX = playerX;
        camY = playerY + mc.player.getEyeHeight(mc.player.getPose());
        camZ = playerZ;
        renderCamX = camX;
        renderCamY = camY;
        renderCamZ = camZ;
        renderCamYaw = camYaw;
        renderCamPitch = camPitch;
        savedCameraType = mc.options.getCameraType();
        if (savedCameraType.isFirstPerson()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        cameraActive = false;
        if (mc.options != null && savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
        if (mc.player == null || !returnOnDisable.getValue()) {
            return;
        }
        mc.player.setPos(playerX, playerY, playerZ);
        mc.player.setYRot(camYaw);
        mc.player.setXRot(camPitch);
        mc.player.setDeltaMovement(0, 0, 0);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !cameraActive) {
            return;
        }

        mc.player.setPos(playerX, playerY, playerZ);
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setOnGround(false);
        mc.player.setYRot(camYaw);
        mc.player.setXRot(camPitch);

        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();

        double moveSpeed = speed.getValue();
        double dx = 0;
        double dy = 0;
        double dz = 0;

        if (mc.options.keyUp.isDown()) {
            dx -= Math.sin(Math.toRadians(camYaw)) * Math.cos(Math.toRadians(camPitch)) * moveSpeed;
            dy -= Math.sin(Math.toRadians(camPitch)) * moveSpeed;
            dz += Math.cos(Math.toRadians(camYaw)) * Math.cos(Math.toRadians(camPitch)) * moveSpeed;
        }
        if (mc.options.keyDown.isDown()) {
            dx += Math.sin(Math.toRadians(camYaw)) * Math.cos(Math.toRadians(camPitch)) * moveSpeed;
            dy += Math.sin(Math.toRadians(camPitch)) * moveSpeed;
            dz -= Math.cos(Math.toRadians(camYaw)) * Math.cos(Math.toRadians(camPitch)) * moveSpeed;
        }
        if (mc.options.keyLeft.isDown()) {
            dx += Math.cos(Math.toRadians(camYaw)) * moveSpeed;
            dz += Math.sin(Math.toRadians(camYaw)) * moveSpeed;
        }
        if (mc.options.keyRight.isDown()) {
            dx -= Math.cos(Math.toRadians(camYaw)) * moveSpeed;
            dz -= Math.sin(Math.toRadians(camYaw)) * moveSpeed;
        }
        if (mc.options.keyJump.isDown()) {
            dy += moveSpeed;
        }
        if (mc.options.keyShift.isDown()) {
            dy -= moveSpeed;
        }

        double smooth = Mth.clamp(smoothness.getValue(), 0.05, 1.0);
        camX += dx * smooth;
        camY += dy * smooth;
        camZ += dz * smooth;
    }
}
