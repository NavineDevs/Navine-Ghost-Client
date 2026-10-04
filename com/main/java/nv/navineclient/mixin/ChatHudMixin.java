package nv.navineclient.mixin;

import nv.navineclient.util.AuthManager;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component onAddMessage(Component message) {
        String content = message.getString();

        // Announcement logic
        if (content.contains("§9§0§9§0")) {
            String msg = content.replace("§9§0§9§0", "").trim();
            String announcer = "Owner";
            if (content.contains("<") && content.contains(">")) {
                announcer = content.substring(content.indexOf("<") + 1, content.indexOf(">"));
            } else if (content.contains(":") && content.indexOf(":") < content.indexOf("§9§0§9§0")) {
                announcer = content.substring(0, content.indexOf(":")).trim();
            }
            return Component.literal("§8[§dNavine Owner§8] §b" + announcer + "§f: " + msg);
        }

        // Regular Tag logic - handle chat messages with <username> format
        if (content.contains("<") && content.contains(">")) {
            int start = content.indexOf("<") + 1;
            int end = content.indexOf(">");
            if (start < end) {
                String username = content.substring(start, end);
                String tag = AuthManager.getTagForPlayer(username);
                if (!tag.isEmpty() && !content.contains("[Navine")) {
                    // Prepend tag, preserving server prefixes
                    return Component.literal(tag).append(message);
                }
            }
        } else if (content.contains(":") && !content.startsWith("[")) {
            // Check for format "User: message" or "[Server] User: message"
            String possibleUser = content.split(":")[0].trim();
            // Remove server prefixes/tags to get username
            if (possibleUser.contains(" ")) {
                possibleUser = possibleUser.substring(possibleUser.lastIndexOf(" ") + 1);
            }
            if (possibleUser.split(" ").length == 1 && !possibleUser.contains("[") && !possibleUser.contains("]")) {
                String tag = AuthManager.getTagForPlayer(possibleUser);
                if (!tag.isEmpty() && !content.contains("[Navine")) {
                    // Prepend tag before username
                    return Component.literal(tag).append(message);
                }
            }
        }
        
        // Handle DM format: "[DM] User → You: message"
        if (content.contains("[DM]") || content.contains("→")) {
            String[] parts = content.split("→");
            if (parts.length >= 2) {
                String senderPart = parts[0].trim();
                // Extract username from sender part
                String username = senderPart;
                if (senderPart.contains("]")) {
                    username = senderPart.substring(senderPart.lastIndexOf("]") + 1).trim();
                } else if (senderPart.contains(" ")) {
                    username = senderPart.substring(senderPart.lastIndexOf(" ") + 1).trim();
                }
                String tag = AuthManager.getTagForPlayer(username);
                if (!tag.isEmpty() && !content.contains("[Navine")) {
                    // Format: "[DM] [Navine Tag] User → You: message"
                    String dmPrefix = content.contains("[DM]") ? "§7[DM] " : "";
                    return Component.literal(dmPrefix + tag + username + " §7→ You: " + parts[1].trim());
                }
            }
        }
        
        return message;
    }
}
