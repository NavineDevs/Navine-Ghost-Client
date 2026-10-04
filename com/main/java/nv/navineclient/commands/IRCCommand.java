package nv.navineclient.commands;

import nv.navineclient.util.ClientScreens;
import nv.navineclient.ui.IRCLogScreen;
import nv.navineclient.util.AuthManager;
import nv.navineclient.util.ChatUtils;
import nv.navineclient.util.IRCClient;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IRCCommand extends Command {
    private static final List<String> SUBCOMMANDS = Arrays.asList("connect", "disconnect", "say", "dm", "list", "logs", "help");

    public IRCCommand() {
        super("irc", "Navine IRC chat system", ".irc <connect|say|dm|logs>", new String[]{"chat"});
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length == 0) {
            openLogScreen();
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "connect" -> handleConnect();
            case "disconnect" -> handleDisconnect();
            case "say" -> handleSay(args);
            case "dm" -> handleDM(args);
            case "list", "logs" -> openLogScreen();
            case "help" -> showHelp();
            default -> {
                if (IRCClient.isConnected()) {
                    String message = String.join(" ", args);
                    sendIrcMessage(message);
                } else {
                    ChatUtils.message("§cUnknown subcommand. Use §f.irc help§c for options.");
                }
            }
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        }
        return completions;
    }

    private void showHelp() {
        String prefix = CommandManager.getPrefix();
        ChatUtils.message("§b§l=== Navine IRC ===");
        ChatUtils.message("§f" + prefix + "irc connect §7- Connect to IRC");
        ChatUtils.message("§f" + prefix + "irc disconnect §7- Disconnect from IRC");
        ChatUtils.message("§f" + prefix + "irc say <message> §7- Send message to IRC");
        ChatUtils.message("§f" + prefix + "irc dm <user> <message> §7- Send DM to user");
        ChatUtils.message("§f" + prefix + "irc logs §7- Open IRC chat log viewer");
        ChatUtils.message("");
        ChatUtils.message("§7IRC Status: " + (IRCClient.isConnected() ? "§aConnected" : "§c" + IRCClient.getStatus()));
        if (AuthManager.isVerified()) {
            String mcName = AuthManager.getMinecraftUsername();
            String tag = AuthManager.getTagForIRC(AuthManager.getIrcRankUser());
            ChatUtils.message("§7Your tag: §f" + tag + " " + mcName);
        }
    }

    private void handleConnect() {
        if (!AuthManager.isVerified()) {
            ChatUtils.message("§cYou must login first! Use §f.login <username>");
            return;
        }

        if (IRCClient.isConnected()) {
            ChatUtils.message("§eAlready connected to IRC!");
            return;
        }

        String mcName = AuthManager.getMinecraftUsername();
        IRCClient.connect(mcName);
        ChatUtils.message("§7[IRC] Connecting...");
    }

    private String resolveIrcUsername() {
        return AuthManager.getMinecraftUsername();
    }

    private String resolveIrcRankUser() {
        return AuthManager.getIrcRankUser();
    }

    private void handleDisconnect() {
        if (!IRCClient.isConnected()) {
            ChatUtils.message("§cNot connected to IRC!");
            return;
        }

        IRCClient.disconnect();
        ChatUtils.message("§c[Navine Client] Disconnected from Navine IRC");
    }

    private void handleSay(String[] args) {
        if (!IRCClient.isConnected()) {
            ChatUtils.message("§cNot connected! Use §f.irc connect");
            return;
        }

        if (args.length < 2) {
            ChatUtils.message("§cUsage: .irc say <message>");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        sendIrcMessage(message);
    }

    private void handleDM(String[] args) {
        if (!IRCClient.isConnected()) {
            ChatUtils.message("§cNot connected! Use §f.irc connect");
            return;
        }

        if (args.length < 3) {
            ChatUtils.message("§cUsage: .irc dm <user> <message>");
            return;
        }

        String target = args[1];
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String username = resolveIrcUsername();
        String rankUser = resolveIrcRankUser();
        String tag = AuthManager.getTagForIRC(rankUser);
        String color = AuthManager.getTagColor(rankUser);

        ChatUtils.message("§d[DM -> " + target + "] §8[" + color + tag.replace("[", "").replace("]", "") + "§8] §f" + username + "§7: " + message);
        IRCClient.addLogEntry(username, message, IRCClient.LogType.DM, target);
        IRCClient.sendDirectMessage(target, message);
    }

    private void openLogScreen() {
        IRCClient.requestLogs();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> ClientScreens.set(mc, new IRCLogScreen(ClientScreens.get(mc))));
        }
    }

    private void sendIrcMessage(String message) {
        String username = resolveIrcUsername();
        String rankUser = resolveIrcRankUser();
        String tag = AuthManager.getTagForIRC(rankUser);
        String color = AuthManager.getTagColor(rankUser);

        ChatUtils.message("§8[" + color + tag.replace("[", "").replace("]", "") + "§8] §f" + username + "§7: " + message);
        IRCClient.addLogEntry(username, message, IRCClient.LogType.SAY);
        IRCClient.sendMessage(message);
    }
}
