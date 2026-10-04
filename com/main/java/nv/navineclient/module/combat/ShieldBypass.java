package nv.navineclient.module.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.NumberSetting;

public class ShieldBypass extends Module {
    public static ShieldBypass INSTANCE;
    private final ModeSetting mode = new ModeSetting("Mode", "Bypass method", "Packet", "Packet", "Angle", "Timing");
    private final BooleanSetting onlyPlayers = new BooleanSetting("Players", "Only target players", true);
    private final NumberSetting range = new NumberSetting("Range", "Bypass range", 4.0, 1.0, 6.0);

    public ShieldBypass() {
        super("ShieldBypass", "Allows hitting through shields", Category.COMBAT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(onlyPlayers);
        addSetting(range);
    }

    public static boolean shouldBypass() {
        Module module = ModuleManager.getModuleByName("ShieldBypass");
        return module != null && module.isEnabled();
    }

    public boolean isInRange(LivingEntity entity) {
        if (mc.player == null) return false;
        return mc.player.distanceTo(entity) <= range.getValue();
    }

    public boolean shouldTarget(LivingEntity entity) {
        if (!isEnabled()) return false;
        if (onlyPlayers.getValue() && !(entity instanceof Player)) return false;
        return isInRange(entity);
    }

    public static boolean isBlocking(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;

        boolean hasShield = player.getOffhandItem().getItem() == Items.SHIELD
                || player.getMainHandItem().getItem() == Items.SHIELD;

        if (!hasShield) return false;

        return player.isUsingItem()
                && (player.getUsedItemHand() == InteractionHand.OFF_HAND || player.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }

    public String getMode() {
        return mode.getValue();
    }
}
