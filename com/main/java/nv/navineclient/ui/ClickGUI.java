package nv.navineclient.ui;

import nv.navineclient.NavineClient;
import nv.navineclient.config.ConfigManager;
import nv.navineclient.module.CategoryKey;
import nv.navineclient.module.CategoryRegistry;
import nv.navineclient.module.Module;
import nv.navineclient.module.ModuleManager;
import nv.navineclient.module.settings.*;
import nv.navineclient.module.world.DevMod;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.NavineButtonRenderer;
import nv.navineclient.util.NavineTheme;
import nv.navineclient.util.NavineSoundManager;
import nv.navineclient.util.RenderUtil;
import nv.navineclient.util.UiAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import nv.navineclient.util.NavineInput;

import java.util.*;

public class ClickGUI extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();

    public static int getToggleKey() {
        return NavineClient.getClickGuiKey();
    }

    private static final int FRAME_W = 300;
    private static final int FRAME_H = 240;
    private static final int RAIL_W = 72;
    private static final int RAIL_ITEM_H = 14;
    private static final int SEARCH_H = 14;
    private static final int MODULE_HEIGHT = 13;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SETTINGS_GAP = 6;
    private static final int DIVIDER = 0xFF1A1A1E;

    private static final int ENABLED_COLOR = NavineTheme.TEXT_PRIMARY;
    private static final int DISABLED_COLOR = NavineTheme.TEXT_DISABLED;

    private static final Module.Category[] TAB_CATEGORY_ORDER = {
            Module.Category.COMBAT,
            Module.Category.RENDER,
            Module.Category.MOVEMENT,
            Module.Category.WORLD,
            Module.Category.PLAYER,
            Module.Category.MISC
    };

    private final List<TabEntry> tabs = new ArrayList<>();
    private String activeTabId = null;
    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;
    private int lastCategoryCount = -1;

    private int frameX;
    private int frameY;
    private int searchX;
    private int searchY;
    private int searchW;
    private int searchClearX;
    private int searchClearY;
    private int searchClearW;
    private int searchClearH;
    private int moduleListX;
    private int moduleListY;
    private int moduleListW;
    private int moduleListH;

    private String searchQuery = "";
    private String searchText = "";
    private boolean searchFocused = false;
    private final Map<String, Integer> moduleScrollByTab = new HashMap<>();
    private final Map<String, Float> smoothScrollByTab = new HashMap<>();
    private int maxModuleScroll = 0;

    private Module selectedModule = null;
    private Module settingsModule = null;
    private NumberSetting draggingSlider = null;
    private ColorSetting draggingColorSetting = null;
    private int draggingColorChannel = -1;
    private StringSetting editingStringSetting = null;
    private String stringEditBuffer = "";
    private boolean listeningKey = false;
    private int settingsScroll = 0;
    private float settingsAnim = 0f;
    private float openAnim = 0f;
    private float closeHoverAnim = 0f;
    private float uiDelta = 0f;
    private final Map<Module, Float> moduleHoverAnim = new HashMap<>();
    private final Map<Module, Float> moduleEnableAnim = new HashMap<>();
    private final Map<Setting<?>, Float> settingToggleAnim = new HashMap<>();
    private final Map<String, Float> tabHoverAnim = new HashMap<>();

    private boolean leftMouseDown = false;
    private boolean rightMouseDown = false;
    private boolean toggleKeyHeld = false;

    private int settingsPanelX = -1;
    private int settingsPanelY = -1;
    private boolean draggingSettingsPanel = false;
    private int settingsDragOffsetX = 0;
    private int settingsDragOffsetY = 0;
    private boolean pendingSettingsDrag = false;
    private double pendingSettingsDragX = 0;
    private double pendingSettingsDragY = 0;
    private static final int DRAG_THRESHOLD = 4;

    public ClickGUI() {
        super(Component.literal(NavineClient.displayName()));
    }

    public void prepareForOpen() {
        activeTabId = null;
        searchQuery = "";
        searchText = "";
        searchFocused = false;
        moduleScrollByTab.clear();
        selectedModule = null;
        settingsModule = null;
        listeningKey = false;
        editingStringSetting = null;
        stringEditBuffer = "";
        draggingColorSetting = null;
        draggingColorChannel = -1;
        settingsScroll = 0;
        settingsAnim = 0f;
        openAnim = 0f;
        closeHoverAnim = 0f;
        tabHoverAnim.clear();
        settingsPanelX = -1;
        settingsPanelY = -1;
        draggingSettingsPanel = false;
        pendingSettingsDrag = false;
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        ensureLayout();
        long window = mc.getWindow() != null ? mc.getWindow().handle() : 0L;
        if (window != 0L) {
            toggleKeyHeld = NavineInput.isKeyDown(NavineClient.getClickGuiKey());
        }
    }

    private void ensureLayout() {
        if (mc.getWindow() == null) {
            return;
        }
        int sw = width > 0 ? width : mc.getWindow().getGuiScaledWidth();
        int sh = height > 0 ? height : mc.getWindow().getGuiScaledHeight();
        int catCount = CategoryRegistry.orderedKeys().size();
        if (sw == lastLayoutWidth && sh == lastLayoutHeight && catCount == lastCategoryCount && !tabs.isEmpty()) {
            return;
        }
        lastLayoutWidth = sw;
        lastLayoutHeight = sh;
        lastCategoryCount = catCount;
        buildTabs();
        layoutFrame(sw, sh);
    }

    private void buildTabs() {
        String previous = activeTabId;
        tabs.clear();
        for (Module.Category category : TAB_CATEGORY_ORDER) {
            tabs.add(TabEntry.category(CategoryKey.builtin(category)));
        }
        for (CategoryKey key : CategoryRegistry.orderedKeys()) {
            if (key.isCustom()) {
                tabs.add(TabEntry.category(key));
            }
        }
        if (previous != null && findTab(previous) != null) {
            activeTabId = previous;
        } else if (!tabs.isEmpty()) {
            activeTabId = tabs.get(0).id;
        }
    }

    private void layoutFrame(int sw, int sh) {
        frameX = Math.max(4, (sw - FRAME_W) / 2);
        frameY = Math.max(4, (sh - FRAME_H) / 2);

        int railY = frameY + 1;
        for (TabEntry tab : tabs) {
            tab.x = frameX + 1;
            tab.y = railY;
            tab.width = RAIL_W - 1;
            tab.height = RAIL_ITEM_H;
            railY += RAIL_ITEM_H;
        }

        searchX = frameX + RAIL_W + 5;
        searchY = frameY + 4;
        searchW = FRAME_W - RAIL_W - 10;

        moduleListX = frameX + RAIL_W + 1;
        moduleListY = frameY + 4 + SEARCH_H + 3;
        moduleListW = FRAME_W - RAIL_W - 2;
        moduleListH = FRAME_H - (moduleListY - frameY) - 2;
    }

    private int getModuleScroll() {
        if (activeTabId == null) {
            return 0;
        }
        return moduleScrollByTab.getOrDefault(activeTabId, 0);
    }

    private void setModuleScroll(int scroll) {
        if (activeTabId != null) {
            moduleScrollByTab.put(activeTabId, scroll);
        }
    }

    private TabEntry findTab(String id) {
        for (TabEntry tab : tabs) {
            if (tab.id.equals(id)) {
                return tab;
            }
        }
        return null;
    }

    private TabEntry getActiveTab() {
        return activeTabId != null ? findTab(activeTabId) : null;
    }

    @Override
    protected void init() {
        ensureLayout();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        ensureLayout();
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        selectedModule = null;
        settingsModule = null;
        listeningKey = false;
        settingsScroll = 0;
        settingsAnim = 0f;
        settingsPanelX = -1;
        settingsPanelY = -1;
        searchFocused = false;
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (mc.getWindow() == null) {
            return;
        }
        ensureLayout();
        long window = mc.getWindow().handle();
        int toggleKey = NavineClient.getClickGuiKey();
        boolean togglePressed = NavineInput.isKeyDown(toggleKey);
        if (!togglePressed) {
            toggleKeyHeld = false;
        }

        boolean leftNow = NavineInput.isMouseDown(NavineInput.MOUSE_BUTTON_LEFT);
        boolean rightNow = NavineInput.isMouseDown(NavineInput.MOUSE_BUTTON_RIGHT);

        if (leftNow && !leftMouseDown) {
            handleMouseClicked(mx, my, 0);
        }
        if (rightNow && !rightMouseDown) {
            handleMouseClicked(mx, my, 1);
        }
        if (!leftNow && leftMouseDown) {
            handleMouseReleased(mx, my, 0);
        }
        if (!rightNow && rightMouseDown) {
            handleMouseReleased(mx, my, 1);
        }

        leftMouseDown = leftNow;
        rightMouseDown = rightNow;
        uiDelta = delta;
        openAnim = UiAnim.approach(openAnim, 1f, 10f, delta);

        if (leftNow && pendingSettingsDrag && !draggingSettingsPanel) {
            double sdx = mx - pendingSettingsDragX;
            double sdy = my - pendingSettingsDragY;
            if (Math.abs(sdx) > DRAG_THRESHOLD || Math.abs(sdy) > DRAG_THRESHOLD) {
                draggingSettingsPanel = true;
                pendingSettingsDrag = false;
            }
        }

        if (leftNow && draggingSettingsPanel && settingsModule != null) {
            SettingsLayout layout = computeSettingsLayout(settingsAnim);
            int newX = mx - settingsDragOffsetX;
            int newY = my - settingsDragOffsetY;
            settingsPanelX = Math.max(0, Math.min(newX, width - layout.scaledW));
            settingsPanelY = Math.max(0, Math.min(newY, height - layout.scaledH));
        }

        int alpha = Math.min(255, Math.max(0, (int) (openAnim * 255)));
        ctx.fill(0, 0, width, height, UiAnim.scaleAlpha(NavineTheme.screenDim(), openAnim));
        if (NavineClient.isGhost() && !AuthManager.isVerified()) {
            String hint = "Not logged in - use .login <name> in chat";
            int hintW = mc.font.width(hint);
            ctx.fill(width / 2 - hintW / 2 - 6, 2, width / 2 + hintW / 2 + 6, 14, applyAlpha(0xCC220000, alpha));
            ctx.text(font, hint, width / 2 - hintW / 2, 4, applyAlpha(0xFFFF8888, alpha), false);
        }

        renderFrame(ctx, mx, my, alpha);

        float targetAnim = selectedModule != null ? 1f : 0f;
        settingsAnim = UiAnim.approach(settingsAnim, targetAnim, 10f, delta);
        if (selectedModule != null) {
            settingsModule = selectedModule;
        } else if (settingsAnim < 0.02f) {
            settingsModule = null;
            settingsScroll = 0;
        }

        if (settingsModule != null && settingsAnim > 0.01f) {
            renderSettingsPanel(ctx, mx, my, settingsAnim);
        }

        if (leftNow && draggingSlider != null && settingsModule != null && leftMouseDown) {
            updateSlider(mx, my);
        }
        if (leftNow && draggingColorSetting != null && settingsModule != null && leftMouseDown) {
            updateColorSlider(mx, my);
        }

        super.extractRenderState(ctx, mx, my, delta);
    }

    private void renderFrame(GuiGraphicsExtractor ctx, int mx, int my, int alpha) {
        int frameBg = applyAlpha(NavineTheme.panelBg(), alpha);
        int border = applyAlpha(NavineTheme.border(), alpha);
        ctx.fill(frameX, frameY, frameX + FRAME_W, frameY + FRAME_H, frameBg);
        RenderUtil.drawOutline(ctx, frameX, frameY, FRAME_W, FRAME_H, 1, border);

        int railBg = applyAlpha(NavineTheme.headerBg(), alpha);
        ctx.fill(frameX + 1, frameY + 1, frameX + RAIL_W, frameY + FRAME_H - 1, railBg);
        ctx.fill(frameX + RAIL_W, frameY + 1, frameX + RAIL_W + 1, frameY + FRAME_H - 1, border);

        for (TabEntry tab : tabs) {
            renderRailItem(ctx, tab, mx, my, alpha);
        }

        renderSearchBar(ctx, mx, my, alpha);

        TabEntry active = getActiveTab();
        if (active != null) {
            renderModuleList(ctx, active, mx, my, alpha);
        }
    }

    private void renderRailItem(GuiGraphicsExtractor ctx, TabEntry tab, int mx, int my, int alpha) {
        boolean active = tab.id.equals(activeTabId);
        boolean hover = mx >= tab.x && mx <= tab.x + tab.width && my >= tab.y && my <= tab.y + tab.height;
        float hoverAnim = tabHoverAnim.getOrDefault(tab.id, 0f);
        hoverAnim = UiAnim.approach(hoverAnim, hover ? 1f : 0f, 14f, uiDelta);
        tabHoverAnim.put(tab.id, hoverAnim);

        if (active) {
            ctx.fill(tab.x, tab.y, tab.x + tab.width, tab.y + tab.height, applyAlpha(NavineTheme.enabledBg(), alpha));
            ctx.fill(tab.x, tab.y + 1, tab.x + 2, tab.y + tab.height - 1, applyAlpha(NavineTheme.accent(), alpha));
        } else if (hoverAnim > 0.01f) {
            ctx.fill(tab.x, tab.y, tab.x + tab.width, tab.y + tab.height,
                    applyAlpha(UiAnim.lerpColor(0x00000000, NavineTheme.hover(), hoverAnim), alpha));
        }

        int labelColor = active ? NavineTheme.tabTextActive() : NavineTheme.TEXT_SECONDARY;
        ctx.text(font, tab.label, tab.x + 6, tab.y + 3, applyAlpha(labelColor, alpha), false);
    }

    private void renderSearchBar(GuiGraphicsExtractor ctx, int mx, int my, int alpha) {
        int border = searchFocused ? applyAlpha(NavineTheme.accent(), alpha) : applyAlpha(NavineTheme.border(), alpha);
        ctx.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_H, applyAlpha(NavineTheme.headerBg(), alpha));
        RenderUtil.drawOutline(ctx, searchX, searchY, searchW, SEARCH_H, 1, border);

        String display = searchText.isEmpty() ? "Search..." : searchText;
        int textColor = searchText.isEmpty() ? applyAlpha(NavineTheme.TEXT_DISABLED, alpha) : applyAlpha(NavineTheme.TEXT_PRIMARY, alpha);
        if (searchText.isEmpty()) {
            ctx.text(font, display, searchX + 4, searchY + 3, textColor, false);
        } else {
            renderSearchText(ctx, searchX + 4, searchY + 3, searchText, searchQuery, alpha);
        }

        if (!searchText.isEmpty()) {
            int clearX = searchX + searchW - 12;
            boolean clearHover = mx >= clearX && mx <= clearX + 10 && my >= searchY && my <= searchY + SEARCH_H;
            ctx.text(font, "x", clearX + 1, searchY + 3,
                    clearHover ? applyAlpha(NavineTheme.TEXT_PRIMARY, alpha) : applyAlpha(NavineTheme.TEXT_DISABLED, alpha), false);
            searchClearX = clearX;
            searchClearY = searchY;
            searchClearW = 10;
            searchClearH = SEARCH_H;
        } else {
            searchClearX = 0;
            searchClearY = 0;
            searchClearW = 0;
            searchClearH = 0;
        }
    }

    private boolean matchesSearch(Module module, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String name = module.getName().toLowerCase();
        String normalizedName = name.replace("_", " ").replace("-", " ");
        String compactName = normalizedName.replace(" ", "");
        String compactQuery = query.replace(" ", "");
        if (normalizedName.contains(query) || compactName.contains(compactQuery)) {
            return true;
        }
        String description = module.getDescription();
        if (description != null && description.toLowerCase().contains(query)) {
            return true;
        }
        String[] queryParts = query.split("\\s+");
        if (queryParts.length > 1) {
            for (String part : queryParts) {
                if (part.isEmpty()) {
                    continue;
                }
                if (!normalizedName.contains(part) && !compactName.contains(part.replace(" ", ""))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private List<Module> getModulesForTab(TabEntry tab) {
        List<Module> mods = ModuleManager.getModulesByCategoryKey(tab.categoryKey);
        if (searchQuery.isEmpty()) {
            return mods;
        }
        List<Module> filtered = new ArrayList<>();
        for (Module m : mods) {
            if (matchesSearch(m, searchQuery)) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    private void renderModuleList(GuiGraphicsExtractor ctx, TabEntry tab, int mx, int my, int alpha) {
        List<Module> mods = getModulesForTab(tab);
        int listX = moduleListX;
        int listY = moduleListY;
        int listW = moduleListW;
        int visH = moduleListH;
        int contentH = Math.max(visH, mods.size() * MODULE_HEIGHT);
        boolean hasScrollbar = mods.size() * MODULE_HEIGHT > visH;

        maxModuleScroll = Math.max(0, mods.size() * MODULE_HEIGHT - visH);
        setModuleScroll(Math.max(0, Math.min(getModuleScroll(), maxModuleScroll)));

        float smooth = smoothScrollByTab.getOrDefault(tab.id, (float) getModuleScroll());
        smooth = UiAnim.approach(smooth, getModuleScroll(), 16f, uiDelta);
        smoothScrollByTab.put(tab.id, smooth);
        int renderScroll = Math.round(smooth);

        if (mods.isEmpty()) {
            String emptyMsg = searchQuery.isEmpty() ? "No modules" : "No matches";
            ctx.text(font, emptyMsg, listX + 6, listY + 6, applyAlpha(DISABLED_COLOR, alpha), false);
            return;
        }

        int moduleAreaWidth = hasScrollbar ? listW - SCROLLBAR_WIDTH - 2 : listW;
        ctx.enableScissor(listX, listY, listX + moduleAreaWidth, listY + visH);

        int curY = listY - renderScroll;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float moduleHover = moduleHoverAnim.getOrDefault(m, 0f);
            boolean moduleHovered = mx >= listX && mx <= listX + moduleAreaWidth
                    && my >= Math.max(curY, listY) && my <= Math.min(curY + MODULE_HEIGHT - 1, listY + visH);
            moduleHover = UiAnim.approach(moduleHover, moduleHovered ? 1f : 0f, 12f, uiDelta);
            moduleHoverAnim.put(m, moduleHover);
            renderModule(ctx, m, listX, curY, listY, listY + visH, moduleAreaWidth, moduleHover, alpha);
            if (i < mods.size() - 1) {
                int divY = curY + MODULE_HEIGHT - 1;
                if (divY >= listY && divY < listY + visH) {
                    ctx.fill(listX + 4, divY, listX + moduleAreaWidth - 2, divY + 1, applyAlpha(DIVIDER, alpha));
                }
            }
            curY += MODULE_HEIGHT;
        }

        ctx.disableScissor();

        if (hasScrollbar) {
            int scrollbarX = listX + listW - SCROLLBAR_WIDTH - 1;
            ctx.fill(scrollbarX, listY, scrollbarX + SCROLLBAR_WIDTH, listY + visH, applyAlpha(NavineTheme.scrollbarBg(), alpha));
            double scrollPct = maxModuleScroll > 0 ? (double) renderScroll / maxModuleScroll : 0;
            int thumbH = Math.max(8, (int) ((double) visH / contentH * visH));
            int thumbY = listY + (int) (scrollPct * (visH - thumbH));
            ctx.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbH, applyAlpha(NavineTheme.scrollbarColor(), alpha));
        }
    }

    private void renderModule(GuiGraphicsExtractor ctx, Module m, int listX, int curY,
                              int clipTop, int clipBot, int areaW, float hoverAnim, int alpha) {
        if (curY + MODULE_HEIGHT < clipTop || curY > clipBot) {
            return;
        }

        float enableAnim = moduleEnableAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f);
        enableAnim = UiAnim.approach(enableAnim, m.isEnabled() ? 1f : 0f, 12f, uiDelta);
        moduleEnableAnim.put(m, enableAnim);

        if (hoverAnim > 0.01f) {
            ctx.fill(listX, curY, listX + areaW, curY + MODULE_HEIGHT - 1,
                    applyAlpha(UiAnim.lerpColor(0x00000000, NavineTheme.hover(), hoverAnim), alpha));
        }
        if (enableAnim > 0.01f) {
            ctx.fill(listX, curY + 2, listX + 2, curY + MODULE_HEIGHT - 3,
                    applyAlpha(UiAnim.scaleAlpha(NavineTheme.accent(), enableAnim), alpha));
        }

        int txtC = applyAlpha(UiAnim.lerpColor(m.isEnabled() ? ENABLED_COLOR : DISABLED_COLOR, ENABLED_COLOR, hoverAnim * 0.5f), alpha);
        int textX = listX + 6;
        if (searchQuery.isEmpty()) {
            ctx.text(mc.font, m.getName(), textX, curY + 3, txtC, false);
        } else {
            renderHighlightedModuleName(ctx, m, textX, curY + 3, txtC, areaW - 10, alpha);
        }
    }

    private List<Setting<?>> visibleSettings(Module module) {
        List<Setting<?>> visible = new ArrayList<>();
        if (module == null) {
            return visible;
        }
        for (Setting<?> setting : module.getSettings()) {
            if (module.isSettingVisible(setting)) {
                visible.add(setting);
            }
        }
        return visible;
    }

    private int settingRowHeight(Setting<?> setting) {
        if (setting instanceof StringSetting) {
            return 34;
        }
        if (setting instanceof ColorSetting) {
            return 52;
        }
        if (setting instanceof ModeSetting) {
            return 28;
        }
        if (setting instanceof NumberSetting) {
            return 28;
        }
        if (setting instanceof BooleanSetting) {
            return 22;
        }
        return 28;
    }

    private int totalSettingsHeight(Module module) {
        int total = 0;
        for (Setting<?> setting : visibleSettings(module)) {
            total += settingRowHeight(setting) + 2;
        }
        return total;
    }

    private void updateSlider(double mx, double my) {
        if (settingsModule == null || draggingSlider == null) {
            return;
        }
        SettingsLayout layout = computeSettingsLayout(settingsAnim);
        double[] local = new double[2];
        toSettingsLocal(mx, my, layout, local);
        int barX = 8;
        int barW = layout.panelW - 16;
        double pct = Math.max(0, Math.min(1, (local[0] - barX) / (double) barW));
        draggingSlider.setValue(draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin()));
        ConfigManager.save();
    }

    private void updateColorSlider(double mx, double my) {
        if (settingsModule == null || draggingColorSetting == null || draggingColorChannel < 0) {
            return;
        }
        SettingsLayout layout = computeSettingsLayout(settingsAnim);
        double[] local = new double[2];
        toSettingsLocal(mx, my, layout, local);
        int barX = 22;
        int barW = layout.panelW - 34;
        double pct = Math.max(0, Math.min(1, (local[0] - barX) / (double) barW));
        int value = (int) Math.round(pct * 255.0);
        int r = draggingColorSetting.getRed();
        int g = draggingColorSetting.getGreen();
        int b = draggingColorSetting.getBlue();
        if (draggingColorChannel == 0) {
            r = value;
        } else if (draggingColorChannel == 1) {
            g = value;
        } else {
            b = value;
        }
        draggingColorSetting.setRGB(r, g, b);
        ConfigManager.save();
    }

    private SettingsLayout computeSettingsLayout(float anim) {
        List<Setting<?>> visible = visibleSettings(settingsModule);
        int settingCount = visible.size();
        boolean widePanel = false;
        for (Setting<?> setting : visible) {
            if (setting instanceof StringSetting || setting instanceof ColorSetting) {
                widePanel = true;
                break;
            }
        }
        int panelW = widePanel ? 260 : (settingCount >= 5 ? 240 : (settingCount >= 3 ? 220 : 200));
        int baseH = 88;
        int settingH = totalSettingsHeight(settingsModule);
        int panelH = Math.min(baseH + settingH, 380);
        float scale = 1.0f;
        int scaledW = (int) (panelW * scale);
        int scaledH = (int) (panelH * scale);
        int panelX;
        int panelY;
        if (settingsPanelX >= 0 && settingsPanelY >= 0) {
            panelX = settingsPanelX;
            panelY = settingsPanelY;
        } else {
            panelX = frameX + FRAME_W + SETTINGS_GAP;
            panelY = frameY;
            if (panelX + scaledW > width - 4) {
                panelX = Math.max(4, frameX - SETTINGS_GAP - scaledW);
            }
            if (panelY + scaledH > height - 4) {
                panelY = Math.max(4, height - scaledH - 4);
            }
        }
        return new SettingsLayout(panelW, panelH, scale, scaledW, scaledH, panelX, panelY);
    }

    private void toSettingsLocal(double mx, double my, SettingsLayout layout, double[] out) {
        float cx = layout.panelX + layout.scaledW / 2f;
        float cy = layout.panelY + layout.scaledH / 2f;
        out[0] = (mx - cx) / layout.scale + layout.panelW / 2f;
        out[1] = (my - cy) / layout.scale + layout.panelH / 2f;
    }

    private void renderSettingsPanel(GuiGraphicsExtractor ctx, int mx, int my, float anim) {
        SettingsLayout layout = computeSettingsLayout(anim);
        int panelW = layout.panelW;
        int panelH = layout.panelH;
        int scaledW = layout.scaledW;
        int scaledH = layout.scaledH;
        int panelX = layout.panelX;
        int panelY = layout.panelY;
        List<Setting<?>> visible = visibleSettings(settingsModule);
        int alpha = Math.min(255, Math.max(0, (int) (anim * 255)));
        int settingsTop = panelY + 22;
        int settingsBottom = panelY + panelH - 42;
        int settingsAreaH = Math.max(0, settingsBottom - settingsTop);
        int totalSettingsH = totalSettingsHeight(settingsModule);
        int maxSettingsScroll = Math.max(0, totalSettingsH - settingsAreaH);
        settingsScroll = Math.max(0, Math.min(settingsScroll, maxSettingsScroll));

        ctx.fill(panelX, panelY, panelX + scaledW, panelY + scaledH, applyAlpha(NavineTheme.settingsBg(), alpha));
        RenderUtil.drawOutline(ctx, panelX, panelY, scaledW, scaledH, 1, applyAlpha(NavineTheme.border(), alpha));

        ctx.fill(panelX + 1, panelY + 1, panelX + scaledW - 1, panelY + 18, applyAlpha(NavineTheme.headerBg(), alpha));
        if (draggingSettingsPanel) {
            ctx.fill(panelX + 1, panelY + 1, panelX + scaledW - 1, panelY + 18, applyAlpha(NavineTheme.dragHighlight(), alpha));
        }
        String modName = settingsModule.getName();
        ctx.text(font, modName, panelX + 6, panelY + 5, applyAlpha(NavineTheme.accent(), alpha), false);
        ctx.fill(panelX + 1, panelY + 18, panelX + scaledW - 1, panelY + 19, applyAlpha(NavineTheme.border(), alpha));

        if (DevMod.shouldShowUiOutlines()) {
            RenderUtil.drawOutline(ctx, panelX, panelY, scaledW, scaledH, 2, applyAlpha(0xFFFF0000, alpha));
        }

        int settingY = settingsTop - settingsScroll;
        ctx.enableScissor(panelX + 6, settingsTop, panelX + scaledW - 6, settingsBottom);
        for (Setting<?> s : visible) {
            int rowH = settingRowHeight(s);
            if (settingY + rowH >= settingsTop && settingY <= settingsBottom) {
                renderSettingEntry(ctx, s, panelX + 8, settingY, panelW - 16, mx, my);
            }
            settingY += rowH + 2;
        }
        ctx.disableScissor();

        if (maxSettingsScroll > 0) {
            int sbX = panelX + scaledW - 6;
            int sbY = settingsTop;
            int sbH = settingsAreaH;
            ctx.fill(sbX, sbY, sbX + 3, sbY + sbH, applyAlpha(NavineTheme.scrollbarBg(), alpha));
            double pct = (double) settingsScroll / maxSettingsScroll;
            int thumbH = Math.max(10, (int) ((double) settingsAreaH / totalSettingsH * sbH));
            int thumbY = sbY + (int) (pct * (sbH - thumbH));
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, applyAlpha(NavineTheme.scrollbarColor(), alpha));
        }

        int keyY = panelY + panelH - 34;
        String keyLabel = "Keybind: ";
        String keyName = listeningKey ? "..." : (settingsModule != null && settingsModule.getKey() == 0 ? "NONE" : (settingsModule != null ? getKeyName(settingsModule.getKey()) : "NONE"));
        ctx.text(font, keyLabel, panelX + 8, keyY, applyAlpha(NavineTheme.mutedText(), alpha), false);
        ctx.text(font, keyName, panelX + 8 + mc.font.width(keyLabel), keyY, applyAlpha(listeningKey ? 0xFFFF5555 : NavineTheme.accent(), alpha), false);

        int btnY = panelY + panelH - 16;
        int btnW = panelW - 16;
        boolean btnHover = mx >= panelX + 8 && mx <= panelX + 8 + btnW && my >= btnY && my <= btnY + 12;
        closeHoverAnim = UiAnim.approach(closeHoverAnim, btnHover ? 1f : 0f, 14f, uiDelta);
        NavineButtonRenderer.renderPanelButton(ctx, panelX + 8, btnY, btnW, 12, "Close", closeHoverAnim, alpha);
    }

    private void renderSettingEntry(GuiGraphicsExtractor ctx, Setting<?> s, int x, int y, int w, int mx, int my) {
        if (s instanceof BooleanSetting bs) {
            ctx.text(font, s.getName(), x, y + 2, NavineTheme.TEXT_PRIMARY, false);
            float toggleAnim = settingToggleAnim.getOrDefault(s, bs.getValue() ? 1f : 0f);
            toggleAnim = UiAnim.approach(toggleAnim, bs.getValue() ? 1f : 0f, 14f, uiDelta);
            settingToggleAnim.put(s, toggleAnim);
            int trackX = x + w - 20;
            int trackW = 20;
            int trackH = 10;
            int trackY = y + 2;
            int trackColor = UiAnim.lerpColor(NavineTheme.trackBg(), NavineTheme.accent(), toggleAnim);
            ctx.fill(trackX, trackY, trackX + trackW, trackY + trackH, trackColor);
            RenderUtil.drawOutline(ctx, trackX, trackY, trackW, trackH, 1, NavineTheme.border());
            int knobX = trackX + 2 + (int) ((trackW - 10) * toggleAnim);
            int knobColor = UiAnim.lerpColor(NavineTheme.knobOff(), 0xFFFFFFFF, toggleAnim);
            ctx.fill(knobX, trackY + 2, knobX + 6, trackY + trackH - 2, knobColor);
        } else if (s instanceof NumberSetting ns) {
            String txt = s.getName();
            String val = String.format("%.2f", ns.getValue());
            ctx.text(font, txt, x, y + 1, NavineTheme.TEXT_SECONDARY, false);

            int resetBtnW = 10;
            int resetBtnX = x + w - resetBtnW;
            boolean resetHover = mx >= resetBtnX && mx <= resetBtnX + resetBtnW && my >= y && my <= y + 10;
            ctx.fill(resetBtnX, y, resetBtnX + resetBtnW, y + 10, resetHover ? 0xFFFF5555 : NavineTheme.controlBg());
            RenderUtil.drawOutline(ctx, resetBtnX, y, resetBtnW, 10, 1, NavineTheme.border());
            ctx.text(font, "R", resetBtnX + 2, y + 1, 0xFFFFFFFF, false);

            ctx.text(font, val, resetBtnX - mc.font.width(val) - 4, y + 1, NavineTheme.accent(), false);

            int barY = y + 12;
            int barW = w;
            int barH = 8;
            double pct = (ns.getValue() - ns.getMin()) / (ns.getMax() - ns.getMin());
            ctx.fill(x, barY, x + barW, barY + barH, NavineTheme.trackBg());
            ctx.fill(x, barY, x + (int) (barW * pct), barY + barH, NavineTheme.accent());
        } else if (s instanceof ModeSetting ms) {
            ctx.text(font, s.getName(), x, y, NavineTheme.TEXT_PRIMARY, false);
            String val = ms.getValue();
            int valW = mc.font.width(val);
            int modeY = y + 9;
            int arrowW = 10;
            ctx.fill(x, modeY, x + w, modeY + 9, NavineTheme.headerBg());
            RenderUtil.drawOutline(ctx, x, modeY, w, 9, 1, NavineTheme.border());
            boolean leftHover = mx >= x && mx <= x + arrowW && my >= modeY && my <= modeY + 9;
            boolean rightHover = mx >= x + w - arrowW && mx <= x + w && my >= modeY && my <= modeY + 9;
            ctx.text(font, "<", x + 2, modeY + 1, leftHover ? NavineTheme.accent2() : NavineTheme.mutedText(), false);
            ctx.text(font, ">", x + w - 8, modeY + 1, rightHover ? NavineTheme.accent2() : NavineTheme.mutedText(), false);
            ctx.text(font, val, x + (w - valW) / 2, modeY + 1, NavineTheme.accent(), false);
        } else if (s instanceof ColorSetting cs) {
            ctx.text(font, s.getName(), x, y, NavineTheme.TEXT_PRIMARY, false);
            int colorBoxX = x + w - 44;
            int colorBoxW = 36;
            int color = cs.getValue();
            ctx.fill(colorBoxX, y + 1, colorBoxX + colorBoxW, y + 13, color);
            RenderUtil.drawOutline(ctx, colorBoxX, y + 1, colorBoxW, 12, 1, NavineTheme.border());

            renderColorChannelSlider(ctx, x, y + 16, w, "R", cs.getRed());
            renderColorChannelSlider(ctx, x, y + 28, w, "G", cs.getGreen());
            renderColorChannelSlider(ctx, x, y + 40, w, "B", cs.getBlue());
        } else if (s instanceof StringSetting ss) {
            ctx.text(font, s.getName(), x, y, NavineTheme.TEXT_PRIMARY, false);
            int boxY = y + 12;
            int boxH = 14;
            boolean editing = editingStringSetting == ss;
            String display = editing ? stringEditBuffer : ss.getValue();
            if (display == null) {
                display = "";
            }
            ctx.fill(x, boxY, x + w, boxY + boxH, editing ? NavineTheme.enabledBg() : NavineTheme.headerBg());
            RenderUtil.drawOutline(ctx, x, boxY, w, boxH, 1, editing ? NavineTheme.accent() : NavineTheme.border());
            String shown = truncateSettingText(display, w - 8);
            ctx.text(font, shown, x + 4, boxY + 3, editing ? NavineTheme.TEXT_PRIMARY : NavineTheme.TEXT_SECONDARY, false);
            if (editing) {
                int cursorX = x + 4 + mc.font.width(shown);
                ctx.fill(cursorX, boxY + 2, cursorX + 1, boxY + boxH - 2, NavineTheme.accent());
            }
        }
    }

    private void renderColorChannelSlider(GuiGraphicsExtractor ctx, int x, int y, int w, String label, int value) {
        ctx.text(font, label, x, y + 1, NavineTheme.TEXT_SECONDARY, false);
        int barX = x + 14;
        int barW = w - 34;
        int barH = 6;
        double pct = value / 255.0;
        ctx.fill(barX, y + 2, barX + barW, y + 2 + barH, NavineTheme.trackBg());
        ctx.fill(barX, y + 2, barX + (int) (barW * pct), y + 2 + barH, NavineTheme.accent());
        String valText = String.valueOf(value);
        ctx.text(font, valText, x + w - mc.font.width(valText), y + 1, NavineTheme.TEXT_SECONDARY, false);
    }

    private String truncateSettingText(String text, int maxWidth) {
        if (text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = text;
        while (trimmed.length() > 0 && mc.font.width(trimmed) + mc.font.width(ellipsis) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    private String getKeyName(int k) {
        String n = NavineInput.getKeyName(k);
        if (n != null) return n.toUpperCase();
        return switch (k) {
            case NavineInput.KEY_LEFT_SHIFT -> "LSHIFT";
            case NavineInput.KEY_RIGHT_SHIFT -> "RSHIFT";
            case NavineInput.KEY_LEFT_CONTROL -> "LCTRL";
            case NavineInput.KEY_LEFT_ALT -> "LALT";
            case NavineInput.KEY_SPACE -> "SPACE";
            case NavineInput.KEY_TAB -> "TAB";
            case NavineInput.KEY_CAPS_LOCK -> "CAPS";
            default -> k >= NavineInput.KEY_F1 && k <= NavineInput.KEY_F12 ? "F" + (k - NavineInput.KEY_F1 + 1) : "KEY" + k;
        };
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.key();

        if (editingStringSetting != null) {
            if (keyCode == NavineInput.KEY_ESCAPE) {
                editingStringSetting = null;
                stringEditBuffer = "";
                return true;
            }
            if (keyCode == NavineInput.KEY_ENTER) {
                editingStringSetting.setValue(stringEditBuffer.trim());
                editingStringSetting = null;
                stringEditBuffer = "";
                ConfigManager.save();
                return true;
            }
            if (keyCode == NavineInput.KEY_BACKSPACE && !stringEditBuffer.isEmpty()) {
                stringEditBuffer = stringEditBuffer.substring(0, stringEditBuffer.length() - 1);
                return true;
            }
            return true;
        }

        if (listeningKey && selectedModule != null) {
            if (keyCode == NavineInput.KEY_ESCAPE) {
                listeningKey = false;
            } else if (keyCode == NavineInput.KEY_DELETE || keyCode == NavineInput.KEY_BACKSPACE) {
                selectedModule.setKey(0);
                listeningKey = false;
                ConfigManager.save();
            } else {
                selectedModule.setKey(keyCode);
                listeningKey = false;
                ConfigManager.save();
            }
            return true;
        }

        if (searchFocused) {
            if (keyCode == NavineInput.KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                searchQuery = searchText.toLowerCase();
                setModuleScroll(0);
                return true;
            }
            if (keyCode == NavineInput.KEY_ESCAPE) {
                searchText = "";
                searchQuery = "";
                setModuleScroll(0);
                searchFocused = false;
                return true;
            }
            if (keyCode == NavineInput.KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == NavineClient.getClickGuiKey()) {
                return true;
            }
        }

        if (keyCode == NavineInput.KEY_ESCAPE) {
            if (!searchQuery.isEmpty() || !searchText.isEmpty()) {
                searchText = "";
                searchQuery = "";
                setModuleScroll(0);
                searchFocused = false;
                return true;
            }
            if (selectedModule != null) {
                selectedModule = null;
                listeningKey = false;
                editingStringSetting = null;
                stringEditBuffer = "";
                settingsPanelX = -1;
                settingsPanelY = -1;
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == NavineClient.getClickGuiKey()) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        if (listeningKey) return true;
        if (editingStringSetting != null) {
            if (charInput.isAllowedChatCharacter() && stringEditBuffer.length() < 64) {
                stringEditBuffer += charInput.codepointAsString();
                return true;
            }
            return true;
        }
        if (!searchFocused) return super.charTyped(charInput);

        if (charInput.isAllowedChatCharacter()) {
            searchText += charInput.codepointAsString();
            searchQuery = searchText.toLowerCase();
            setModuleScroll(0);
            return true;
        }
        return super.charTyped(charInput);
    }

    private void handleMouseClicked(double mx, double my, int button) {
        if (searchClearW > 0
                && mx >= searchClearX && mx <= searchClearX + searchClearW
                && my >= searchClearY && my <= searchClearY + searchClearH) {
            searchText = "";
            searchQuery = "";
            setModuleScroll(0);
            searchFocused = true;
            return;
        }
        if (mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + SEARCH_H) {
            searchFocused = true;
            return;
        }
        searchFocused = false;

        if (selectedModule != null && settingsModule != null) {
            SettingsLayout layout = computeSettingsLayout(settingsAnim);
            double[] local = new double[2];
            toSettingsLocal(mx, my, layout, local);
            double localX = local[0];
            double localY = local[1];

            if (button == 0 && localX >= 0 && localX <= layout.panelW && localY >= 0 && localY <= 18) {
                pendingSettingsDrag = true;
                pendingSettingsDragX = mx;
                pendingSettingsDragY = my;
                settingsDragOffsetX = (int) mx - layout.panelX;
                settingsDragOffsetY = (int) my - layout.panelY;
                return;
            }

            int btnY = layout.panelH - 16;
            int btnW = layout.panelW - 16;
            if (localX >= 8 && localX <= 8 + btnW && localY >= btnY && localY <= btnY + 12) {
                if (!NavineClient.isGhost()) {
                    NavineSoundManager.playClick();
                }
                selectedModule = null;
                listeningKey = false;
                settingsScroll = 0;
                settingsPanelX = -1;
                settingsPanelY = -1;
                return;
            }

            int keyY = layout.panelH - 34;
            if (localY >= keyY - 2 && localY <= keyY + 10 && localX >= 8 && localX <= layout.panelW - 8) {
                listeningKey = !listeningKey;
                return;
            }

            int settingsTop = 22;
            int settingsBottom = layout.panelH - 42;
            int settingY = settingsTop - settingsScroll;
            for (Setting<?> s : visibleSettings(settingsModule)) {
                if (localY >= settingsTop && localY <= settingsBottom &&
                        handleSettingClick(s, 8, settingY, layout.panelW - 16, localX, localY, button)) {
                    return;
                }
                settingY += settingRowHeight(s) + 2;
            }

            if (localX < 0 || localX > layout.panelW || localY < 0 || localY > layout.panelH) {
                selectedModule = null;
                listeningKey = false;
                settingsScroll = 0;
                settingsPanelX = -1;
                settingsPanelY = -1;
                return;
            }
            return;
        }

        for (TabEntry tab : tabs) {
            if (mx >= tab.x && mx <= tab.x + tab.width && my >= tab.y && my <= tab.y + tab.height) {
                if (button == 0) {
                    activeTabId = tab.id;
                    setModuleScroll(moduleScrollByTab.getOrDefault(tab.id, 0));
                }
                return;
            }
        }

        TabEntry active = getActiveTab();
        if (active != null && handleModuleListClick(active, mx, my, button)) {
            return;
        }
    }

    private boolean handleModuleListClick(TabEntry tab, double mx, double my, int button) {
        if (mx < moduleListX || mx > moduleListX + moduleListW
                || my < moduleListY || my > moduleListY + moduleListH) {
            return false;
        }

        List<Module> mods = getModulesForTab(tab);
        boolean hasScrollbar = mods.size() * MODULE_HEIGHT > moduleListH;
        int moduleAreaWidth = hasScrollbar ? moduleListW - SCROLLBAR_WIDTH - 2 : moduleListW;

        int curY = moduleListY - Math.round(smoothScrollByTab.getOrDefault(tab.id, (float) getModuleScroll()));
        for (Module m : mods) {
            int modTop = Math.max(curY, moduleListY);
            int modBot = Math.min(curY + MODULE_HEIGHT, moduleListY + moduleListH);
            if (my >= modTop && my < modBot && mx >= moduleListX && mx <= moduleListX + moduleAreaWidth) {
                if (button == 0) {
                    if (!AuthManager.isVerified()) {
                        return true;
                    }
                    if (ModuleManager.canToggleModule(m)) {
                        m.toggle();
                        m.suppressKeybind(200);
                        ConfigManager.save();
                    }
                    return true;
                } else if (button == 1) {
                    selectedModule = m;
                    settingsPanelX = -1;
                    settingsPanelY = -1;
                    settingsScroll = 0;
                    return true;
                }
            }
            curY += MODULE_HEIGHT;
        }
        return true;
    }

    private boolean handleSettingClick(Setting<?> s, int x, int y, int w, double mx, double my, int btn) {
        if (s instanceof BooleanSetting bs) {
            int boxX = x + w - 20;
            if (mx >= boxX && mx <= boxX + 20 && my >= y + 1 && my <= y + 13) {
                bs.setValue(!bs.getValue());
                if (!NavineClient.isGhost()) {
                    NavineSoundManager.playClick();
                }
                ConfigManager.save();
                return true;
            }
        } else if (s instanceof NumberSetting ns) {
            int resetBtnW = 10;
            int resetBtnX = x + w - resetBtnW;
            if (mx >= resetBtnX && mx <= resetBtnX + resetBtnW && my >= y && my <= y + 10) {
                ns.reset();
                ConfigManager.save();
                return true;
            }

            int barY = y + 12;
            if (mx >= x && mx <= x + w && my >= barY - 6 && my <= barY + 14) {
                draggingSlider = ns;
                updateSlider(mx, my);
                ConfigManager.save();
                return true;
            }
        } else if (s instanceof ModeSetting ms) {
            int modeY = y + 9;
            int arrowW = 10;
            if (my >= modeY && my <= modeY + 9) {
                if (mx >= x && mx <= x + arrowW) {
                    ms.cycleBack();
                    ConfigManager.save();
                    return true;
                }
                if (mx >= x + w - arrowW && mx <= x + w) {
                    ms.cycle();
                    ConfigManager.save();
                    return true;
                }
            }
            if (mx >= x && mx <= x + w && my >= modeY && my <= modeY + 9) {
                if (btn == 0) ms.cycle();
                else if (btn == 1) ms.cycleBack();
                ConfigManager.save();
                return true;
            }
        } else if (s instanceof ColorSetting cs) {
            int colorBoxX = x + w - 44;
            int colorBoxW = 36;
            if (mx >= colorBoxX && mx <= colorBoxX + colorBoxW && my >= y + 1 && my <= y + 13) {
                int current = cs.getValue();
                int[] presets = {
                        0xFF87CEEB,
                        0xFFFF0000,
                        0xFF00FF00,
                        0xFF0000FF,
                        0xFFFFFF00,
                        0xFFFF00FF,
                        0xFF00FFFF,
                        0xFFFFFFFF,
                        0xFF8B5CF6,
                        0xFFFFA500,
                };
                int nextIndex = 0;
                int currentRgb = current & 0x00FFFFFF;
                for (int i = 0; i < presets.length; i++) {
                    if ((presets[i] & 0x00FFFFFF) == currentRgb) {
                        nextIndex = (i + 1) % presets.length;
                        break;
                    }
                }
                cs.setValue(presets[nextIndex]);
                ConfigManager.save();
                return true;
            }
            for (int i = 0; i < 3; i++) {
                int barY = y + 16 + i * 12;
                int barX = x + 14;
                int barW = w - 34;
                if (mx >= barX && mx <= barX + barW && my >= barY && my <= barY + 10) {
                    draggingColorSetting = cs;
                    draggingColorChannel = i;
                    updateColorSlider(mx, my);
                    ConfigManager.save();
                    return true;
                }
            }
        } else if (s instanceof StringSetting ss) {
            int boxY = y + 12;
            int boxH = 14;
            if (mx >= x && mx <= x + w && my >= boxY && my <= boxY + boxH) {
                editingStringSetting = ss;
                stringEditBuffer = ss.getValue() == null ? "" : ss.getValue();
                searchFocused = false;
                listeningKey = false;
                return true;
            }
        }
        return false;
    }

    private void handleMouseReleased(double mx, double my, int button) {
        draggingSlider = null;
        draggingColorSetting = null;
        draggingColorChannel = -1;

        if (button == 0) {
            draggingSettingsPanel = false;
            pendingSettingsDrag = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (settingsModule != null) {
            SettingsLayout layout = computeSettingsLayout(settingsAnim);
            double[] local = new double[2];
            toSettingsLocal(mx, my, layout, local);
            int settingsTop = 22;
            int settingsBottom = layout.panelH - 42;
            int settingsAreaH = Math.max(0, settingsBottom - settingsTop);
            int totalSettingsH = totalSettingsHeight(settingsModule);
            int maxSettingsScroll = Math.max(0, totalSettingsH - settingsAreaH);

            if (local[0] >= 0 && local[0] <= layout.panelW
                    && local[1] >= settingsTop && local[1] <= settingsBottom) {
                settingsScroll -= (int) (vAmount * 16);
                settingsScroll = Math.max(0, Math.min(settingsScroll, maxSettingsScroll));
                return true;
            }
        }

        if (mx >= moduleListX && mx <= moduleListX + moduleListW
                && my >= moduleListY && my <= moduleListY + moduleListH) {
            int scroll = getModuleScroll() - (int) (vAmount * 10);
            setModuleScroll(Math.max(0, Math.min(scroll, maxModuleScroll)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    private void renderSearchText(GuiGraphicsExtractor ctx, int x, int y, String text, String query, int alpha) {
        int cursorX = x;
        String lowerText = text.toLowerCase();
        int queryLen = query.length();
        int i = 0;
        while (i < text.length()) {
            int highlightStart = lowerText.indexOf(query, i);
            if (queryLen == 0 || highlightStart < 0) {
                ctx.text(font, text.substring(i), cursorX, y, applyAlpha(NavineTheme.TEXT_PRIMARY, alpha), false);
                break;
            }
            if (highlightStart > i) {
                String before = text.substring(i, highlightStart);
                ctx.text(font, before, cursorX, y, applyAlpha(NavineTheme.TEXT_PRIMARY, alpha), false);
                cursorX += mc.font.width(before);
            }
            String match = text.substring(highlightStart, highlightStart + queryLen);
            int matchW = mc.font.width(match);
            ctx.fill(cursorX, y - 1, cursorX + matchW, y + 9, applyAlpha(NavineTheme.searchHighlight(), alpha));
            ctx.text(font, match, cursorX, y, applyAlpha(NavineTheme.accent(), alpha), false);
            cursorX += matchW;
            i = highlightStart + queryLen;
        }
    }

    private void renderHighlightedModuleName(GuiGraphicsExtractor ctx, Module m, int x, int y, int baseColor, int maxWidth, int alpha) {
        String name = m.getName();
        String lowerName = name.toLowerCase();
        String normalizedName = lowerName.replace("_", " ").replace("-", " ");
        String compactName = normalizedName.replace(" ", "");
        String compactQuery = searchQuery.replace(" ", "");
        int matchStart = -1;
        int matchLen = searchQuery.length();
        if (matchLen > 0) {
            matchStart = lowerName.indexOf(searchQuery);
            if (matchStart < 0) {
                matchStart = normalizedName.indexOf(searchQuery);
            }
            if (matchStart < 0 && !compactQuery.isEmpty()) {
                matchStart = compactName.indexOf(compactQuery);
                matchLen = compactQuery.length();
            }
        }
        if (matchStart < 0) {
            String desc = m.getDescription();
            if (desc != null && desc.toLowerCase().contains(searchQuery)) {
                ctx.text(mc.font, name, x, y, applyAlpha(NavineTheme.TEXT_SECONDARY, alpha), false);
                return;
            }
            ctx.text(mc.font, name, x, y, baseColor, false);
            return;
        }
        int cursorX = x;
        if (matchStart > 0) {
            String before = name.substring(0, matchStart);
            ctx.text(mc.font, before, cursorX, y, baseColor, false);
            cursorX += mc.font.width(before);
        }
        String match = name.substring(matchStart, Math.min(name.length(), matchStart + matchLen));
        int matchW = mc.font.width(match);
        ctx.fill(cursorX, y - 1, cursorX + matchW, y + 9, applyAlpha(NavineTheme.searchHighlight(), alpha));
        ctx.text(mc.font, match, cursorX, y, applyAlpha(NavineTheme.accent(), alpha), false);
        cursorX += matchW;
        if (matchStart + matchLen < name.length()) {
            String after = name.substring(matchStart + matchLen);
            if (cursorX + mc.font.width(after) <= x + maxWidth) {
                ctx.text(mc.font, after, cursorX, y, baseColor, false);
            }
        }
    }

    private int applyAlpha(int color, int alpha) {
        int a = (color >>> 24) & 0xFF;
        int newA = Math.min(255, (a * alpha) / 255);
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    private static final class SettingsLayout {
        final int panelW;
        final int panelH;
        final float scale;
        final int scaledW;
        final int scaledH;
        final int panelX;
        final int panelY;

        SettingsLayout(int panelW, int panelH, float scale, int scaledW, int scaledH, int panelX, int panelY) {
            this.panelW = panelW;
            this.panelH = panelH;
            this.scale = scale;
            this.scaledW = scaledW;
            this.scaledH = scaledH;
            this.panelX = panelX;
            this.panelY = panelY;
        }
    }

    private static final class TabEntry {
        final String id;
        final String label;
        final CategoryKey categoryKey;
        int x;
        int y;
        int width;
        int height;

        private TabEntry(String id, String label, CategoryKey categoryKey) {
            this.id = id;
            this.label = label;
            this.categoryKey = categoryKey;
        }

        static TabEntry category(CategoryKey key) {
            return new TabEntry(key.storageId(), key.getDisplayName(), key);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
