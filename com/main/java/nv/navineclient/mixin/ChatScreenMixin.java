package nv.navineclient.mixin;

import nv.navineclient.util.NavineInput;
import nv.navineclient.util.ClientScreens;
import nv.navineclient.commands.CommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import nv.navineclient.util.NavineInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Shadow protected EditBox input;
    @Shadow private boolean closeOnSubmit;

    @Unique private List<String> navine_suggestions = List.of();
    @Unique private int navine_selectedSuggestion = -1;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void navineHandleClientCommandKeys(KeyEvent keyInput, CallbackInfoReturnable<Boolean> cir) {
        if (input == null) {
            return;
        }
        String message = input.getValue();
        if (message == null || message.isEmpty()) {
            navine_suggestions = List.of();
            navine_selectedSuggestion = -1;
            return;
        }
        String trimmed = message.trim();
        String prefix = CommandManager.getPrefix();
        if (!trimmed.startsWith(prefix)) {
            navine_suggestions = List.of();
            navine_selectedSuggestion = -1;
            return;
        }

        navine_suggestions = CommandManager.getCompletions(trimmed);
        int key = keyInput.key();

        if (key == NavineInput.KEY_TAB) {
            if (!navine_suggestions.isEmpty()) {
                int index = navine_selectedSuggestion >= 0 ? navine_selectedSuggestion : 0;
                input.setValue(navine_suggestions.get(index));
                input.moveCursorToEnd(false);
                navine_suggestions = List.of();
                navine_selectedSuggestion = -1;
                cir.setReturnValue(true);
            }
            return;
        }

        if (key == NavineInput.KEY_DOWN && !navine_suggestions.isEmpty()) {
            navine_selectedSuggestion = Math.min(Math.max(navine_selectedSuggestion + 1, 0), navine_suggestions.size() - 1);
            cir.setReturnValue(true);
            return;
        }

        if (key == NavineInput.KEY_UP && !navine_suggestions.isEmpty()) {
            navine_selectedSuggestion = Math.max(navine_selectedSuggestion - 1, 0);
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "keyPressed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/CommandSuggestions;hasAllowedInput()Z"
            ),
            require = 0
    )
    private boolean navineAllowClientCommands(CommandSuggestions suggestions) {
        if (input != null) {
            String message = input.getValue().trim();
            if (!message.isEmpty() && message.startsWith(CommandManager.getPrefix())) {
                return true;
            }
        }
        return suggestions.hasAllowedInput();
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (nv.navineclient.ChatCommandBridge.tryHandleClientCommand(message, addToHistory)) {
            nv.navineclient.ChatCommandBridge.markMixinChannel();
            ci.cancel();
            if (closeOnSubmit) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    ClientScreens.set(mc, null);
                }
            } else if (input != null) {
                input.setValue("");
            }
            navine_suggestions = List.of();
            navine_selectedSuggestion = -1;
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (input == null) {
            return;
        }
        String text = input.getValue();
        String prefix = CommandManager.getPrefix();

        if (text.startsWith(prefix) && text.length() > prefix.length()) {
            navine_suggestions = CommandManager.getCompletions(text.trim());

            if (!navine_suggestions.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                int x = input.getX() + 2;
                int y = input.getY() - 4 - (navine_suggestions.size() * 12);

                int maxWidth = 0;
                for (String s : navine_suggestions) {
                    maxWidth = Math.max(maxWidth, mc.font.width(s));
                }

                context.fill(x - 2, y - 2, x + maxWidth + 4, input.getY() - 2, 0xE0101020);
                context.fill(x - 2, y - 2, x - 1, input.getY() - 2, 0xFFb48eff);

                for (int i = 0; i < navine_suggestions.size(); i++) {
                    String suggestion = navine_suggestions.get(i);
                    int color = (i == navine_selectedSuggestion) ? 0xFFb48eff : 0xFFaaaaaa;
                    if (i == navine_selectedSuggestion) {
                        context.fill(x - 1, y + i * 12 - 1, x + maxWidth + 3, y + i * 12 + 11, 0x40b48eff);
                    }
                    context.text(mc.font, suggestion, x, y + i * 12, color, false);
                }
            }
        } else {
            navine_suggestions = List.of();
            navine_selectedSuggestion = -1;
        }
    }
}
