package nv.navineclient.commands;

import nv.navineclient.util.ChatUtils;

public class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix", "Change command prefix", ".prefix <char>");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 1) {
            ChatUtils.message("§cSyntax: .prefix <char>");
            return;
        }
        CommandManager.setPrefix(args[0]);
        ChatUtils.message("Prefix changed to: " + args[0]);
    }
}
