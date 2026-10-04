package nv.navineclient.module.player;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.ChatUtils;

public class AutoLog extends Module {
    private final NumberSetting health = new NumberSetting("Health", "Disconnect below this health", 5.0, 1.0, 19.0);
    private final NumberSetting fallDamage = new NumberSetting("FallDamage", "Disconnect below fall distance", 0.0, 0.0, 20.0);
    private final BooleanSetting totemPop = new BooleanSetting("TotemPop", "Disconnect on totem pop", false);

    private boolean hadTotem = false;

    public AutoLog() {
        super("AutoLog", "Auto disconnects at low health", Category.PLAYER);
        addSetting(health);
        addSetting(fallDamage);
        addSetting(totemPop);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getConnection() == null) return;

        boolean hasTotem = mc.player.getOffhandItem().getItem() == net.minecraft.world.item.Items.TOTEM_OF_UNDYING
                || mc.player.getMainHandItem().getItem() == net.minecraft.world.item.Items.TOTEM_OF_UNDYING;

        if (totemPop.getValue() && hadTotem && !hasTotem) {
            disconnect("Totem popped");
            return;
        }
        hadTotem = hasTotem;

        if (mc.player.getHealth() <= health.getValue()) {
            disconnect("Health too low");
            return;
        }

        if (fallDamage.getValue() > 0 && mc.player.fallDistance >= fallDamage.getValue()) {
            disconnect("Fall damage risk");
        }
    }

    private void disconnect(String reason) {
        ChatUtils.message("AutoLog: Disconnecting - " + reason);
        mc.player.connection.getConnection().disconnect(net.minecraft.network.chat.Component.literal("AutoLog - " + reason));
        setEnabled(false);
    }
}
