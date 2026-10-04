package nv.navineclient.module.misc;

import nv.navineclient.integration.baritone.BaritoneBridge;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.ModeSetting;
import nv.navineclient.module.settings.StringSetting;
import nv.navineclient.util.ChatUtils;
import nv.navineclient.util.NavinePathfinder;

public class BaritoneModule extends Module {
    public static BaritoneModule INSTANCE;

    private final BooleanSetting allowBreak = new BooleanSetting("Break Blocks", "Break blocks that obstruct the path", true);
    private final BooleanSetting bridge = new BooleanSetting("Bridge", "Place blocks at gaps", false);
    private final BooleanSetting sprint = new BooleanSetting("Sprint", "Sprint while pathfinding", true);
    private final BooleanSetting notifyStatus = new BooleanSetting("Notify", "Notify pathfinder status on enable", true);
    private final ModeSetting quickAction = new ModeSetting("Quick Action", "Run a pathfinder command on enable", "None", "None", "Stop", "Goto", "Help");
    private final StringSetting customCommand = new StringSetting("Custom Command", "Command for quick action", "goto 0 64 0");

    public BaritoneModule() {
        super("Pathfinder", "Built-in pathfinding: goto, mine, follow", Category.MOVEMENT);
        INSTANCE = this;
        addSetting(allowBreak);
        addSetting(bridge);
        addSetting(sprint);
        addSetting(notifyStatus);
        addSetting(quickAction);
        addSetting(customCommand);
    }

    public boolean shouldSprint() {
        return sprint.getValue();
    }

    @Override
    public void onEnable() {
        NavinePathfinder.setBridgeMode(bridge.getValue());
        NavinePathfinder.setAllowBreak(allowBreak.getValue());
        if (notifyStatus.getValue()) {
            ChatUtils.success("Pathfinder ready. Use .baritone goto <x> <z>");
        }
        String action = quickAction.getValue();
        if (!"None".equals(action)) {
            runQuickAction(action);
        }
    }

    @Override
    public void onTick() {
        NavinePathfinder.setBridgeMode(bridge.getValue());
        NavinePathfinder.setAllowBreak(allowBreak.getValue());
    }

    @Override
    public void onDisable() {
        NavinePathfinder.stop();
    }

    public void runQuickAction(String action) {
        switch (action) {
            case "Stop" -> BaritoneBridge.executeCommand("stop");
            case "Goto" -> BaritoneBridge.executeCommand(customCommand.getValue());
            case "Help" -> ChatUtils.message("Use .baritone goto <x> <z>, .baritone mine <block>, .baritone follow <player>, .baritone stop");
            default -> {
            }
        }
    }

    public void runCustomCommand() {
        BaritoneBridge.executeCommand(customCommand.getValue());
    }
}
