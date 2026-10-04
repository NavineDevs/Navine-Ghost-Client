package nv.navineclient;

import nv.navineclient.commands.Command;
import nv.navineclient.module.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class NavineAddon {
    public static final Logger LOG = LoggerFactory.getLogger("NavineAddon");

    private final List<Command> addonCommands = new ArrayList<>();
    private final List<Module> addonModules = new ArrayList<>();

    public abstract void onInitialize();

    public void onRegisterCategories() {
    }

    protected void registerCategory(String id, String displayName) {
        nv.navineclient.module.CategoryRegistry.register(id, displayName);
    }

    public abstract String getPackage();

    public String getName() {
        return getClass().getSimpleName();
    }

    public String getVersion() {
        return "1.0.0";
    }

    public List<String> getAuthors() {
        return Collections.singletonList("Unknown");
    }

    protected void registerCommand(Command command) {
        addonCommands.add(command);
    }

    protected void registerModule(Module module) {
        addonModules.add(module);
    }

    public List<Command> getCommands() {
        return new ArrayList<>(addonCommands);
    }

    public List<Module> getModules() {
        return new ArrayList<>(addonModules);
    }
}
