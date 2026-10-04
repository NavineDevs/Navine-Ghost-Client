package nv.navineclient;

import nv.navineclient.module.combat.Reach;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class GhostHitOverride {
    private static final List<Field> hitFields = new ArrayList<>();
    private static boolean fieldsResolved;

    private GhostHitOverride() {
    }

    public static void apply(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        Reach reach = Reach.INSTANCE;
        if (reach == null || !reach.isActive()) {
            return;
        }
        resolveFields(client);
        applyPlayerRange(client.player, reach.getReachDistance(), reach.getBlockReachDistance());
        EntityHitResult entityHit = pickEntity(client, reach.getReachDistance());
        if (entityHit != null) {
            writeHit(client, entityHit);
            return;
        }
        BlockHitResult blockHit = pickBlock(client, reach.getBlockReachDistance());
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            writeHit(client, blockHit);
        }
    }

    private static void resolveFields(Minecraft client) {
        if (fieldsResolved) {
            return;
        }
        fieldsResolved = true;
        Class<?> c = client.getClass();
        while (c != null && c != Object.class) {
            for (Field field : c.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (HitResult.class.isAssignableFrom(type) || type.getName().contains("HitResult")) {
                    field.setAccessible(true);
                    hitFields.add(field);
                }
            }
            c = c.getSuperclass();
        }
    }

    public static void resetPlayerRange(Player player) {
        if (player == null) {
            return;
        }
        boolean creative = false;
        try {
            creative = player.isCreative();
        } catch (Throwable ignored) {
        }
        applyPlayerRange(player, creative ? 5.0 : 3.0, creative ? 5.0 : 4.5);
    }

    private static void applyPlayerRange(Player player, double attack, double block) {
        if (player == null) {
            return;
        }
        setInteractionAttribute(player, "ENTITY_INTERACTION_RANGE", attack);
        setInteractionAttribute(player, "BLOCK_INTERACTION_RANGE", block);
        invokeRangeHint(player, "entityInteractionRange");
        invokeRangeHint(player, "blockInteractionRange");
    }

    private static void setInteractionAttribute(Player player, String fieldName, double value) {
        try {
            Class<?> attributes = Class.forName("net.minecraft.world.entity.ai.attributes.Attributes", true, player.getClass().getClassLoader());
            Object holder = attributes.getField(fieldName).get(null);
            Method getAttribute = null;
            for (String name : new String[]{"getAttribute", "method_5996", "getAttributeInstance"}) {
                try {
                    getAttribute = player.getClass().getMethod(name, holder.getClass());
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (getAttribute == null) {
                for (Method m : player.getClass().getMethods()) {
                    if ((m.getName().contains("Attribute") || m.getName().contains("attribute"))
                            && m.getParameterCount() == 1) {
                        getAttribute = m;
                        break;
                    }
                }
            }
            if (getAttribute == null) {
                return;
            }
            Object instance = getAttribute.invoke(player, holder);
            if (instance == null) {
                return;
            }
            for (String name : new String[]{"setBaseValue", "method_6192"}) {
                try {
                    instance.getClass().getMethod(name, double.class).invoke(instance, value);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void invokeRangeHint(Player player, String methodName) {
        try {
            Method method = player.getClass().getMethod(methodName);
            method.invoke(player);
        } catch (Throwable ignored) {
        }
    }

    private static void writeHit(Minecraft client, HitResult hit) {
        for (Field field : hitFields) {
            try {
                field.set(client, hit);
            } catch (Throwable ignored) {
            }
        }
        try {
            Method setter = client.getClass().getMethod("setHitResult", HitResult.class);
            setter.invoke(client, hit);
        } catch (Throwable ignored) {
        }
    }

    private static EntityHitResult pickEntity(Minecraft client, double range) {
        Player player = client.player;
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
        try {
            EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                    player, eye, end, box, pred, range * range);
            if (hit != null) {
                return hit;
            }
        } catch (Throwable ignored) {
        }
        EntityHitResult best = null;
        double bestDist = range * range;
        for (Entity entity : client.level.getEntities(player, box, pred)) {
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

    private static BlockHitResult pickBlock(Minecraft client, double range) {
        Player player = client.player;
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        try {
            return client.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
