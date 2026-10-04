package nv.navineclient.mixin;

import nv.navineclient.util.ClientScreens;
import nv.navineclient.AddonManager;
import nv.navineclient.NavineClient;
import nv.navineclient.ui.AddonManagerScreen;
import nv.navineclient.ui.CoolButton;
import nv.navineclient.ui.LoginScreen;
import nv.navineclient.ui.NavineSettingsScreen;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.BackgroundManager;
import nv.navineclient.util.MenuMusicManager;
import nv.navineclient.util.NavineTheme;
import nv.navineclient.util.RenderUtil;
import nv.navineclient.util.ScreenBackgroundHelper;
import nv.navineclient.util.UiAnim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final int MENU_BTN_H = 18;
    private static final int MENU_BTN_GAP = 6;
    private static final int MENU_PANEL_PAD = 8;
    private static final int MENU_TOP_Y = 4;
    private static final int TOP_BAR_H = 36;
    private int menuPanelX;
    private int menuPanelY;
    private int menuPanelW;
    private int menuPanelH;
    private float screenAnim;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!NavineClient.isGhost()) {
            BackgroundManager.loadExternalBackground();
            MenuMusicManager.onTitleScreenOpen();
        }
        clearWidgets();
        screenAnim = 0f;

        if (!AuthManager.isVerified()) {
            layoutUnverifiedButtons();
        } else {
            layoutVerifiedButtons();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
    }

    private void layoutUnverifiedButtons() {
        List<String> labels = List.of("Login", "Quit Game");
        layoutMenuButtons(labels, this::openUnverifiedAction);
    }

    private void layoutVerifiedButtons() {
        List<String> labels = new ArrayList<>();
        labels.add("Singleplayer");
        labels.add("Multiplayer");
        labels.add("Realms");
        if (showAddonsButton()) {
            labels.add("Addons");
        }
        labels.add("Settings");
        labels.add("Options");
        labels.add("Quit");
        layoutMenuButtons(labels, this::openVerifiedAction);
    }

    private void layoutMenuButtons(List<String> labels, java.util.function.Consumer<String> action) {
        int count = labels.size();
        menuPanelW = Math.max(200, this.width - 40);
        menuPanelX = (this.width - menuPanelW) / 2;
        menuPanelY = MENU_TOP_Y;
        menuPanelH = MENU_PANEL_PAD * 2 + MENU_BTN_H;

        int innerW = menuPanelW - MENU_PANEL_PAD * 2;
        int btnW = count > 0 ? (innerW - (count - 1) * MENU_BTN_GAP) / count : innerW;
        int rowWidth = count * btnW + Math.max(0, count - 1) * MENU_BTN_GAP;
        int startX = menuPanelX + MENU_PANEL_PAD + (innerW - rowWidth) / 2;
        int y = menuPanelY + MENU_PANEL_PAD;

        for (int i = 0; i < count; i++) {
            String label = labels.get(i);
            int x = startX + i * (btnW + MENU_BTN_GAP);
            CoolButton button = new CoolButton(x, y, btnW, MENU_BTN_H,
                    Component.literal(label), b -> action.accept(label), true);
            button.setRevealOrder(i);
            addRenderableWidget(button);
        }
    }

    private void openUnverifiedAction(String label) {
        if (this.minecraft == null) {
            return;
        }
        if ("Login".equals(label)) {
            ClientScreens.set(this.minecraft, new LoginScreen(this));
        } else {
            this.minecraft.stop();
        }
    }

    private String truncateToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        String trimmed = text;
        while (trimmed.length() > 0 && this.font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    private void openVerifiedAction(String label) {
        if (this.minecraft == null) return;
        switch (label) {
            case "Singleplayer" -> ClientScreens.set(this.minecraft, new SelectWorldScreen(this));
            case "Multiplayer" -> ClientScreens.set(this.minecraft, new JoinMultiplayerScreen(this));
            case "Realms" -> ClientScreens.set(this.minecraft, new RealmsMainScreen(this));
            case "Addons" -> openAddonManager();
            case "Settings" -> ClientScreens.set(this.minecraft, new NavineSettingsScreen(this));
            case "Options" -> ClientScreens.set(this.minecraft, new OptionsScreen(this, this.minecraft.options));
            case "Quit" -> this.minecraft.stop();
        }
    }

    private void openAddonManager() {
        if (this.minecraft != null && showAddonsButton()) {
            ClientScreens.set(this.minecraft, new AddonManagerScreen(this));
        }
    }

    private boolean showAddonsButton() {
        return AuthManager.isVerified() && !AddonManager.getAddons().isEmpty();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!NavineClient.isGhost()) {
            MenuMusicManager.tick();
        }
        screenAnim = UiAnim.approach(screenAnim, 1f, 7f, delta);
        float panelEase = UiAnim.easeOutCubic(screenAnim);
        int panelSlide = (int) ((1f - panelEase) * -14f);
        int panelDrawY = menuPanelY + panelSlide;
        int topBarH = Math.max(TOP_BAR_H, menuPanelH > 0 ? panelDrawY + menuPanelH + 4 : TOP_BAR_H);

        if (NavineClient.isGhost()) {
            ScreenBackgroundHelper.renderMenuBackground(context, this.width, this.height);
        } else if (BackgroundManager.hasBackground()) {
            BackgroundManager.renderBackground(context, this.width, this.height);
        } else {
            context.fillGradient(0, 0, this.width, this.height, 0xFF030306, 0xFF050508);
        }

        context.fill(0, 0, this.width, this.height, 0x90000000);
        context.fill(0, 0, this.width, topBarH, 0x30000000);
        context.fill(0, topBarH, this.width, topBarH + 1, 0x40FFFFFF);

        if (menuPanelH > 0) {
            int panelBg = UiAnim.scaleAlpha(NavineTheme.panelBg() & 0x00FFFFFF | 0x90000000, panelEase);
            int panelBorder = UiAnim.scaleAlpha(NavineTheme.accent(), panelEase);
            int panelAccent = UiAnim.scaleAlpha(NavineTheme.accent2(), panelEase);
            RenderUtil.drawRoundedRect(context, menuPanelX, panelDrawY, menuPanelW, menuPanelH, 4, panelBg);
            RenderUtil.drawRoundedOutline(context, menuPanelX, panelDrawY, menuPanelW, menuPanelH, 4, 1, panelBorder);
            context.fill(menuPanelX + 2, panelDrawY + menuPanelH - 1, menuPanelX + menuPanelW - 2, panelDrawY + menuPanelH, panelAccent);
        }

        String mcVer = this.minecraft != null ? SharedConstants.getCurrentVersion().name() : "26.1.2";
        String ver = "v" + NavineClient.VERSION + " (" + mcVer + ")";
        int verWidth = this.font.width(ver);
        int footerY = this.height - 15;

        if (!AuthManager.isVerified()) {
            String notLoggedIn = "YOU ARE NOT LOGGED IN";
            int textWidth = this.font.width(notLoggedIn);
            context.text(this.font, notLoggedIn, (this.width - textWidth) / 2, this.height / 2 - 40, 0xFFFFFFFF, true);
            context.text(this.font, ver, this.width - verWidth - 5, footerY, 0xFFAAAAAA, true);
        } else {
            context.text(this.font, "Made By Navine Team", 5, this.height - 25, NavineTheme.titleAccent(), true);

            String loginUser = AuthManager.getLoggedInUser();
            if (loginUser == null) {
                loginUser = "";
            }
            String prefix = NavineClient.displayName() + " | logged in as ";
            int prefixWidth = this.font.width(prefix);
            int footerX = 5;
            int maxFooterWidth = Math.max(0, this.width - verWidth - 15);
            int maxUserWidth = Math.max(0, maxFooterWidth - prefixWidth);
            String displayUser = truncateToWidth(loginUser, maxUserWidth);

            context.text(this.font, prefix, footerX, footerY, NavineTheme.titleAccent(), true);
            context.text(this.font, displayUser, footerX + prefixWidth, footerY, NavineTheme.titleSecondary(), true);
            context.text(this.font, ver, this.width - verWidth - 5, footerY, 0xFFAAAAAA, true);
        }

        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
        ci.cancel();
    }
}
