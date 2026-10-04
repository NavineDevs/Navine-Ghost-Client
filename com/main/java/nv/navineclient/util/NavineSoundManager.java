package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class NavineSoundManager {
    private static long lastHoverAt;

    private NavineSoundManager() {
    }

    public static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) {
            return;
        }
        float master = getMasterVolume(mc);
        if (master <= 0.01f) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05f));
    }

    public static void playHover() {
        long now = System.currentTimeMillis();
        if (now - lastHoverAt < 55) {
            return;
        }
        lastHoverAt = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) {
            return;
        }
        float master = getMasterVolume(mc);
        if (master <= 0.01f) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, 1.6f));
    }

    private static float getMasterVolume(Minecraft mc) {
        if (mc.options == null) {
            return 1.0f;
        }
        return (float) mc.options.getSoundSourceVolume(SoundSource.MASTER);
    }
}
