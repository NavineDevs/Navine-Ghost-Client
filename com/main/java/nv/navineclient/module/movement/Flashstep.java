package nv.navineclient.module.movement;

import nv.navineclient.module.Module;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.util.NavineInput;
import nv.navineclient.util.TeleportHelper;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Flashstep extends Module {
    private final NumberSetting distance = new NumberSetting("Distance", "Teleport distance", 5.0, 1.0, 20.0);
    private final BooleanSetting allowVertical = new BooleanSetting("AllowVertical", "Allow teleporting up/down", true);
    private final NumberSetting cooldown = new NumberSetting("Cooldown", "Cooldown ms", 250.0, 0.0, 2000.0);
    private final BooleanSetting onKeyPress = new BooleanSetting("OnKeyPress", "Teleport when key is pressed while enabled", true);

    private long lastTeleport;
    private boolean wasKeyDown;

    public Flashstep() {
        super("Flashstep", "Teleport forward with your keybind", Category.MOVEMENT);
        addSetting(distance);
        addSetting(allowVertical);
        addSetting(cooldown);
        addSetting(onKeyPress);
    }

    @Override
    public void onEnable() {
        if (!onKeyPress.getValue()) {
            tryTeleport();
        }
        wasKeyDown = isBindDown();
    }

    @Override
    public void onTick() {
        if (!onKeyPress.getValue() || mc.player == null) {
            return;
        }
        boolean keyDown = isBindDown();
        if (keyDown && !wasKeyDown) {
            tryTeleport();
        }
        wasKeyDown = keyDown;
    }

    private boolean isBindDown() {
        if (getKey() == 0 || mc.options == null) {
            return false;
        }
        return NavineInput.isKeyDown(getKey());
    }

    private void tryTeleport() {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        long minCooldown = AcBypassUtil.isLegitActive() ? Math.max(cooldown.getValue().longValue(), 400L) : cooldown.getValue().longValue();
        if (currentTime - lastTeleport < minCooldown) {
            return;
        }
        teleport();
        lastTeleport = currentTime;
    }

    private void teleport() {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        double dist = Math.min(distance.getValue(), AcBypassUtil.maxTeleportSegment() * 2.0);

        double dx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * dist;
        double dy = allowVertical.getValue() ? -Math.sin(Math.toRadians(pitch)) * dist : 0;
        double dz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * dist;

        double newX = mc.player.getX() + dx;
        double newY = mc.player.getY() + dy;
        double newZ = mc.player.getZ() + dz;

        TeleportHelper.teleportTo(newX, newY, newZ, mc.player.getYRot(), mc.player.getXRot());
    }
}
