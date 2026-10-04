package nv.navineclient.module.combat;

import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.util.AcBypassUtil;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import net.minecraft.world.phys.Vec3;

public class Velocity extends Module {
    public static Velocity INSTANCE;
    private final NumberSetting horizontal = new NumberSetting("Horizontal", "Horizontal knockback %", 0.0, 0.0, 100.0);
    private final NumberSetting vertical = new NumberSetting("Vertical", "Vertical knockback %", 0.0, 0.0, 100.0);
    private final NumberSetting chance = new NumberSetting("Chance", "Activation chance %", 100.0, 0.0, 100.0);
    private final BooleanSetting horizontalOnly = new BooleanSetting("HorizontalOnly", "Only reduce horizontal knockback", false);
    private final BooleanSetting tickAssist = new BooleanSetting("TickAssist", "Clamp motion each tick when mixins missing", false);

    private boolean chancePassed = true;
    private Vec3 lastMotion = Vec3.ZERO;

    public Velocity() {
        super("Velocity", "Reduces knockback taken", Category.COMBAT);
        INSTANCE = this;
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(chance);
        addSetting(horizontalOnly);
        addSetting(tickAssist);
    }

    public void rollChance() {
        chancePassed = Math.random() * 100.0 <= chance.getValue();
    }

    public double getHorizontalMultiplier() {
        if (!chancePassed) {
            return 1.0;
        }
        return AcBypassUtil.minVelocityPercent(horizontal.getValue()) / 100.0;
    }

    public double getVerticalMultiplier() {
        if (!chancePassed) {
            return 1.0;
        }
        if (horizontalOnly.getValue()) {
            return 1.0;
        }
        return AcBypassUtil.minVelocityPercent(vertical.getValue()) / 100.0;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || !tickAssist.getValue()) {
            return;
        }
        refreshClient();
        if (mc == null || mc.player == null) {
            return;
        }
        Vec3 motion = mc.player.getDeltaMovement();
        double dx = motion.x - lastMotion.x;
        double dy = motion.y - lastMotion.y;
        double dz = motion.z - lastMotion.z;
        double impulse = Math.sqrt(dx * dx + dz * dz);
        if (impulse > 0.08 || Math.abs(dy) > 0.12) {
            if (!mc.player.onGround() || impulse > 0.12) {
                rollChance();
                double h = getHorizontalMultiplier();
                double v = getVerticalMultiplier();
                if (h < 0.999 || v < 0.999) {
                    double nx = lastMotion.x + dx * h;
                    double ny = lastMotion.y + dy * v;
                    double nz = lastMotion.z + dz * h;
                    mc.player.setDeltaMovement(nx, ny, nz);
                    motion = mc.player.getDeltaMovement();
                }
            }
        }
        lastMotion = motion;
    }

    public static boolean shouldModify() {
        Module module = ModuleManager.getModuleByName("Velocity");
        return module != null && module.isEnabled();
    }

    public static double getHorizontalMultiplierStatic() {
        Module module = ModuleManager.getModuleByName("Velocity");
        if (module != null && module.isEnabled() && module instanceof Velocity vel) {
            return vel.getHorizontalMultiplier();
        }
        return 1.0;
    }

    public static double getVerticalMultiplierStatic() {
        Module module = ModuleManager.getModuleByName("Velocity");
        if (module != null && module.isEnabled() && module instanceof Velocity vel) {
            return vel.getVerticalMultiplier();
        }
        return 1.0;
    }
}
