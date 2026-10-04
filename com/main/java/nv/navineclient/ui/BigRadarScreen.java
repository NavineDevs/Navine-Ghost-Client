package nv.navineclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.render.Radar;
import nv.navineclient.module.render.Trajectories;
import nv.navineclient.util.ScreenBackgroundHelper;
import nv.navineclient.util.NavineInput;

public class BigRadarScreen extends Screen {
    private static final int ACCENT = 0xFF4169E1;
    private static final int NAVINE_CYAN = 0xFF00FFFF;
    
    private Radar radar;
    private int mapSize = 400;
    private int centerX, centerY;
    private double scale = 1.0;
    
    public BigRadarScreen() {
        super(Component.literal("Big Radar"));
        radar = (Radar) ModuleManager.getModuleByName("Radar");
    }
    
    @Override
    protected void init() {
        if (radar == null || !radar.isEnabled() || !radar.isBigRadarEnabled()) {
            this.onClose();
            return;
        }
        
        centerX = width / 2;
        centerY = height / 2;
        
        // Close button
        addRenderableWidget(new CoolButton(width / 2 - 50, height - 30, 100, 20,
            Component.literal("Close"), button -> this.onClose(), true));
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft client = this.minecraft;
        if (client == null || client.player == null || client.level == null || radar == null) {
            this.onClose();
            return;
        }
        
        // Dark background
        ScreenBackgroundHelper.renderNavineBackground(context, width, height);
        context.fill(0, 0, width, height, 0x90000000);
        
        // Draw radar map
        int mapX = centerX - mapSize / 2;
        int mapY = centerY - mapSize / 2;
        
        // Map background
        context.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xC0101010);
        
        // Grid
        int gridSpacing = mapSize / 20;
        int gridColor = 0x40FFFFFF;
        for (int i = 1; i < 20; i++) {
            context.fill(mapX + i * gridSpacing, mapY, mapX + i * gridSpacing + 1, mapY + mapSize, gridColor);
            context.fill(mapX, mapY + i * gridSpacing, mapX + mapSize, mapY + i * gridSpacing + 1, gridColor);
        }
        
        // Center crosshair
        int centerMapX = mapX + mapSize / 2;
        int centerMapY = mapY + mapSize / 2;
        context.fill(centerMapX - 1, mapY, centerMapX + 1, mapY + mapSize, 0x60FFFFFF);
        context.fill(mapX, centerMapY - 1, mapX + mapSize, centerMapY + 1, 0x60FFFFFF);
        
        // Border with Navine colors
        int borderThickness = 3;
        context.fill(mapX, mapY, mapX + mapSize, mapY + borderThickness, ACCENT);
        context.fill(mapX, mapY + mapSize - borderThickness, mapX + mapSize, mapY + mapSize, ACCENT);
        context.fill(mapX, mapY, mapX + borderThickness, mapY + mapSize, ACCENT);
        context.fill(mapX + mapSize - borderThickness, mapY, mapX + mapSize, mapY + mapSize, ACCENT);

        context.fill(centerMapX - 2, centerMapY - 2, centerMapX + 2, centerMapY + 2, NAVINE_CYAN);
        context.fill(centerMapX - 1, centerMapY - 1, centerMapX + 1, centerMapY + 1, 0xFFFFFFFF);
        
        // Direction indicator
        float yaw = client.player.getYRot();
        double dirX = Math.cos(Math.toRadians(yaw + 90));
        double dirZ = Math.sin(Math.toRadians(yaw + 90));
        int dirEndX = centerMapX + (int)(dirX * 15);
        int dirEndY = centerMapY + (int)(dirZ * 15);
        Trajectories.drawLine(context, centerMapX, centerMapY, dirEndX, dirEndY, NAVINE_CYAN);

        String[] directions = {"N", "E", "S", "W"};
        int[] compassX = {centerMapX - 3, mapX + mapSize - 12, centerMapX - 3, mapX + 6};
        int[] compassY = {mapY + 6, centerMapY - 4, mapY + mapSize - 14, centerMapY - 4};
        for (int i = 0; i < 4; i++) {
            context.text(font, directions[i], compassX[i], compassY[i], NAVINE_CYAN, true);
        }
        
        // Draw entities
        double range = radar.getRange();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == client.player) continue;
            
            double dx = entity.getX() - client.player.getX();
            double dz = entity.getZ() - client.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist > range) continue;
            
            double angle = Math.atan2(dz, dx) - Math.toRadians(client.player.getYRot() + 90);
            double relX = Math.cos(angle) * (dist / range) * (mapSize / 2 - 10);
            double relZ = Math.sin(angle) * (dist / range) * (mapSize / 2 - 10);
            
            int ex = centerMapX + (int) relX;
            int ey = centerMapY + (int) relZ;
            
            if (ex >= mapX + 5 && ex < mapX + mapSize - 5 && ey >= mapY + 5 && ey < mapY + mapSize - 5) {
                int headSize;
                int color;
                if (entity instanceof Player) {
                    color = NAVINE_CYAN;
                    headSize = 6;
                    // Draw player head
                    context.fill(ex - headSize - 1, ey - headSize - 1, ex + headSize + 1, ey + headSize + 1, 0xFF000000);
                    context.fill(ex - headSize, ey - headSize, ex + headSize, ey + headSize, color);
                    context.fill(ex - headSize + 1, ey - headSize + 1, ex + headSize - 1, ey + headSize - 1, 0xFF00CCCC);
                    // Eyes
                    context.fill(ex - 2, ey - 1, ex - 1, ey, 0xFF000000);
                    context.fill(ex + 1, ey - 1, ex + 2, ey, 0xFF000000);
                } else if (entity instanceof Monster) {
                    color = 0xFFFF0000;
                    headSize = 5;
                    context.fill(ex - headSize - 1, ey - headSize - 1, ex + headSize + 1, ey + headSize + 1, 0xFF000000);
                    context.fill(ex - headSize, ey - headSize, ex + headSize, ey + headSize, color);
                    context.fill(ex - headSize + 1, ey - headSize + 1, ex + headSize - 1, ey + headSize - 1, color | 0xCC000000);
                    // Eyes
                    context.fill(ex - 1, ey - 1, ex, ey, 0xFF000000);
                    context.fill(ex, ey - 1, ex + 1, ey, 0xFF000000);
                } else if (entity instanceof Animal) {
                    color = 0xFF00FF00;
                    headSize = 5;
                    context.fill(ex - headSize - 1, ey - headSize - 1, ex + headSize + 1, ey + headSize + 1, 0xFF000000);
                    context.fill(ex - headSize, ey - headSize, ex + headSize, ey + headSize, color);
                    context.fill(ex - headSize + 1, ey - headSize + 1, ex + headSize - 1, ey + headSize - 1, color | 0xCC000000);
                    // Eyes
                    context.fill(ex - 1, ey - 1, ex, ey, 0xFF000000);
                    context.fill(ex, ey - 1, ex + 1, ey, 0xFF000000);
                } else {
                    continue;
                }
            }
        }
        
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
        int keyCode = keyInput.key();
        if (keyCode == NavineInput.KEY_M || keyCode == NavineInput.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyInput);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
