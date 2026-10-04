package nv.navineclient.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class RenderUtil {

    public static void drawRoundedRect(GuiGraphicsExtractor context, float x, float y, float width, float height, float radius, int color) {
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) width;
        int ih = (int) height;
        int ir = (int) Math.min(radius, Math.min(iw / 2, ih / 2));
        
        // Main rectangle
        context.fill(ix + ir, iy, ix + iw - ir, iy + ih, color);
        context.fill(ix, iy + ir, ix + ir, iy + ih - ir, color);
        context.fill(ix + iw - ir, iy + ir, ix + iw, iy + ih - ir, color);
        
        // Rounded corners (approximated with small rectangles)
        for (int i = 0; i < ir; i++) {
            int cornerSize = ir - i;
            // Top-left
            context.fill(ix, iy + i, ix + cornerSize, iy + i + 1, color);
            // Top-right
            context.fill(ix + iw - cornerSize, iy + i, ix + iw, iy + i + 1, color);
            // Bottom-left
            context.fill(ix, iy + ih - i - 1, ix + cornerSize, iy + ih - i, color);
            // Bottom-right
            context.fill(ix + iw - cornerSize, iy + ih - i - 1, ix + iw, iy + ih - i, color);
        }
    }
    
    public static void drawRoundedOutline(GuiGraphicsExtractor context, float x, float y, float width, float height, float radius, float thickness, int color) {
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) width;
        int ih = (int) height;
        int ir = (int) Math.min(radius, Math.min(iw / 2, ih / 2));
        int it = (int) thickness;
        
        // Top
        context.fill(ix + ir, iy, ix + iw - ir, iy + it, color);
        // Bottom
        context.fill(ix + ir, iy + ih - it, ix + iw - ir, iy + ih, color);
        // Left
        context.fill(ix, iy + ir, ix + it, iy + ih - ir, color);
        // Right
        context.fill(ix + iw - it, iy + ir, ix + iw, iy + ih - ir, color);
        
        // Rounded corners outline
        for (int i = 0; i < ir; i++) {
            int cornerSize = ir - i;
            // Top-left
            if (i < it) context.fill(ix, iy + i, ix + cornerSize, iy + i + 1, color);
            // Top-right
            if (i < it) context.fill(ix + iw - cornerSize, iy + i, ix + iw, iy + i + 1, color);
            // Bottom-left
            if (i < it) context.fill(ix, iy + ih - i - 1, ix + cornerSize, iy + ih - i, color);
            // Bottom-right
            if (i < it) context.fill(ix + iw - cornerSize, iy + ih - i - 1, ix + iw, iy + ih - i, color);
        }
    }

    public static void drawGradientRect(GuiGraphicsExtractor context, int x, int y, int x2, int y2, int color1, int color2) {
        context.fillGradient(x, y, x2, y2, color1, color2);
    }

    public static void drawOutline(GuiGraphicsExtractor context, float x, float y, float width, float height, float thickness, int color) {
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) width;
        int ih = (int) height;
        int it = (int) thickness;
        
        context.fill(ix, iy, ix + iw, iy + it, color);
        context.fill(ix, iy + ih - it, ix + iw, iy + ih, color);
        context.fill(ix, iy, ix + it, iy + ih, color);
        context.fill(ix + iw - it, iy, ix + iw, iy + ih, color);
    }
}
