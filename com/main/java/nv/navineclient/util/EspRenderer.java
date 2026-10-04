package nv.navineclient.util;

import nv.navineclient.util.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.render.ESP;
import nv.navineclient.module.render.Trajectories;

public final class EspRenderer {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private EspRenderer() {
    }

    public static void render(GuiGraphicsExtractor context, Minecraft client, ESP esp) {
        if (client.player == null || client.level == null || esp == null || !esp.isEnabled()) {
            return;
        }

        int outline = 0xFF000000 | (esp.getColor() & 0x00FFFFFF);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !esp.shouldHighlight(entity)) {
                continue;
            }
            drawEntityBox(context, client, entity.getBoundingBox(), outline);
        }
    }

    private static void drawEntityBox(GuiGraphicsExtractor context, Minecraft client, AABB box, int outlineColor) {
        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };

        float[][] screen = new float[8][2];
        boolean[] visible = new boolean[8];
        for (int i = 0; i < corners.length; i++) {
            visible[i] = projectToScreenRelaxed(client, corners[i], screen[i]);
        }

        for (int[] edge : BOX_EDGES) {
            int a = edge[0];
            int b = edge[1];
            if (!visible[a] || !visible[b]) {
                continue;
            }
            Trajectories.drawLine(context, (int) screen[a][0], (int) screen[a][1], (int) screen[b][0], (int) screen[b][1], outlineColor);
        }
    }

    private static boolean projectToScreenRelaxed(Minecraft client, Vec3 worldPos, float[] out) {
        if (client.gameRenderer == null) {
            return false;
        }
        var camera = ClientAccess.getMainCamera(client);
        Vec3 diff = worldPos.subtract(camera.position());

        float yaw = camera.yRot();
        float pitch = camera.xRot();
        double cosYaw = Math.cos(Math.toRadians(-yaw - 90.0));
        double sinYaw = Math.sin(Math.toRadians(-yaw - 90.0));
        double cosPitch = Math.cos(Math.toRadians(-pitch));
        double sinPitch = Math.sin(Math.toRadians(-pitch));

        double x = diff.x * cosYaw + diff.z * sinYaw;
        double z = -diff.x * sinYaw + diff.z * cosYaw;
        double y = diff.y * cosPitch - z * sinPitch;
        z = diff.y * sinPitch + z * cosPitch;

        if (z <= 0.05) {
            return false;
        }

        double fov = client.options.fov().get().intValue();
        double scale = client.getWindow().getGuiScaledHeight() / (2.0 * Math.tan(Math.toRadians(fov / 2.0)));
        out[0] = (float) (client.getWindow().getGuiScaledWidth() / 2.0 + x / z * scale);
        out[1] = (float) (client.getWindow().getGuiScaledHeight() / 2.0 - y / z * scale);
        return Float.isFinite(out[0]) && Float.isFinite(out[1]);
    }
}
