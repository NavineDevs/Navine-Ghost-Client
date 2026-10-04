 package nv.navineclient.module.world;

import net.minecraft.world.level.block.Blocks;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class AntiGrief extends Module {
    private final BooleanSetting preventBlockBreak = new BooleanSetting("PreventBreak", "Prevent block breaking", false);
    private final BooleanSetting preventBlockPlace = new BooleanSetting("PreventPlace", "Prevent block placement", false);
    private final BooleanSetting protectChests = new BooleanSetting("ProtectChests", "Prevent chest damage", true);
    private final BooleanSetting protectEnderChests = new BooleanSetting("ProtectEnderChests", "Prevent ender chest damage", true);
    private final NumberSetting protectionRadius = new NumberSetting("Radius", "Protection radius", 50.0, 5.0, 128.0);

    public AntiGrief() {
        super("AntiGrief", "Prevent accidental block breaking/placing", Category.WORLD);
        addSetting(preventBlockBreak);
        addSetting(preventBlockPlace);
        addSetting(protectChests);
        addSetting(protectEnderChests);
        addSetting(protectionRadius);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        // Module logic would be handled via mixins
        // This provides settings and state management
    }

    public boolean shouldPreventBlockBreak() {
        return preventBlockBreak.getValue();
    }

    public boolean shouldPreventBlockPlace() {
        return preventBlockPlace.getValue();
    }

    public boolean isChestProtected() {
        return protectChests.getValue();
    }

    public boolean isEnderChestProtected() {
        return protectEnderChests.getValue();
    }

    public double getProtectionRadius() {
        return protectionRadius.getValue();
    }
}
