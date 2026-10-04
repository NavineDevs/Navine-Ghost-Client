package nv.navineclient.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.util.Prediction;

public final class ClientAccess {
    private ClientAccess() {
    }

    public static Screen getScreen() {
        return ClientScreens.get(Minecraft.getInstance());
    }

    public static Screen getScreen(Minecraft mc) {
        return ClientScreens.get(mc);
    }

    public static void setScreen(Screen screen) {
        ClientScreens.set(Minecraft.getInstance(), screen);
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        ClientScreens.set(mc, screen);
    }

    public static ChatComponent getChat(Minecraft mc) {
        if (mc == null || mc.gui == null || mc.gui.hud == null) {
            return null;
        }
        return mc.gui.hud.getChat();
    }

    public static DebugScreenOverlay getDebugOverlay(Minecraft mc) {
        if (mc == null || mc.gui == null || mc.gui.hud == null) {
            return null;
        }
        return mc.gui.hud.getDebugOverlay();
    }

    public static int getGuiTicks(Minecraft mc) {
        if (mc == null || mc.gui == null || mc.gui.hud == null) {
            return 0;
        }
        return mc.gui.hud.getGuiTicks();
    }

    public static void extractDeferredSubtitles(Minecraft mc) {
        if (mc == null || mc.gui == null || mc.gui.hud == null) {
            return;
        }
        mc.gui.hud.extractDeferredSubtitles();
    }

    public static Camera getMainCamera(Minecraft mc) {
        if (mc == null || mc.gameRenderer == null) {
            return null;
        }
        return mc.gameRenderer.mainCamera();
    }

    public static RenderTarget getMainRenderTarget(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        try {
            return mc.gameRenderer.mainRenderTarget();
        } catch (Throwable ignored) {
        }
        try {
            return (RenderTarget) Minecraft.class.getMethod("getMainRenderTarget").invoke(mc);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void swing(LivingEntity entity, InteractionHand hand) {
        if (entity == null) {
            return;
        }
        entity.swing(hand, SwingAnimation.DEFAULT, false);
    }

    public static void drop(LivingEntity entity, ItemStack stack, boolean thrownFromHand) {
        if (entity == null) {
            return;
        }
        entity.drop(stack, thrownFromHand, Prediction.PREDICTED);
    }

    public static void markVelocityUpdate(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.hurtTime = Math.max(entity.hurtTime, 1);
    }
}
