package nv.navineclient.module.render;

import nv.navineclient.util.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.ColorSetting;
import nv.navineclient.module.settings.NumberSetting;
import nv.navineclient.module.settings.BooleanSetting;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    public static Trajectories INSTANCE;

    private final ColorSetting color = new ColorSetting("Color", "Trajectory color", 0xFF00FF00);
    private final NumberSetting maxSteps = new NumberSetting("MaxSteps", "Simulation steps", 300.0, 50.0, 500.0);
    private final BooleanSetting showLanding = new BooleanSetting("Landing", "Highlight landing point", true);

    public Trajectories() {
        super("Trajectories", "Shows projectile trajectories", Category.RENDER);
        INSTANCE = this;
        addSetting(color);
        addSetting(maxSteps);
        addSetting(showLanding);
    }

    public static boolean isTrajectories() {
        Module module = ModuleManager.getModuleByName("Trajectories");
        return module != null && module.isEnabled();
    }

    public static void render(GuiGraphicsExtractor context, Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        Trajectories mod = INSTANCE;
        int lineColor = mod != null ? mod.color.getValue() : 0xFF00FF00;
        int dotColor = mod != null ? (mod.color.getValue() | 0xFF000000) : 0xFF00FF00;
        boolean highlightLanding = mod == null || mod.showLanding.getValue();

        List<Vec3> points = simulateTrajectory(client.player, client);
        if (points.size() < 2) {
            return;
        }

        float[] prev = new float[2];
        boolean hadPrev = false;
        for (Vec3 point : points) {
            float[] screen = new float[2];
            if (!projectToScreen(client, point, screen)) {
                hadPrev = false;
                continue;
            }
            int x = (int) screen[0];
            int y = (int) screen[1];
            if (x < 0 || y < 0 || x >= context.guiWidth() || y >= context.guiHeight()) {
                hadPrev = false;
                continue;
            }
            context.fill(x - 1, y - 1, x + 2, y + 2, dotColor);
            if (hadPrev) {
                drawLine(context, (int) prev[0], (int) prev[1], x, y, (lineColor & 0x00FFFFFF) | 0xAA000000);
            }
            prev[0] = screen[0];
            prev[1] = screen[1];
            hadPrev = true;
        }

        if (highlightLanding && !points.isEmpty()) {
            Vec3 last = points.get(points.size() - 1);
            float[] screen = new float[2];
            if (projectToScreen(client, last, screen)) {
                int lx = (int) screen[0];
                int ly = (int) screen[1];
                if (lx >= 0 && ly >= 0 && lx < context.guiWidth() && ly < context.guiHeight()) {
                    context.fill(lx - 2, ly - 2, lx + 3, ly + 3, dotColor);
                }
            }
        }
    }

    private static List<Vec3> simulateTrajectory(Player player, Minecraft client) {
        List<Vec3> points = new ArrayList<>();
        ItemStack stack = player.getMainHandItem();
        float velocity = getThrowVelocity(stack, player);
        if (velocity <= 0.0f) {
            return points;
        }

        Vec3 pos = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 motion = look.scale(velocity);
        float gravity = 0.05f;
        float drag = stack.is(Items.ARROW) ? 0.99f : 0.99f;

        int steps = INSTANCE != null ? INSTANCE.maxSteps.getValue().intValue() : 300;
        for (int i = 0; i < steps; i++) {
            points.add(pos);
            Vec3 next = pos.add(motion);
            HitResult hit = client.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                points.add(hit.getLocation());
                break;
            }
            pos = next;
            motion = motion.subtract(0.0, gravity, 0.0).scale(drag);
            if (pos.y < client.level.getMinY()) {
                break;
            }
        }
        return points;
    }

    private static float getThrowVelocity(ItemStack stack, Player player) {
        if (stack.getItem() instanceof BowItem) {
            int useTime = player.getTicksUsingItem();
            float pull = BowItem.getPowerForTime(useTime);
            return pull * 3.0f;
        }
        if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL)) {
            return 1.5f;
        }
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return 0.7f;
        }
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            return 0.5f;
        }
        if (stack.is(Items.TRIDENT)) {
            return 2.5f;
        }
        if (stack.is(Items.ARROW)) {
            return 3.0f;
        }
        return 0.0f;
    }

    public static boolean projectToScreen(Minecraft client, Vec3 worldPos, float[] out) {
        if (client.gameRenderer == null) {
            return false;
        }
        var camera = ClientAccess.getMainCamera(client);
        Vec3 camPos = camera.position();
        Vec3 diff = worldPos.subtract(camPos);

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
        if (!Float.isFinite(out[0]) || !Float.isFinite(out[1])) {
            return false;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        if (out[0] < 0 || out[1] < 0 || out[0] >= width || out[1] >= height) {
            return false;
        }
        return true;
    }

    public static void drawLine(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            context.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
