package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

public class KnockbackPlus extends Module {
    public static KnockbackPlus INSTANCE;
    private final NumberSetting multiplier = new NumberSetting("Multiplier", "Knockback multiplier", 1.5, 1.0, 3.0);
    private final NumberSetting boostStrength = new NumberSetting("Strength", "Boost strength factor", 0.35, 0.05, 1.0);
    private final BooleanSetting playersOnly = new BooleanSetting("Players", "Only boost vs players", false);

    public KnockbackPlus() {
        super("KnockbackPlus", "Increases knockback dealt to enemies", Category.COMBAT);
        INSTANCE = this;
        addSetting(multiplier);
        addSetting(boostStrength);
        addSetting(playersOnly);
    }

    public void doKnockbackBoost(LivingEntity target) {
        if (target == null || mc.player == null) {
            return;
        }
        if (playersOnly.getValue() && !(target instanceof Player)) {
            return;
        }

        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return;
        }
        dx /= len;
        dz /= len;

        double strength = multiplier.getValue() * boostStrength.getValue();
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(
                motion.x + dx * strength,
                motion.y + strength * 0.15,
                motion.z + dz * strength
        );
        ClientAccess.markVelocityUpdate(target);
    }

    public static boolean shouldBoost() {
        Module module = ModuleManager.getModuleByName("KnockbackPlus");
        return module != null && module.isEnabled();
    }
}
