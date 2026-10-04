package nv.navineclient.module.combat;

import nv.navineclient.util.ClientScreens;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

public class WTap extends Module {
    public static WTap INSTANCE;

    private final NumberSetting chance = new NumberSetting("Chance", "WTap chance percent", 100.0, 0.0, 100.0);
    private final NumberSetting releaseDelay = new NumberSetting("Release", "Release W delay ms", 50.0, 0.0, 500.0);
    private final NumberSetting rePressDelay = new NumberSetting("RePress", "Re-press W delay ms", 50.0, 0.0, 500.0);
    private final BooleanSetting selectHits = new BooleanSetting("SelectHits", "Only when target is vulnerable", true);

    private boolean releasePending;
    private boolean rePressPending;
    private long releaseAt;
    private long rePressAt;
    private boolean forwardPhysicallyDown;

    public WTap() {
        super("WTap", "Sprint reset on hit for extra knockback", Category.COMBAT);
        INSTANCE = this;
        addSetting(chance);
        addSetting(releaseDelay);
        addSetting(rePressDelay);
        addSetting(selectHits);
    }

    @Override
    public void onDisable() {
        restoreForwardKey();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) {
            return;
        }
        if (ClientScreens.get(mc) != null) {
            if (releasePending || rePressPending) {
                restoreForwardKey();
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (releasePending && now >= releaseAt) {
            mc.options.keyUp.setDown(false);
            releasePending = false;
            rePressPending = true;
            rePressAt = now + (long) rePressDelay.getValue().doubleValue();
        }
        if (rePressPending && now >= rePressAt) {
            if (forwardPhysicallyDown) {
                mc.options.keyUp.setDown(true);
            }
            rePressPending = false;
        }
    }

    public void onPlayerAttack(Entity target) {
        if (!isEnabled() || mc.player == null || mc.options == null) {
            return;
        }
        if (releasePending || rePressPending) {
            return;
        }
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        if (selectHits.getValue() && living.hurtTime <= 0) {
            return;
        }
        if (chance.getValue() < 100.0) {
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll > chance.getValue()) {
                return;
            }
        }
        forwardPhysicallyDown = mc.options.keyUp.isDown();
        if (!forwardPhysicallyDown) {
            return;
        }
        releasePending = true;
        releaseAt = System.currentTimeMillis() + (long) releaseDelay.getValue().doubleValue();
    }

    private void restoreForwardKey() {
        if (mc.options != null && forwardPhysicallyDown) {
            mc.options.keyUp.setDown(true);
        }
        releasePending = false;
        rePressPending = false;
    }
}
