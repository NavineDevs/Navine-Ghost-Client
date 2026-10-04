package nv.navineclient.mixin;

import nv.navineclient.util.ClientAccess;
import nv.navineclient.util.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.*;
import nv.navineclient.util.BackgroundManager;
import nv.navineclient.util.HUDManager;
import nv.navineclient.util.NavineTheme;
import nv.navineclient.util.PingUtil;
import nv.navineclient.util.RenderUtil;
import nv.navineclient.util.ToggleNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    private static int lastOverlayTick = -1;
    private static volatile InGameHudMixin activeHost;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void navine$trackHost(CallbackInfo ci) {
        activeHost = (InGameHudMixin) (Object) this;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void navine_onRender(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        doRenderOverlays(context, tickCounter);
    }

    public static void renderOverlaysOnce(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        InGameHudMixin host = activeHost;
        if (host != null) {
            host.doRenderOverlays(context, tickCounter);
        }
    }

    private void doRenderOverlays(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getWindow() == null || client.font == null) {
                return;
            }
            int tick = 0;
            try {
                tick = ClientAccess.getGuiTicks(client);
            } catch (Throwable ignored) {
            }
            if (tick == lastOverlayTick) {
                return;
            }
            lastOverlayTick = tick;
            if (ClientAccess.getDebugOverlay(client) != null && ClientAccess.getDebugOverlay(client).showDebugScreen()) {
                return;
            }

            if (HUDManager.shouldHideOverlays(client)) {
                return;
            }

            boolean screenOpen = ClientScreens.get(client) != null;
            int bottomOffset = HUDManager.getBottomInset(client);
            
            int scaledHeight = context.guiHeight();
            int scaledWidth = context.guiWidth();
            if (scaledHeight <= 0) return;

            HUD hud = HUD.INSTANCE;
            if (!screenOpen && (hud == null || hud.isEnabled())) {
                java.util.List<String> lines = new java.util.ArrayList<>();

                if (hud == null || hud.showFps()) {
                    lines.add("§bFPS: §f" + client.getFps());
                }

                if (client.player != null && client.level != null) {
                    if (hud == null || hud.showCoords()) {
                        lines.add(String.format("§bXYZ: §f%d, %d, %d",
                            (int) client.player.getX(), (int) client.player.getY(), (int) client.player.getZ()));
                    }
                    if (hud == null || hud.showBiome()) {
                        try {
                            net.minecraft.core.BlockPos pos = client.player.blockPosition();
                            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeEntry = client.level.getBiome(pos);
                            if (biomeEntry != null) {
                                String biomeName = navine_formatName(biomeEntry.getRegisteredName());
                                if (!biomeName.isEmpty()) {
                                    lines.add("§dBiome: §f" + biomeName);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    if (hud == null || hud.showPing()) {
                        String pingLine = navine_formatPing(client);
                        if (pingLine != null) {
                            lines.add(pingLine);
                        }
                    }
                } else if (hud == null || hud.showCoords()) {
                    lines.add("§bXYZ: §f0, 0, 0");
                }

                if (!lines.isEmpty()) {
                    int maxW = 0;
                    for (String line : lines) {
                        maxW = Math.max(maxW, client.font.width(line));
                    }
                    int width = maxW + HUDManager.PANEL_PADDING * 2;
                    int height = lines.size() * HUDManager.LINE_HEIGHT + HUDManager.PANEL_PADDING;
                    int x = HUDManager.HUD_MARGIN;
                    int y;
                    if (nv.navineclient.config.ConfigManager.isHudTopLeft()) {
                        y = HUDManager.resolveTopLeftHudY(client, scaledWidth, scaledHeight, height);
                    } else {
                        y = scaledHeight - height - bottomOffset;
                    }

                    BackgroundManager.renderPanel(context, x - 2, y - 2, width + 4, height + 4);
                    int lineY = y + HUDManager.PANEL_PADDING;
                    for (String line : lines) {
                        context.text(client.font, line, x + HUDManager.PANEL_PADDING, lineY, -1, false);
                        lineY += HUDManager.LINE_HEIGHT;
                    }
                }

                if (hud != null && hud.isEnabled() && hud.showArrayList()) {
                    navine_renderModuleArrayList(context, client, scaledWidth, scaledHeight, bottomOffset);
                }
            }
            
            // Render Radar
            Radar radar = (Radar) ModuleManager.getModuleByName("Radar");
            if (radar != null && radar.isEnabled()) {
                renderRadar(context, client, radar);
            }
            
            // Render TargetHUD
            TargetHUD targetHUD = (TargetHUD) ModuleManager.getModuleByName("TargetHUD");
            if (targetHUD != null && targetHUD.isEnabled() && targetHUD.getTarget() != null) {
                renderTargetHUD(context, client, targetHUD);
            }
            
            // Render Waypoints
            Waypoints waypoints = (Waypoints) ModuleManager.getModuleByName("Waypoints");
            if (waypoints != null && waypoints.isEnabled()) {
                renderWaypoints(context, client, waypoints);
            }
            
            // Render Trajectories
            Trajectories trajectories = (Trajectories) ModuleManager.getModuleByName("Trajectories");
            if (trajectories != null && trajectories.isEnabled()) {
                Trajectories.render(context, client);
            }

            Breadcrumbs breadcrumbs = (Breadcrumbs) ModuleManager.getModuleByName("Breadcrumbs");
            if (breadcrumbs != null && breadcrumbs.isEnabled()) {
                renderBreadcrumbs(context, client, breadcrumbs);
            }

            StorageESP storageESP = (StorageESP) ModuleManager.getModuleByName("StorageESP");
            if (storageESP != null && storageESP.isEnabled()) {
                renderStorageESP(context, client, storageESP);
            }
            
            Nametags nametags = (Nametags) ModuleManager.getModuleByName("Nametags");
            if (nametags != null && nametags.isEnabled()) {
                renderNametags(context, client, nametags);
            }
            
            renderToggleNotification(context, client, screenOpen);

            if (nv.navineclient.module.world.DevMod.shouldShowTimings()) {
                String timing = String.format("§bTick: §f%.2fms", nv.navineclient.module.world.DevMod.lastTickMs);
                int hudInfoHeight = 0;
                if (hud == null || hud.isEnabled()) {
                    hudInfoHeight = 48;
                }
                int devY = HUDManager.resolveDevTimingsY(client, scaledWidth, scaledHeight, hudInfoHeight);
                context.text(client.font, timing, HUDManager.HUD_MARGIN, devY, -1, false);
            }

            if (nv.navineclient.util.NavinePathfinder.isActive() && !screenOpen) {
                net.minecraft.core.BlockPos goal = nv.navineclient.util.NavinePathfinder.getTarget();
                int remaining = nv.navineclient.util.NavinePathfinder.getRemainingWaypoints();
                if (goal != null) {
                    int pathY = HUDManager.resolveDevTimingsY(client, scaledWidth, scaledHeight, 0) + HUDManager.LINE_HEIGHT;
                    String pathLine = String.format("§bPath: §f%d,%d,%d §7(%d)", goal.getX(), goal.getY(), goal.getZ(), remaining);
                    context.text(client.font, pathLine, HUDManager.HUD_MARGIN, pathY, -1, false);
                }
            }

            Tracer tracer = (Tracer) ModuleManager.getModuleByName("Tracer");
            if (tracer != null && tracer.isEnabled()) {
                renderTracers(context, client, tracer);
            }

            ESP esp = ESP.INSTANCE;
            if (esp != null && esp.isEnabled()) {
                nv.navineclient.util.EspRenderer.render(context, client, esp);
            }

            nv.navineclient.module.misc.PacketLogger packetLogger =
                (nv.navineclient.module.misc.PacketLogger) ModuleManager.getModuleByName("PacketLogger");
            if (packetLogger != null && packetLogger.isEnabled()) {
                renderPacketLogger(context, client, packetLogger);
            }

            nv.navineclient.module.world.BuildHeight buildHeight = nv.navineclient.module.world.BuildHeight.INSTANCE;
            if (buildHeight != null && buildHeight.isActive() && buildHeight.shouldShowWarning() && client.player != null) {
                int limit = client.level.getMaxY() + buildHeight.getExtraHeight();
                int yPos = client.player.blockPosition().getY();
                if (yPos >= limit - 16) {
                    String warn = "Near build height limit: Y=" + limit;
                    context.text(client.font, warn, HUDManager.HUD_MARGIN, context.guiHeight() - 40, 0xFFFFAA00, false);
                }
            }

            nv.navineclient.module.player.PortalChat portalChat = nv.navineclient.module.player.PortalChat.INSTANCE;
            if (portalChat != null && portalChat.isEnabled() && portalChat.showIndicator() && client.player != null && client.level != null) {
                net.minecraft.core.BlockPos pos = client.player.blockPosition();
                boolean inPortal = client.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                        || client.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.END_PORTAL);
                if (inPortal) {
                    context.text(client.font, "PortalChat active", context.guiWidth() - 90, context.guiHeight() - 14, 0xFF55FFFF, false);
                }
            }
        } catch (Exception e) {
        }
    }

    private boolean isOnScreen(GuiGraphicsExtractor context, int x, int y) {
        return isOnScreen(context, x, y, 0);
    }

    private boolean isOnScreen(GuiGraphicsExtractor context, int x, int y, int margin) {
        return x >= margin && y >= margin && x < context.guiWidth() - margin && y < context.guiHeight() - margin;
    }

    private void renderPacketLogger(GuiGraphicsExtractor context, Minecraft client, nv.navineclient.module.misc.PacketLogger logger) {
        java.util.List<String> lines = logger.getDisplayLines();
        if (lines.isEmpty()) return;
        int x = context.guiWidth() - HUDManager.HUD_MARGIN;
        int y = HUDManager.HUD_MARGIN;
        for (String line : lines) {
            int w = client.font.width(line);
            context.fill(x - w - 4, y - 1, x + 2, y + 10, 0x90000000);
            context.text(client.font, line, x - w, y, 0xFF00FF00, false);
            y += 11;
        }
    }

    private void renderTracers(GuiGraphicsExtractor context, Minecraft client, Tracer tracer) {
        if (client.player == null || client.level == null) return;
        int color = tracer.getColor();
        int cx = context.guiWidth() / 2;
        int cy = context.guiHeight() / 2;
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == client.player) continue;
            if (!tracerMatches(tracer, living)) continue;
            if (client.player.distanceTo(entity) > tracer.getRange()) continue;
            double[] screen = worldToScreen(client, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ());
            if (screen == null) continue;
            drawTracerLine(context, cx, cy, (int) screen[0], (int) screen[1], color);
        }
    }

    private boolean tracerMatches(Tracer tracer, LivingEntity entity) {
        if (entity instanceof Player) return tracer.showPlayers();
        if (entity instanceof net.minecraft.world.entity.monster.Monster) return tracer.showMobs();
        if (entity instanceof net.minecraft.world.entity.animal.Animal) return tracer.showAnimals();
        return false;
    }

    private double[] worldToScreen(Minecraft client, double x, double y, double z) {
        net.minecraft.client.Camera camera = ClientAccess.getMainCamera(client);
        org.joml.Vector3f pos = new org.joml.Vector3f(
                (float) (x - camera.position().x),
                (float) (y - camera.position().y),
                (float) (z - camera.position().z)
        );
        org.joml.Quaternionf rot = camera.rotation().conjugate(new org.joml.Quaternionf());
        pos.rotate(rot);
        if (pos.z() >= 0) return null;
        float fov = client.options.fov().get().intValue();
        float halfHeight = contextHeight(client) / 2f;
        float scale = halfHeight / (float) Math.tan(Math.toRadians(fov / 2.0));
        float sx = contextWidth(client) / 2f + pos.x() * scale / -pos.z();
        float sy = contextHeight(client) / 2f - pos.y() * scale / -pos.z();
        if (!Float.isFinite(sx) || !Float.isFinite(sy)) return null;
        if (sx < 0 || sy < 0 || sx > contextWidth(client) || sy > contextHeight(client)) return null;
        return new double[]{sx, sy};
    }

    private int contextWidth(Minecraft client) {
        return client.getWindow().getGuiScaledWidth();
    }

    private int contextHeight(Minecraft client) {
        return client.getWindow().getGuiScaledHeight();
    }

    private void drawTracerLine(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, color);
        }
    }
    
    private void renderToggleNotification(GuiGraphicsExtractor context, Minecraft client, boolean screenOpen) {
        if (screenOpen || !ToggleNotifier.isVisible()) return;
        String moduleName = ToggleNotifier.getModuleName();
        String state = ToggleNotifier.getState();
        if (moduleName == null || moduleName.isEmpty()) return;

        float alpha = ToggleNotifier.getAlpha();
        int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
        if (a <= 0) {
            return;
        }

        boolean enabled = ToggleNotifier.isEnabledState();
        int accent = enabled ? NavineTheme.PURPLE_BRIGHT : NavineTheme.ROYAL_BLUE_DIM;
        int moduleColor = enabled ? NavineTheme.CYAN : NavineTheme.TEXT_SECONDARY;
        int stateColor = enabled ? NavineTheme.TEXT_PRIMARY : NavineTheme.TEXT_DISABLED;

        String displayName = navine_formatDisplayName(moduleName);
        int nameW = client.font.width(displayName);
        int stateW = client.font.width(state);
        int gap = 6;
        int padX = 10;
        int padY = 5;
        int panelW = nameW + gap + stateW + padX * 2;
        int panelH = 18;
        int panelLeft = (context.guiWidth() - panelW) / 2;
        int panelTop = HUDManager.getNotificationYAboveHotbar(client, context.guiHeight(), panelH) + (int) ToggleNotifier.getSlideOffset();

        RenderUtil.drawRoundedRect(context, panelLeft, panelTop, panelW, panelH, 5, (a / 2 << 24) | (NavineTheme.PANEL_BG & 0x00FFFFFF));
        RenderUtil.drawRoundedOutline(context, panelLeft, panelTop, panelW, panelH, 5, 1, (a << 24) | (accent & 0x00FFFFFF));
        context.fill(panelLeft + 2, panelTop + panelH - 1, panelLeft + panelW - 2, panelTop + panelH, (a << 24) | (NavineTheme.PURPLE & 0x00FFFFFF));

        int textY = panelTop + padY;
        int textX = panelLeft + padX;
        context.text(client.font, displayName, textX, textY, (a << 24) | (moduleColor & 0x00FFFFFF), true);
        context.text(client.font, state, textX + nameW + gap, textY, (a << 24) | (stateColor & 0x00FFFFFF), true);
    }
    
    private void renderWaypoints(GuiGraphicsExtractor context, Minecraft client, Waypoints waypoints) {
        if (client.player == null || client.level == null) return;
        
        Vec3 playerPos = new Vec3(client.player.getX(), client.player.getY() + client.player.getEyeHeight(client.player.getPose()), client.player.getZ());
        Vec3 lookDir = client.player.getForward();
        
        for (Waypoints.Waypoint waypoint : waypoints.getWaypoints()) {
            BlockPos pos = waypoint.pos;
            Vec3 waypointVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3 toWaypoint = waypointVec.subtract(playerPos).normalize();
            
            // Check if player is looking at the waypoint (dot product > 0.7 means within ~45 degrees)
            double dot = lookDir.dot(toWaypoint);
            if (dot < 0.7) continue; // Not looking at it
            
            double distance = playerPos.distanceTo(waypointVec);
            if (distance > 256) continue; // Too far
            
            // Project waypoint to screen coordinates
            // Simple 2D projection based on angle
            double angle = Math.acos(dot);
            double screenX = context.guiWidth() / 2.0;
            double screenY = context.guiHeight() / 2.0 - (angle * 50); // Offset based on angle
            
            String text = waypoint.name + " (" + String.format("%.0f", distance) + "m)";
            int textWidth = client.font.width(text);
            int x = (int) (screenX - textWidth / 2);
            int y = (int) screenY;
            
            // Only render if on screen
            if (y >= 0 && y < context.guiHeight() - 12) {
                context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x80000000);
                context.text(client.font, text, x, y, waypoints.getColor() | 0xFF000000, false);
            }
        }
    }
    
    private void renderRadar(GuiGraphicsExtractor context, Minecraft client, Radar radar) {
        if (client.player == null || client.level == null) return;

        int size = HUDManager.scaleRadarSize((int) radar.getSize(), context.guiWidth(), context.guiHeight());
        int x = HUDManager.HUD_MARGIN;
        int y = HUDManager.HUD_MARGIN;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int accent = 0xFF4169E1;
        int playerColor = 0xFF00FFFF;
        int innerRadius = size / 2 - 6;

        context.fill(x, y, x + size, y + size, 0xD0101018);

        int gridColor = 0x25FFFFFF;
        int gridSpacing = size / 4;
        for (int i = 1; i < 4; i++) {
            int vx = x + i * gridSpacing;
            context.fill(vx, y, vx + 1, y + size, gridColor);
            int hy = y + i * gridSpacing;
            context.fill(x, hy, x + size, hy + 1, gridColor);
        }

        context.fill(centerX, y + 2, centerX + 1, y + size - 2, 0x30FFFFFF);
        context.fill(x + 2, centerY, x + size - 2, centerY + 1, 0x30FFFFFF);

        String[] directions = {"N", "E", "S", "W"};
        int[] compassX = {centerX - 2, x + size - 10, centerX - 2, x + 4};
        int[] compassY = {y + 3, centerY - 3, y + size - 11, centerY - 3};
        for (int i = 0; i < 4; i++) {
            context.text(client.font, directions[i], compassX[i], compassY[i], 0xFFAAAAAA, false);
        }

        context.fill(x, y, x + size, y + 1, accent);
        context.fill(x, y + size - 1, x + size, y + size, accent);
        context.fill(x, y, x + 1, y + size, accent);
        context.fill(x + size - 1, y, x + size, y + size, accent);

        context.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, playerColor);
        context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xFFFFFFFF);

        float yaw = client.player.getYRot();
        double dirX = Math.cos(Math.toRadians(yaw + 90));
        double dirZ = Math.sin(Math.toRadians(yaw + 90));
        int dirEndX = centerX + (int) (dirX * 8);
        int dirEndY = centerY + (int) (dirZ * 8);
        Trajectories.drawLine(context, centerX, centerY, dirEndX, dirEndY, playerColor);

        java.util.List<RadarMarker> markers = new java.util.ArrayList<>();
        for (Player player : radar.getNearbyPlayers()) {
            int[] pos = radarEntityPos(client, player, radar, innerRadius, centerX, centerY);
            if (pos == null) continue;
            markers.add(new RadarMarker(pos[0], pos[1], playerColor, player.getName().getString()));
        }
        for (LivingEntity entity : radar.getNearbyEntities()) {
            int[] pos = radarEntityPos(client, entity, radar, innerRadius, centerX, centerY);
            if (pos == null) continue;
            int color;
            if (entity instanceof net.minecraft.world.entity.monster.Monster) {
                color = 0xFFFF5555;
            } else if (entity instanceof net.minecraft.world.entity.animal.Animal) {
                color = 0xFF55FF55;
            } else {
                color = 0xFFFFAA00;
            }
            markers.add(new RadarMarker(pos[0], pos[1], color, entity.getName().getString()));
        }

        spreadRadarMarkers(markers, x, y, size, 4);
        boolean crowded = markers.size() > radar.getCrowdThreshold();
        int labelsDrawn = 0;
        int maxLabels = crowded ? Math.min(4, radar.getMaxNameLabels()) : radar.getMaxNameLabels();

        for (RadarMarker marker : markers) {
            int dotSize = crowded ? 1 : 2;
            context.fill(marker.x - dotSize - 1, marker.y - dotSize - 1, marker.x + dotSize + 1, marker.y + dotSize + 1, 0xFF000000);
            context.fill(marker.x - dotSize, marker.y - dotSize, marker.x + dotSize, marker.y + dotSize, marker.color);
            if (radar.shouldShowNames() && !crowded && labelsDrawn < maxLabels) {
                drawRadarName(context, client, marker.name, marker.x, marker.y, x, y, size, marker.color);
                labelsDrawn++;
            }
        }
    }

    private record RadarMarker(int x, int y, int color, String name) {
    }

    private void spreadRadarMarkers(java.util.List<RadarMarker> markers, int rx, int ry, int size, int minDistance) {
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < markers.size(); i++) {
                RadarMarker a = markers.get(i);
                for (int j = i + 1; j < markers.size(); j++) {
                    RadarMarker b = markers.get(j);
                    int dx = b.x - a.x;
                    int dy = b.y - a.y;
                    int distSq = dx * dx + dy * dy;
                    if (distSq >= minDistance * minDistance || distSq == 0) {
                        continue;
                    }
                    double dist = Math.sqrt(distSq);
                    double push = (minDistance - dist) / 2.0 + 0.5;
                    int px = (int) Math.round(dx / dist * push);
                    int py = (int) Math.round(dy / dist * push);
                    a = clampRadarMarker(a, rx, ry, size, -px, -py);
                    b = clampRadarMarker(b, rx, ry, size, px, py);
                    markers.set(i, a);
                    markers.set(j, b);
                }
            }
        }
    }

    private RadarMarker clampRadarMarker(RadarMarker marker, int rx, int ry, int size, int dx, int dy) {
        int minX = rx + 3;
        int maxX = rx + size - 4;
        int minY = ry + 3;
        int maxY = ry + size - 4;
        return new RadarMarker(
                Math.max(minX, Math.min(maxX, marker.x + dx)),
                Math.max(minY, Math.min(maxY, marker.y + dy)),
                marker.color,
                marker.name
        );
    }

    private int[] radarEntityPos(Minecraft client, LivingEntity entity, Radar radar, int innerRadius, int centerX, int centerY) {
        double dx = entity.getX() - client.player.getX();
        double dz = entity.getZ() - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > radar.getRange()) return null;

        double angle = Math.atan2(dz, dx) - Math.toRadians(client.player.getYRot() + 90);
        double scale = dist / radar.getRange();
        if (scale > 1.0) {
            scale = 1.0;
        }
        double relX = Math.cos(angle) * scale * innerRadius;
        double relZ = Math.sin(angle) * scale * innerRadius;

        int px = centerX + (int) relX;
        int pz = centerY + (int) relZ;
        return new int[]{px, pz};
    }

    private void drawRadarName(GuiGraphicsExtractor context, Minecraft client, String name, int px, int pz, int rx, int ry, int size, int color) {
        int nameW = client.font.width(name);
        int nameX = Math.max(rx + 2, Math.min(px - nameW / 2, rx + size - nameW - 2));
        int nameY = Math.max(ry + 2, pz - 10);
        if (nameY >= ry + size - 10) return;
        context.fill(nameX - 2, nameY - 1, nameX + nameW + 2, nameY + 9, 0xC0000000);
        context.text(client.font, name, nameX, nameY, color, false);
    }
    
    private void renderBreadcrumbs(GuiGraphicsExtractor context, Minecraft client, Breadcrumbs breadcrumbs) {
        if (client.player == null) return;
        java.util.List<Vec3> points = breadcrumbs.getPositions();
        if (points.size() < 2) return;
        int color = breadcrumbs.getColor();
        float[] prev = new float[2];
        boolean hadPrev = false;
        for (Vec3 point : points) {
            float[] screen = new float[2];
            if (!Trajectories.projectToScreen(client, point.add(0, 0.1, 0), screen)) {
                hadPrev = false;
                continue;
            }
            int x = (int) screen[0];
            int y = (int) screen[1];
            if (!isOnScreen(context, x, y)) {
                hadPrev = false;
                continue;
            }
            context.fill(x - 1, y - 1, x + 2, y + 2, color);
            if (hadPrev) {
                Trajectories.drawLine(context, (int) prev[0], (int) prev[1], x, y, color);
            }
            prev[0] = screen[0];
            prev[1] = screen[1];
            hadPrev = true;
        }
    }

    private void renderStorageESP(GuiGraphicsExtractor context, Minecraft client, StorageESP storageESP) {
        if (client.player == null || client.level == null) return;
        double range = storageESP.getRange();
        int color = storageESP.getColor();
        for (net.minecraft.world.level.block.entity.BlockEntity be : storageESP.getNearbyStorage()) {
            Vec3 center = Vec3.atCenterOf(be.getBlockPos());
            if (client.player.position().distanceTo(center) > range) continue;
            float[] screen = new float[2];
            if (!Trajectories.projectToScreen(client, center, screen)) continue;
            int x = (int) screen[0];
            int y = (int) screen[1];
            if (!isOnScreen(context, x, y)) continue;
            String label = formatBlockEntityLabel(be);
            int w = client.font.width(label);
            context.fill(x - w / 2 - 2, y - 2, x + w / 2 + 2, y + 10, 0x90000000);
            context.text(client.font, label, x - w / 2, y, color, false);
        }
    }

    private void renderNametags(GuiGraphicsExtractor context, Minecraft client, Nametags nametags) {
        if (client.level == null || client.player == null) {
            return;
        }
        for (Player player : client.level.players()) {
            if (player == client.player || !player.isAlive()) {
                continue;
            }
            Vec3 pos = player.getEyePosition(1.0f).add(0.0, 0.55, 0.0);
            float[] screen = new float[2];
            if (!Trajectories.projectToScreen(client, pos, screen)) {
                continue;
            }
            String text = nametags.getNametagText(player);
            float distance = (float) client.player.distanceTo(player);
            float distanceScale = Math.max(0.75f, Math.min(1.35f, 12f / Math.max(distance, 4f)));
            float renderScale = nametags.getScale() * distanceScale;
            int textWidth = client.font.width(text);
            int scaledWidth = Math.max(1, Math.round(textWidth * renderScale));
            int scaledHeight = Math.max(1, Math.round(10 * renderScale));
            int x = (int) screen[0] - scaledWidth / 2;
            int y = (int) screen[1] - scaledHeight - 2;
            if (x + scaledWidth < 0 || y + scaledHeight < 0 || x > context.guiWidth() || y > context.guiHeight()) {
                continue;
            }
            int padX = Math.max(2, Math.round(3 * renderScale));
            int padY = Math.max(1, Math.round(2 * renderScale));
            if (nametags.drawBackground()) {
                context.fill(x - padX, y - padY, x + scaledWidth + padX, y + scaledHeight + padY, 0xC0101018);
                context.fill(x - padX, y - padY, x + scaledWidth + padX, y - padY + 1, 0xFF9D4EDD);
            }
            if (renderScale != 1.0f) {
                context.pose().pushMatrix();
                context.pose().translate(x, y);
                context.pose().scale(renderScale, renderScale);
                context.text(client.font, text, 0, 0, 0xFFFFFFFF, true);
                context.pose().popMatrix();
            } else {
                context.text(client.font, text, x, y, 0xFFFFFFFF, true);
            }
        }
    }
    
    private void renderTargetHUD(GuiGraphicsExtractor context, Minecraft client, TargetHUD targetHUD) {
        LivingEntity target = targetHUD.getTarget();
        if (target == null) return;

        int panelWidth = 124;
        int panelHeight = target instanceof Player ? 58 : 46;
        Radar radar = (Radar) ModuleManager.getModuleByName("Radar");
        int radarSize = radar != null && radar.isEnabled()
            ? HUDManager.scaleRadarSize((int) radar.getSize(), context.guiWidth(), context.guiHeight())
            : 0;
        int[] pos = HUDManager.resolveTargetHudPosition(
            client,
            context.guiWidth(),
            context.guiHeight(),
            targetHUD.getX(),
            targetHUD.getY(),
            panelWidth,
            panelHeight,
            radarSize,
            targetHUD.autoPosition()
        );
        int x = pos[0];
        int y = pos[1];
        
        String name = target.getName().getString();
        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        double distance = client.player != null ? client.player.distanceTo(target) : 0;
        
        context.fill(x - 2, y - 2, x + panelWidth, y + panelHeight, 0x80000000);
        context.text(client.font, name, x, y, -1, false);
        
        if (targetHUD.showHealth()) {
            float healthPercent = health / maxHealth;
            int barWidth = 100;
            int barHeight = 8;
            context.fill(x, y + 12, x + barWidth, y + 12 + barHeight, 0xFF000000);
            context.fill(x, y + 12, x + (int)(barWidth * healthPercent), y + 12 + barHeight, 0xFFFF0000);
            context.text(client.font, String.format("%.1f/%.1f", health, maxHealth), x, y + 22, -1, false);
        }
        
        context.text(client.font, String.format("Distance: %.1fm", distance), x, y + 34, -1, false);
        if (target instanceof Player) {
            int armor = ((Player)target).getArmorValue();
            context.text(client.font, String.format("Armor: %d", armor), x, y + 46, 0xFF00FFFF, false);
        }
    }
    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderOverlay(GuiGraphicsExtractor context, net.minecraft.resources.Identifier texture, float opacity, CallbackInfo ci) {
        NoRender noRender = (NoRender) ModuleManager.getModuleByName("NoRender");
        if (noRender != null && noRender.isEnabled()) {
            if (texture.toString().contains("fire") && noRender.hidesFire()) {
                ci.cancel();
            } else if (texture.toString().contains("water") && noRender.hidesWater()) {
                ci.cancel();
            } else if (texture.toString().contains("pumpkin") && noRender.hidesPumpkin()) {
                ci.cancel();
            }
        }
        
        if (NoFire.shouldHideFire() && texture.toString().contains("fire")) {
            ci.cancel();
        }
    }

    private String navine_formatPing(Minecraft client) {
        if (!PingUtil.isMultiplayer(client)) {
            return "§aPing: §7N/A";
        }
        int ping = PingUtil.getPingMs(client);
        if (ping < 0) {
            return "§aPing: §7...";
        }
        return "§aPing: " + PingUtil.formatPingColor(ping) + ping + "ms";
    }

    private String navine_formatName(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.indexOf(':');
        if (idx >= 0 && idx < name.length() - 1) {
            name = name.substring(idx + 1);
        }
        name = name.replace('_', ' ').trim();
        if (name.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        for (String word : name.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            formatted.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                formatted.append(word.substring(1));
            }
        }
        return formatted.toString();
    }

    private String navine_formatDisplayName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return navine_formatName(name.replace('-', ' '));
    }

    private void navine_renderModuleArrayList(GuiGraphicsExtractor context, Minecraft client,
                                              int scaledWidth, int scaledHeight, int bottomOffset) {
        java.util.List<String> enabled = new java.util.ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module instanceof HUD) {
                continue;
            }
            if (module.isEnabled() && ModuleManager.isModuleAccessible(module)) {
                enabled.add(module.getName());
            }
        }
        if (enabled.isEmpty()) {
            return;
        }
        enabled.sort(String.CASE_INSENSITIVE_ORDER);
        int maxW = 0;
        for (String name : enabled) {
            maxW = Math.max(maxW, client.font.width(name));
        }
        int width = maxW + HUDManager.PANEL_PADDING * 2;
        int height = enabled.size() * HUDManager.LINE_HEIGHT + HUDManager.PANEL_PADDING;
        int x = scaledWidth - width - HUDManager.HUD_MARGIN;
        int y = HUDManager.HUD_MARGIN;
        BackgroundManager.renderPanel(context, x - 2, y - 2, width + 4, height + 4);
        int lineY = y + HUDManager.PANEL_PADDING;
        for (String name : enabled) {
            context.text(client.font, name, x + HUDManager.PANEL_PADDING, lineY, NavineTheme.accent(), false);
            lineY += HUDManager.LINE_HEIGHT;
        }
    }

    private static String formatBlockEntityLabel(net.minecraft.world.level.block.entity.BlockEntity be) {
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (id == null) {
            return "Unknown";
        }
        String label = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.getPath();
        return label.replace('_', ' ');
    }
}
