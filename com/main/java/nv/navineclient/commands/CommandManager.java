package nv.navineclient.commands;

import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    private static final List<Command> commands = new ArrayList<>();
    private static String prefix = ".";
    
    public static void init() {
        commands.clear();
        if (nv.navineclient.NavineClient.GHOST) {
            registerGhostCommands();
        } else {
            registerFullCommands();
        }
    }

    private static void registerGhostCommands() {
        commands.add(new HelpCommand());
        commands.add(new ToggleCommand());
        commands.add(new BindCommand());
        commands.add(new PrefixCommand());
        commands.add(new PanicCommand());
        commands.add(new VersionCommand());
        commands.add(new CoordsCommand());
        commands.add(new PingCommand());
        commands.add(new ClearCommand());
        commands.add(new XRayCommand());
        commands.add(new LoginCommand());
        commands.add(new LogoutCommand());
    }

    private static void registerFullCommands() {
        commands.add(new HelpCommand());
        commands.add(new ToggleCommand());
        commands.add(new BindCommand());
        commands.add(new PrefixCommand());
        commands.add(new PanicCommand());
        commands.add(new VersionCommand());
        commands.add(new ReloadCommand());
        commands.add(new CoordsCommand());
        commands.add(new PingCommand());
        commands.add(new SeedCommand());
        commands.add(new GamemodeCommand());
        commands.add(new SpectateCommand());
        commands.add(new InvseeCommand());
        commands.add(new EnderChestCommand());
        commands.add(new SayCommand());
        commands.add(new MsgCommand());
        commands.add(new ClearCommand());
        commands.add(new TeleportCommand());
        commands.add(new VClipCommand());
        commands.add(new HClipCommand());
        commands.add(new TimeCommand());
        commands.add(new WaypointsCommand());
        commands.add(new GiveCommand());
        commands.add(new XRayCommand());
        commands.add(new DupeCommand());
        commands.add(new ServerCommand());
        commands.add(new IRCCommand());
        commands.add(new DiscordCommand());
        commands.add(new BaritoneCommand());
        commands.add(new OwnerCommand());
        commands.add(new DevCommand());
        commands.add(new LoginCommand());
        commands.add(new LogoutCommand());
    }
    
    public static void handleCommand(String message) {
        if (!message.startsWith(prefix)) return;
        String[] split = message.substring(prefix.length()).split(" ");
        String label = split[0];
        String[] args = Arrays.copyOfRange(split, 1, split.length);
        
        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(label) || Arrays.asList(command.getAliases()).contains(label.toLowerCase())) {
                if (!nv.navineclient.util.AuthManager.isVerified() && !isAuthFree(command)) {
                    ChatUtils.message("§cLogin required. Use §f" + prefix + "login <user>");
                    return;
                }
                command.onCommand(args);
                return;
            }
        }
        ChatUtils.message("§cUnknown command! Type §f" + prefix + "help§c for a list of commands.");
    }
    
    public static List<String> getCompletions(String input) {
        List<String> completions = new ArrayList<>();
        if (!input.startsWith(prefix)) return completions;
        
        String withoutPrefix = input.substring(prefix.length());
        String[] parts = withoutPrefix.split(" ", -1);
        
        if (parts.length <= 1) {
            String cmdInput = parts[0].toLowerCase();
            for (Command cmd : commands) {
                if (!cmd.isVisibleToUser()) {
                    continue;
                }
                if (cmd.getName().toLowerCase().startsWith(cmdInput)) {
                    completions.add(prefix + cmd.getName());
                }
                for (String alias : cmd.getAliases()) {
                    if (alias.toLowerCase().startsWith(cmdInput)) {
                        completions.add(prefix + alias);
                    }
                }
            }
        } else {
            String cmdName = parts[0].toLowerCase();
            String[] args = Arrays.copyOfRange(parts, 1, parts.length);
            
            for (Command cmd : commands) {
                if (!cmd.isVisibleToUser()) {
                    continue;
                }
                if (cmd.getName().equalsIgnoreCase(cmdName) || Arrays.asList(cmd.getAliases()).contains(cmdName)) {
                    List<String> cmdCompletions = cmd.getCompletions(args);
                    for (String completion : cmdCompletions) {
                        String base = prefix + cmdName;
                        for (int i = 0; i < args.length - 1; i++) {
                            base += " " + args[i];
                        }
                        completions.add(base + " " + completion);
                    }
                    break;
                }
            }
        }
        return completions;
    }
    
    private static boolean isAuthFree(Command command) {
        String name = command.getName();
        return "login".equalsIgnoreCase(name) || "help".equalsIgnoreCase(name) || "logout".equalsIgnoreCase(name);
    }

    public static List<Command> getCommands() { return commands; }
    public static String getPrefix() { return prefix; }
    public static void setPrefix(String p) { prefix = p; }
    
    /**
     * Register a command from an addon.
     */
    public static void registerCommand(Command command) {
        if (command == null) return;
        // Check if command already exists
        for (Command cmd : commands) {
            if (cmd.getName().equalsIgnoreCase(command.getName())) {
                nv.navineclient.NavineClient.LOGGER.warn("Command " + command.getName() + " is already registered, skipping addon command");
                return;
            }
        }
        commands.add(command);
    }
}
