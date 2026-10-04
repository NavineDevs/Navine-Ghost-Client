package nv.navineclient.commands;

import nv.navineclient.util.ChatUtils;

public class SayCommand extends Command {
    public SayCommand() {
        super("say", "Sends a message to the chat.", ".say <message>");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            ChatUtils.message("Usage: .say <message>");
            return;
        }
        String message = String.join(" ", args);
        ChatUtils.sendChat(message);
    }
}
