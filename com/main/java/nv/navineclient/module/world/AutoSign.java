package nv.navineclient.module.world;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.StringSetting;

public class AutoSign extends Module {
    private final StringSetting line1 = new StringSetting("Line1", "First sign line", "Navine Client");
    private final StringSetting line2 = new StringSetting("Line2", "Second sign line", "");
    private final NumberSetting delay = new NumberSetting("Delay", "Ticks before writing", 5.0, 0.0, 40.0);

    private int writeDelay = 0;
    private boolean pendingWrite = false;

    public AutoSign() {
        super("AutoSign", "Automatically writes on signs", Category.WORLD);
        addSetting(line1);
        addSetting(line2);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (!pendingWrite || mc.player == null) return;

        if (writeDelay > 0) {
            writeDelay--;
            return;
        }

        pendingWrite = false;
    }

    public void onSignOpen() {
        pendingWrite = true;
        writeDelay = delay.getValue().intValue();
    }

    public String getLine1() {
        return line1.getValue();
    }

    public String getLine2() {
        return line2.getValue();
    }

    public boolean isReadyToWrite() {
        return isEnabled() && pendingWrite && writeDelay <= 0;
    }
}
