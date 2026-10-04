package nv.navineclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class BackgroundManager {
    private static final String REMOTE_URL = "https://kurohana-dev.github.io/images/background.png";
    
    private static Identifier customBackground = null;
    private static boolean loadStarted = false;
    private static boolean registered = false;
    private static DynamicTexture texture = null;
    private static int imgWidth = 1920;
    private static int imgHeight = 1080;

    public static Identifier getCustomBackground() {
        ensureRegistered();
        return customBackground;
    }
    
    public static boolean hasBackground() {
        return customBackground != null && texture != null;
    }

    public static void loadExternalBackground() {
        if (nv.navineclient.NavineClient.isGhost()) {
            return;
        }
        if (loadStarted) return;
        loadStarted = true;
        
        new Thread(() -> {
            try {
                Thread.sleep(500);
                
                if (tryLoadLocal()) {
                    System.out.println("[Navine] Background loaded from local file");
                    return;
                }
                
                if (tryLoadRemote()) {
                    System.out.println("[Navine] Background downloaded from remote");
                    return;
                }
                
                System.out.println("[Navine] No background available");
            } catch (Exception e) {
                System.err.println("[Navine] Failed to load background: " + e.getMessage());
                e.printStackTrace();
            }
        }, "Navine-Background-Loader").start();
    }
    
    private static boolean tryLoadLocal() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return false;
            
            File gameDir = client.gameDirectory;
            
            String[] paths = {
                "navine/background.png",
                "navine/background.jpg",
                "assets/background/background.png",
                "assets/background/background.jpg",
                "background.png",
                "background.jpg"
            };
            
            for (String path : paths) {
                File file = new File(gameDir, path);
                if (file.exists() && file.canRead()) {
                    System.out.println("[Navine] Found local background: " + file.getAbsolutePath());
                    byte[] data = readFileToBytes(file);
                    if (data != null && loadFromBytes(data)) {
                        return true;
                    }
                }
            }
            
            File specificPath = new File("C:\\Users\\hitbo\\curseforge\\minecraft\\Instances\\Navine-Client\\assets\\background\\background.png");
            if (specificPath.exists() && specificPath.canRead()) {
                byte[] data = readFileToBytes(specificPath);
                if (data != null && loadFromBytes(data)) {
                    return true;
                }
            }
            
        } catch (Exception e) {
            System.err.println("[Navine] Local background load error: " + e.getMessage());
        }
        return false;
    }
    
    private static byte[] readFileToBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    private static boolean tryLoadRemote() {
        try {
            System.out.println("[Navine] Downloading background from: " + REMOTE_URL);
            
            URI uri = new URI(REMOTE_URL);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 Navine-Client/1.5");
            conn.setRequestProperty("Accept", "image/png,image/*,*/*");
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
            System.out.println("[Navine] HTTP response: " + responseCode);
            
            if (responseCode == 200) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream is = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                }
                
                byte[] imageData = baos.toByteArray();
                System.out.println("[Navine] Downloaded " + imageData.length + " bytes");
                
                if (imageData.length > 1000) {
                    return loadFromBytes(imageData);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[Navine] Remote download error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    private static boolean loadFromBytes(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            NativeImage image = NativeImage.read(bais);
            
            if (image == null) {
                System.err.println("[Navine] Failed to decode image");
                return false;
            }
            
            imgWidth = image.getWidth();
            imgHeight = image.getHeight();
            System.out.println("[Navine] Image size: " + imgWidth + "x" + imgHeight);
            
            final NativeImage finalImage = image;
            
            Minecraft.getInstance().execute(() -> {
                try {
                    texture = new DynamicTexture(() -> "navine_background", finalImage);
                    customBackground = Identifier.fromNamespaceAndPath("navine", "background");
                    Minecraft.getInstance().getTextureManager().register(customBackground, texture);
                    registered = true;
                    System.out.println("[Navine] Texture created and registered on render thread");
                } catch (Exception | NoSuchMethodError e) {
                    System.err.println("[Navine] Constructor failed, trying alternative: " + e.getMessage());
                    try {
                        texture = createTextureManually(finalImage);
                        if (texture != null) {
                            customBackground = Identifier.fromNamespaceAndPath("navine", "background");
                            Minecraft.getInstance().getTextureManager().register(customBackground, texture);
                            registered = true;
                            System.out.println("[Navine] Texture created via alternative method");
                        }
                    } catch (Exception e2) {
                        System.err.println("[Navine] Alternative also failed: " + e2.getMessage());
                        e2.printStackTrace();
                    }
                }
            });
            
            return true;
        } catch (Exception e) {
            System.err.println("[Navine] Image decode error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private static DynamicTexture createTextureManually(NativeImage sourceImage) {
        try {
            java.lang.reflect.Constructor<?>[] constructors = DynamicTexture.class.getDeclaredConstructors();
            System.out.println("[Navine] Available constructors: " + constructors.length);
            for (java.lang.reflect.Constructor<?> c : constructors) {
                System.out.println("[Navine]   " + java.util.Arrays.toString(c.getParameterTypes()));
            }
            
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 1 && params[0] == NativeImage.class) {
                    constructor.setAccessible(true);
                    return (DynamicTexture) constructor.newInstance(sourceImage);
                }
                if (params.length == 2 && params[1] == NativeImage.class) {
                    constructor.setAccessible(true);
                    return (DynamicTexture) constructor.newInstance((java.util.function.Supplier<String>) () -> "navine_bg", sourceImage);
                }
            }
        } catch (Exception e) {
            System.err.println("[Navine] Reflection failed: " + e.getMessage());
        }
        return null;
    }
    
    private static void ensureRegistered() {
        if (texture != null && customBackground != null && !registered) {
            try {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.getTextureManager() != null) {
                    client.getTextureManager().register(customBackground, texture);
                    registered = true;
                    System.out.println("[Navine] Background texture registered");
                }
            } catch (Exception e) {
                System.err.println("[Navine] Failed to register texture: " + e.getMessage());
            }
        }
    }
    
    public static void renderPanel(GuiGraphicsExtractor context, int x, int y, int panelWidth, int panelHeight) {
        if (context == null || panelWidth <= 0 || panelHeight <= 0) {
            return;
        }
        if (!hasBackground()) {
            context.fill(x, y, x + panelWidth, y + panelHeight, 0x80000000);
            return;
        }
        ensureRegistered();
        if (!registered) {
            context.fill(x, y, x + panelWidth, y + panelHeight, 0x80000000);
            return;
        }
        try {
            context.blit(RenderPipelines.GUI_TEXTURED, customBackground, x, y, 0, 0, panelWidth, panelHeight, imgWidth, imgHeight);
            context.fill(x, y, x + panelWidth, y + panelHeight, 0x70000000);
        } catch (Exception e) {
            context.fill(x, y, x + panelWidth, y + panelHeight, 0x80000000);
        }
    }

    public static void renderBackground(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        if (!hasBackground()) return;
        
        ensureRegistered();
        if (!registered) return;
        
        try {
            float screenRatio = (float) screenWidth / screenHeight;
            float imgRatio = (float) imgWidth / imgHeight;
            
            int drawWidth, drawHeight;
            int drawX, drawY;
            
            if (screenRatio > imgRatio) {
                drawWidth = screenWidth;
                drawHeight = (int) (screenWidth / imgRatio);
                drawX = 0;
                drawY = (screenHeight - drawHeight) / 2;
            } else {
                drawHeight = screenHeight;
                drawWidth = (int) (screenHeight * imgRatio);
                drawX = (screenWidth - drawWidth) / 2;
                drawY = 0;
            }
            
            context.blit(RenderPipelines.GUI_TEXTURED, customBackground, drawX, drawY, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
            context.fill(0, 0, screenWidth, screenHeight, 0x40000000);
        } catch (Exception e) {
            System.err.println("[Navine] Render error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
