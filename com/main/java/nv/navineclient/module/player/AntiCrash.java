package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.util.ChatUtils;

public class AntiCrash extends Module {
    private final BooleanSetting protectSelf = new BooleanSetting("ProtectSelf", "Protect your client from self-crash packets", true);
    private final BooleanSetting preventGameFreeze = new BooleanSetting("PreventGameFreeze", "Try to recover from client freeze", true);
    private final BooleanSetting preventServerDisconnect = new BooleanSetting("PreventServerDisconnect", "Keep connection alive on idle", true);
    private final BooleanSetting preventPacketCrash = new BooleanSetting("PreventPacketCrash", "Block incoming crash chat packets", true);
    private final BooleanSetting preventEntityCrash = new BooleanSetting("PreventEntityCrash", "Reset invalid entity positions", true);
    private final BooleanSetting preventRenderCrash = new BooleanSetting("PreventRenderCrash", "Skip broken entity rendering", true);
    private final BooleanSetting alertUser = new BooleanSetting("AlertUser", "Alert on prevention", true);

    private int preventedCrashes;
    private long lastAlert;

    public AntiCrash() {
        super("AntiCrash", "Protect against incoming crashes while allowing outbound tools", Category.PLAYER);
        addSetting(protectSelf);
        addSetting(preventGameFreeze);
        addSetting(preventServerDisconnect);
        addSetting(preventPacketCrash);
        addSetting(preventEntityCrash);
        addSetting(preventRenderCrash);
        addSetting(alertUser);
    }

    @Override
    public void onTick() {
        if (mc.player == null) {
            return;
        }
    }

    public void recordPreventedCrash(String crashType) {
        preventedCrashes++;

        if (alertUser.getValue() && System.currentTimeMillis() - lastAlert > 1000) {
            ChatUtils.message("§6AntiCrash: Prevented " + crashType + " crash");
            lastAlert = System.currentTimeMillis();
        }
    }

    public boolean shouldProtectSelf() {
        return protectSelf.getValue();
    }

    public boolean shouldPreventGameFreeze() {
        return preventGameFreeze.getValue();
    }

    public boolean shouldPreventServerDisconnect() {
        return preventServerDisconnect.getValue();
    }

    public boolean shouldPreventPacketCrash() {
        return preventPacketCrash.getValue();
    }

    public boolean shouldPreventEntityCrash() {
        return preventEntityCrash.getValue();
    }

    public boolean shouldPreventRenderCrash() {
        return preventRenderCrash.getValue();
    }

    public int getPreventedCrashCount() {
        return preventedCrashes;
    }
}
