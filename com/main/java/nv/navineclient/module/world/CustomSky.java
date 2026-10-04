package nv.navineclient.module.world;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.*;
import nv.navineclient.util.ChatUtils;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class CustomSky extends Module {
    public static CustomSky INSTANCE;

    private static final String REMOTE_SKY_URL = "https://kurohana-dev.github.io/images/background.png";
    private static final Identifier BUNDLED_SKY = Identifier.fromNamespaceAndPath("navine-client", "textures/sky.png");

    private final ModeSetting mode = new ModeSetting("Mode", "Sky color mode", "Solid", "Solid", "Gradient", "Rainbow", "Image");
    private final ColorSetting skyColor = new ColorSetting("Color", "Main sky color", 0xFF87CEEB);
    private final NumberSetting red = new NumberSetting("Red", "Red component", 135.0, 0.0, 255.0);
    private final NumberSetting green = new NumberSetting("Green", "Green component", 206.0, 0.0, 255.0);
    private final NumberSetting blue = new NumberSetting("Blue", "Blue component", 235.0, 0.0, 255.0);
    private final StringSetting imagePath = new StringSetting("Image Path", "Custom sky image path (relative to .minecraft/navine/)", "sky.png");
    private final BooleanSetting disableFog = new BooleanSetting("No Fog", "Disable fog rendering", true);
    private final BooleanSetting disableVoid = new BooleanSetting("No Void", "Disable void sky", true);

    private Identifier customSkyTexture = null;
    private NativeImage skyImage = null;
    private String loadedImagePath = "";
    private String lastImageSetting = "";
    private int imageAverageColor = 0xFF87CEEB;

    public CustomSky() {
        super("CustomSky", "Customize sky color with solid, gradient, rainbow, or custom image", Category.WORLD);
        INSTANCE = this;
        addSetting(mode);
        addSetting(skyColor);
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(imagePath);
        addSetting(disableFog);
        addSetting(disableVoid);
    }

    @Override
    public void onEnable() {
        if (mode.getValue().equals("Image")) {
            loadCustomImage();
        }
    }

    @Override
    public void onTick() {
        String currentPath = imagePath.getValue();
        if (mode.getValue().equals("Image")) {
            if (!currentPath.equals(lastImageSetting)) {
                lastImageSetting = currentPath;
                reloadImage();
            } else if (customSkyTexture == null) {
                loadCustomImage();
            }
        }
    }

    public void loadCustomImage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        File navineDir = new File(mc.gameDirectory, "navine");
        if (!navineDir.exists()) {
            navineDir.mkdirs();
        }

        String imgPath = imagePath.getValue();
        File skyFile = new File(navineDir, imgPath);

        if (skyFile.exists() && skyFile.isFile()) {
            applyImageFile(skyFile, imgPath);
            return;
        }

        if (tryLoadBundled(mc, imgPath)) {
            return;
        }

        if (tryLoadRemote(navineDir, imgPath)) {
            return;
        }

        File[] imageFiles = navineDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });

        if (imageFiles != null && imageFiles.length > 0) {
            applyImageFile(imageFiles[0], imageFiles[0].getName());
            ChatUtils.message("§eImage '" + imgPath + "' not found, using: " + imageFiles[0].getName());
            return;
        }

        ChatUtils.message("§cNo sky image found. Bundled, remote, and local navine/ images failed.");
        ChatUtils.message("§7Place sky.png in .minecraft/navine/ or use Image mode default.");
    }

    private boolean tryLoadBundled(Minecraft mc, String imgPath) {
        if (!"sky.png".equalsIgnoreCase(imgPath) && !imgPath.isEmpty()) {
            return false;
        }
        try (InputStream stream = mc.getResourceManager().open(BUNDLED_SKY)) {
            NativeImage image = NativeImage.read(stream);
            registerImage(image, "bundled:" + BUNDLED_SKY, imgPath);
            ChatUtils.message("§aLoaded bundled sky image.");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryLoadRemote(File navineDir, String imgPath) {
        try {
            URI uri = new URI(REMOTE_SKY_URL);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 Navine-Client/1.6");
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return false;
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
                return false;
            }

            File out = new File(navineDir, imgPath.isEmpty() ? "sky.png" : imgPath);
            java.nio.file.Files.write(out.toPath(), data);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                NativeImage image = NativeImage.read(bais);
                registerImage(image, out.getAbsolutePath(), imgPath);
            }
            ChatUtils.message("§aDownloaded sky image to navine/" + out.getName());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void applyImageFile(File skyFile, String settingPath) {
        if (skyFile.getAbsolutePath().equals(loadedImagePath)) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(skyFile)) {
            NativeImage image = NativeImage.read(fis);
            registerImage(image, skyFile.getAbsolutePath(), settingPath);
            ChatUtils.message("§aLoaded sky image: §f" + skyFile.getName());
        } catch (Exception e) {
            ChatUtils.message("§cFailed to load sky image: " + e.getMessage());
        }
    }

    private void registerImage(NativeImage image, String sourceKey, String settingPath) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            image.close();
            return;
        }

        if (customSkyTexture != null) {
            mc.getTextureManager().release(customSkyTexture);
        }
        if (skyImage != null) {
            skyImage.close();
        }

        NativeImage owned = copyImage(image);
        imageAverageColor = computeAverageColor(owned);
        loadedImagePath = sourceKey;
        lastImageSetting = settingPath;
        skyImage = owned;
        customSkyTexture = Identifier.fromNamespaceAndPath("navine-client", "custom_sky");

        Runnable register = () -> {
            try {
                DynamicTexture texture = new DynamicTexture(() -> "navine_custom_sky", owned);
                mc.getTextureManager().register(customSkyTexture, texture);
            } catch (Exception e) {
                ChatUtils.message("§cFailed to register sky texture: " + e.getMessage());
            }
        };

        if (mc.isSameThread()) {
            register.run();
        } else {
            mc.execute(register);
        }
    }

    private NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                copy.setPixel(x, y, source.getPixel(x, y));
            }
        }
        return copy;
    }

    public void reloadImage() {
        loadedImagePath = "";
        customSkyTexture = null;
        if (skyImage != null) {
            skyImage.close();
            skyImage = null;
        }
        loadCustomImage();
    }

    public int getImageAverageColor() {
        return imageAverageColor;
    }

    public NativeImage getSkyImage() {
        return skyImage;
    }

    public int getSkyColor() {
        String m = mode.getValue();
        if (m.equals("Image")) {
            return imageAverageColor;
        }
        if (m.equals("Solid")) {
            return skyColor.getValue();
        } else if (m.equals("Rainbow")) {
            long time = System.currentTimeMillis();
            float hue = (time % 5000) / 5000f;
            return java.awt.Color.HSBtoRGB(hue, 0.7f, 0.9f);
        } else if (m.equals("Gradient")) {
            long time = System.currentTimeMillis();
            float phase = (time % 10000) / 10000f;
            int r = (int) red.getValue().doubleValue();
            int g = (int) green.getValue().doubleValue();
            int b = (int) blue.getValue().doubleValue();
            float factor = (float) Math.sin(phase * Math.PI * 2) * 0.5f + 0.5f;
            r = (int) (r * factor + (255 - r) * (1 - factor));
            g = (int) (g * factor + (255 - g) * (1 - factor));
            b = (int) (b * factor + (255 - b) * (1 - factor));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return skyColor.getValue();
    }

    public boolean isImageMode() {
        return mode.getValue().equals("Image");
    }

    public Identifier getCustomSkyTexture() {
        if (customSkyTexture == null) {
            loadCustomImage();
        }
        return customSkyTexture;
    }

    public boolean shouldDisableFog() {
        return disableFog.getValue();
    }

    public boolean shouldDisableVoid() {
        return disableVoid.getValue();
    }

    @Override
    public boolean isSettingVisible(Setting<?> setting) {
        String m = mode.getValue();
        if (setting == skyColor) {
            return m.equals("Solid");
        }
        if (setting == red || setting == green || setting == blue) {
            return m.equals("Gradient");
        }
        if (setting == imagePath) {
            return m.equals("Image");
        }
        return true;
    }

    private int computeAverageColor(NativeImage image) {
        long r = 0, g = 0, b = 0;
        int count = 0;
        int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 32);
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int argb = image.getPixel(x, y);
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                count++;
            }
        }
        if (count == 0) return 0xFF87CEEB;
        return 0xFF000000 | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
    }
}
