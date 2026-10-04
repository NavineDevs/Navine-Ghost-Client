package nv.navineclient.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import nv.navineclient.module.render.Tracer;

public final class ESPUtil {
    private ESPUtil() {
    }

    public static void applyESP(Entity entity) {
    }

    public static boolean shouldRenderTracer(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.player == null || entity == mc.player) {
            return false;
        }

        Tracer tracer = (Tracer) nv.navineclient.module.ModuleManager.getModuleByName("Tracer");
        if (tracer == null || !tracer.isEnabled()) {
            return false;
        }

        if (mc.player.distanceTo(entity) > tracer.getRange()) {
            return false;
        }

        if (entity instanceof Player && tracer.showPlayers()) {
            return true;
        }
        if (entity instanceof Monster && tracer.showMobs()) {
            return true;
        }
        if (entity instanceof Animal && tracer.showAnimals()) {
            return true;
        }

        return false;
    }
}
