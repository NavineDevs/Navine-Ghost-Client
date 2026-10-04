package nv.navineclient.module.combat;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class GodMode extends Module {
    public static GodMode INSTANCE;
    private final NumberSetting packetInterval = new NumberSetting("Interval", "Ticks between packets", 20.0, 5.0, 40.0);
    private final NumberSetting yOffset = new NumberSetting("YOffset", "Vertical packet offset", 0.0001, 0.0001, 0.1);
    private final BooleanSetting onlyWhenFalling = new BooleanSetting("OnlyFalling", "Only when taking fall damage", true);

    private int ticks = 0;

    public GodMode() {
        super("GodMode", "Attempts to prevent damage (server dependent)", Category.COMBAT);
        INSTANCE = this;
        addSetting(packetInterval);
        addSetting(yOffset);
        addSetting(onlyWhenFalling);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;

        if (onlyWhenFalling.getValue() && mc.player.fallDistance < 2.0f && mc.player.getHealth() >= mc.player.getMaxHealth()) {
            return;
        }

        ticks++;
        int interval = packetInterval.getValue().intValue();
        if (ticks >= interval) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    x, y + yOffset.getValue(), z, mc.player.getYRot(), mc.player.getXRot(), true, mc.player.horizontalCollision));
            ticks = 0;
        }
    }

    public static boolean isGodMode() {
        Module module = ModuleManager.getModuleByName("GodMode");
        return module != null && module.isEnabled();
    }
}
