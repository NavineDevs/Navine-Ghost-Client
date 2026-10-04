package nv.navineclient.commands;

import nv.navineclient.util.ClientAccess;
import net.minecraft.client.Minecraft;
import nv.navineclient.util.ChatUtils;

public class ClearCommand extends Command {
    public ClearCommand() {
        super("clear", "Clears the chat", ".clear");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            ClientAccess.getChat(mc).clearMessages(false);
            ChatUtils.message("Chat cleared!");
        }
    }
}
