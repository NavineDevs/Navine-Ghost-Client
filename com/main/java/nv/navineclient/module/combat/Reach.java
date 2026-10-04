package nv.navineclient.module.combat;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.util.AcBypassUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

public class Reach extends Module {
    public static Reach INSTANCE;
    private final NumberSetting reachDist = new NumberSetting("Reach", "Attack reach distance", 4.5, 3.0, 10.0);
    private final NumberSetting blockReach = new NumberSetting("BlockReach", "Block interaction reach", 5.0, 3.0, 10.0);
    private final BooleanSetting creativeOnly = new BooleanSetting("CreativeOnly", "Only in creative mode", false);
    private final BooleanSetting tickAssist = new BooleanSetting("TickAssist", "Attack assist when mixins missing", false);
    private final NumberSetting chance = new NumberSetting("Chance", "Reach chance percent", 100.0, 0.0, 100.0);
    private final BooleanSetting sprintOnly = new BooleanSetting("SprintOnly", "Only while sprinting", false);

    public Reach() {
        super("Reach", "Increases attack and block reach", Category.COMBAT);
        INSTANCE = this;
        addSetting(reachDist);
        addSetting(blockReach);
        addSetting(chance);
        addSetting(sprintOnly);
        addSetting(creativeOnly);
        addSetting(tickAssist);
    }

    public boolean isActive() {
        if (!isEnabled()) {
            return false;
        }
        refreshClient();
        if (creativeOnly.getValue() && mc != null && mc.player != null && !mc.player.isCreative()) {
            return false;
        }
        if (sprintOnly.getValue() && mc != null && mc.player != null && !mc.player.isSprinting()) {
            return false;
        }
        if (chance.getValue() < 100.0) {
            double roll = Math.random() * 100.0;
            if (roll > chance.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDisable() {
        refreshClient();
        if (mc != null && mc.player != null) {
            nv.navineclient.GhostHitOverride.resetPlayerRange(mc.player);
        }
    }

    public double getReachDistance() {
        return AcBypassUtil.clampAttackReach(reachDist.getValue());
    }

    public double getBlockReachDistance() {
        return AcBypassUtil.clampBlockReach(blockReach.getValue());
    }

    @Override
    public void onTick() {
        if (!isActive() || !tickAssist.getValue()) {
            return;
        }
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null || mc.options == null) {
            return;
        }
        if (ClientScreens.get(mc) != null || !mc.options.keyAttack.isDown()) {
            return;
        }
        if (mc.player.getAttackStrengthScale(0.0f) < 0.92f) {
            return;
        }
        Entity target = pickEntity(getReachDistance());
        if (target == null) {
            return;
        }
        try {
            mc.gameMode.attack(mc.player, target);
            ClientAccess.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    private Entity pickEntity(double range) {
        Player player = mc.player;
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        Predicate<Entity> pred = e -> e != null
                && e.isPickable()
                && e instanceof LivingEntity living
                && living.isAlive()
                && e != player
                && !e.isSpectator();
        EntityHitResult hit = null;
        try {
            hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    player, eye, end, box, pred, range * range);
        } catch (Throwable ignored) {
            hit = manualPick(eye, end, box, pred, range * range);
        }
        return hit != null ? hit.getEntity() : null;
    }

    private EntityHitResult manualPick(Vec3 eye, Vec3 end, AABB box, Predicate<Entity> pred, double maxDistSq) {
        EntityHitResult best = null;
        double bestDist = maxDistSq;
        for (Entity entity : mc.level.getEntities(mc.player, box, pred)) {
            AABB bb = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> clip = bb.clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double dist = eye.distanceToSqr(clip.get());
            if (dist < bestDist) {
                bestDist = dist;
                best = new EntityHitResult(entity, clip.get());
            }
        }
        return best;
    }

    public static double getAttackReach() {
        Module module = ModuleManager.getModuleByName("Reach");
        if (module != null && module.isEnabled() && module instanceof Reach reach && reach.isActive()) {
            return reach.getReachDistance();
        }
        return 3.0;
    }

    public static double getBlockReach() {
        Module module = ModuleManager.getModuleByName("Reach");
        if (module != null && module.isEnabled() && module instanceof Reach reach && reach.isActive()) {
            return reach.getBlockReachDistance();
        }
        return 4.5;
    }
}
