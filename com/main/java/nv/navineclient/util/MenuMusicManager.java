package nv.navineclient.util;

import nv.navineclient.util.ClientScreens;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;
import nv.navineclient.NavineClient;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MenuMusicManager {
    private static final String REMOTE_URL =
            "https://hitboyxx23-dev.github.io/songs-for-websites/darkbeats/SpotiDownloader.com%20-%20lost%20you%20-%20undercurrent..mp3";

    private static final AtomicBoolean loadStarted = new AtomicBoolean(false);
    private static volatile File cachedMusicFile;
    private static volatile AdvancedPlayer player;
    private static volatile Thread playerThread;
    private static volatile boolean shouldPlay;
    private static volatile boolean onTitleScreen;
    private static volatile long lastLoadAttempt;

    private MenuMusicManager() {
    }

    public static void ensureLoaded() {
        scheduleLoad(false);
    }

    private static void scheduleLoad(boolean force) {
        if (!force && loadStarted.get()) {
            return;
        }
        if (force) {
            loadStarted.set(false);
        }
        if (loadStarted.getAndSet(true)) {
            return;
        }
        lastLoadAttempt = System.currentTimeMillis();
        Thread loader = new Thread(MenuMusicManager::loadMusicFile, "Navine-MenuMusic-Loader");
        loader.setDaemon(true);
        loader.start();
    }

    public static void onTitleScreenOpen() {
        onTitleScreen = true;
        shouldPlay = true;
        ensureLoaded();
        startPlaybackLoop();
    }

    public static void onTitleScreenClose() {
        onTitleScreen = false;
        shouldPlay = false;
        stopPlayback();
    }

    public static boolean shouldSuppressVanillaMusic() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level != null) {
            return false;
        }
        Screen screen = ClientScreens.get(mc);
        return screen != null && ScreenBackgroundHelper.isMenuMusicScreen(screen);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Screen screen = ClientScreens.get(mc);
        if (shouldPlay && (screen == null || !ScreenBackgroundHelper.isMenuMusicScreen(screen))) {
            onTitleScreenClose();
            return;
        }
        if (!shouldPlay && screen != null && ScreenBackgroundHelper.isMenuMusicScreen(screen)) {
            onTitleScreenOpen();
        }
        if (!onTitleScreen || !shouldPlay) {
            return;
        }
        suppressVanillaMusic(mc);
        if (cachedMusicFile == null || !cachedMusicFile.exists()) {
            if (System.currentTimeMillis() - lastLoadAttempt > 10000) {
                scheduleLoad(true);
            }
            return;
        }
        if (playerThread == null || !playerThread.isAlive()) {
            startPlaybackLoop();
        }
        updateVolume();
    }

    private static void loadMusicFile() {
        try {
            if (tryLoadLocal()) {
                return;
            }
            tryLoadRemote();
            if (cachedMusicFile == null) {
                NavineClient.LOGGER.warn("Menu music download failed; place navine/menu_music.mp3 in your game directory.");
            }
        } catch (Exception error) {
            NavineClient.LOGGER.warn("Menu music load failed: {}", error.toString());
        } finally {
            if (cachedMusicFile == null) {
                loadStarted.set(false);
            }
        }
    }

    private static boolean tryLoadLocal() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return false;
            }
            File file = new File(mc.gameDirectory, "navine/menu_music.mp3");
            if (file.exists() && file.length() > 1000) {
                cachedMusicFile = file;
                return true;
            }
        } catch (Exception error) {
            NavineClient.LOGGER.warn("Menu music local load failed: {}", error.toString());
        }
        return false;
    }

    private static void tryLoadRemote() {
        try {
            URI uri = new URI(REMOTE_URL);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 Navine-Client/1.6");
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                NavineClient.LOGGER.warn("Menu music HTTP status: {}", conn.getResponseCode());
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = conn.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
            conn.disconnect();

            byte[] data = baos.toByteArray();
            if (data.length < 1000) {
                NavineClient.LOGGER.warn("Menu music download too small ({} bytes)", data.length);
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            File dir = new File(mc.gameDirectory, "navine");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, "menu_music.mp3");
            java.nio.file.Files.write(out.toPath(), data);
            cachedMusicFile = out;
        } catch (Exception error) {
            NavineClient.LOGGER.warn("Menu music remote load failed: {}", error.toString());
        }
    }

    private static void startPlaybackLoop() {
        if (!shouldPlay || cachedMusicFile == null || !cachedMusicFile.exists()) {
            return;
        }
        if (playerThread != null && playerThread.isAlive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        suppressVanillaMusic(mc);
        if (mc != null && mc.getMusicManager() != null) {
            mc.getMusicManager().stopPlaying();
        }

        playerThread = new Thread(() -> {
            while (shouldPlay && onTitleScreen) {
                if (cachedMusicFile == null || !cachedMusicFile.exists()) {
                    break;
                }
                try (FileInputStream fis = new FileInputStream(cachedMusicFile);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    player = new AdvancedPlayer(bis);
                    player.setPlayBackListener(new PlaybackListener() {
                        @Override
                        public void playbackFinished(PlaybackEvent event) {
                        }
                    });
                    updateVolume();
                    player.play();
                } catch (Exception error) {
                    NavineClient.LOGGER.warn("Menu music playback failed: {}", error.toString());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } finally {
                    player = null;
                }
                if (!shouldPlay || !onTitleScreen) {
                    break;
                }
            }
        }, "Navine-MenuMusic-Player");
        playerThread.setDaemon(true);
        playerThread.start();
    }

    private static void stopPlayback() {
        shouldPlay = false;
        AdvancedPlayer active = player;
        if (active != null) {
            active.stop();
        }
        player = null;
        Thread thread = playerThread;
        if (thread != null) {
            thread.interrupt();
        }
        playerThread = null;
    }

    private static void updateVolume() {
        AdvancedPlayer active = player;
        if (active == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        try {
            float music = (float) mc.options.getSoundSourceVolume(SoundSource.MUSIC);
            float master = (float) mc.options.getSoundSourceVolume(SoundSource.MASTER);
            float linear = Math.max(0f, Math.min(1f, music * master));

            Field audioField = AdvancedPlayer.class.getDeclaredField("audio");
            audioField.setAccessible(true);
            Object audio = audioField.get(active);
            if (!(audio instanceof JavaSoundAudioDevice device)) {
                return;
            }
            Field sourceField = JavaSoundAudioDevice.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            SourceDataLine source = (SourceDataLine) sourceField.get(device);
            if (source == null || !source.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                return;
            }
            FloatControl vol = (FloatControl) source.getControl(FloatControl.Type.MASTER_GAIN);
            float gain = vol.getMinimum() + (vol.getMaximum() - vol.getMinimum()) * linear;
            vol.setValue(Math.max(vol.getMinimum(), Math.min(vol.getMaximum(), gain)));
        } catch (Exception error) {
            NavineClient.LOGGER.debug("Menu music volume update failed: {}", error.toString());
        }
    }

    private static void suppressVanillaMusic(Minecraft mc) {
        if (mc != null && mc.getMusicManager() != null) {
            mc.getMusicManager().stopPlaying();
        }
    }
}
