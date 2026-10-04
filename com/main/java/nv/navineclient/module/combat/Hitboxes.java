package nv.navineclient.module.combat;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class Hitboxes extends Module {
    private final NumberSetting width = new NumberSetting("Width", "Hitbox width", 0.6, 0.1, 1.0);
    private final NumberSetting height = new NumberSetting("Height", "Hitbox height", 1.8, 0.5, 2.5);
    private final BooleanSetting expand = new BooleanSetting("Expand", "Expand hitboxes", true);
    private final NumberSetting expandAmount = new NumberSetting("ExpandAmount", "Expansion radius", 0.1, 0.0, 0.5);

    public Hitboxes() {
        super("Hitboxes", "Expand entity hitboxes for easier combat", Category.COMBAT);
        addSetting(width);
        addSetting(height);
        addSetting(expand);
        addSetting(expandAmount);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        // Hitbox expansion is applied via mixin to entity collision boxes
        // This module manages the settings and expansion state
    }

    public double getHitboxWidth() {
        return width.getValue();
    }

    public double getHitboxHeight() {
        return height.getValue();
    }

    public double getExpansionAmount() {
        return expand.getValue() ? expandAmount.getValue() : 0.0;
    }
}
