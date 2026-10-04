package nv.navineclient.commands;

import nv.navineclient.util.ChatUtils;
import java.util.ArrayList;
import java.util.List;

public abstract class Command {
    private final String name;
    private final String description;
    private final String syntax;
    private final String[] aliases;

    public Command(String name, String description, String syntax, String... aliases) {
        this.name = name;
        this.description = description;
        this.syntax = syntax;
        this.aliases = aliases;
    }

    public String getName() { return this.name; }
    public String getDescription() { return this.description; }
    public String getSyntax() { return this.syntax; }
    public String[] getAliases() { return this.aliases; }

    public abstract void onCommand(String[] args);
    
    public List<String> getCompletions(String[] args) {
        return new ArrayList<>();
    }

    public boolean isVisibleToUser() {
        return true;
    }
    
    public void showUsage() {
        ChatUtils.message("§7Usage: §f" + syntax);
    }
}
