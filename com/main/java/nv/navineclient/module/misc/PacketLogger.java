package nv.navineclient.module.misc;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.module.settings.NumberSetting;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PacketLogger extends Module {
    private final BooleanSetting logMovement = new BooleanSetting("LogMovement", "Log movement packets", false);
    private final BooleanSetting logChat = new BooleanSetting("LogChat", "Log chat packets", true);
    private final BooleanSetting logInventory = new BooleanSetting("LogInventory", "Log inventory packets", false);
    private final BooleanSetting logAll = new BooleanSetting("LogAll", "Log all other packets", false);
    private final NumberSetting displayLines = new NumberSetting("DisplayLines", "Lines to keep", 8.0, 1.0, 20.0);

    private final Deque<String> recentPackets = new ArrayDeque<>();

    public PacketLogger() {
        super("PacketLogger", "Log network packets (debug tool)", Category.MISC);
        addSetting(logMovement);
        addSetting(logChat);
        addSetting(logInventory);
        addSetting(logAll);
        addSetting(displayLines);
    }

    @Override
    public void onEnable() {
        addLine("PacketLogger enabled");
    }

    @Override
    public void onDisable() {
        recentPackets.clear();
    }

    public void logOutgoing(Packet<?> packet) {
        if (!isEnabled()) {
            return;
        }
        String name = packet.getClass().getSimpleName();
        if (!matchesFilter(name)) {
            return;
        }
        addLine("OUT " + name);
    }

    public void logIncoming(Packet<?> packet) {
        if (!isEnabled()) {
            return;
        }
        String name = packet.getClass().getSimpleName();
        if (!matchesFilter(name)) {
            return;
        }
        addLine("IN " + name);
    }

    private boolean matchesFilter(String name) {
        if (logAll.getValue()) {
            return true;
        }
        if (shouldLogMovement() && name.contains("Move")) {
            return true;
        }
        if (shouldLogChat() && (name.contains("Chat") || name.contains("SystemChat"))) {
            return true;
        }
        if (shouldLogInventory() && (name.contains("Container") || name.contains("Inventory") || name.contains("SetSlot"))) {
            return true;
        }
        return false;
    }

    private void addLine(String line) {
        String timestamp = String.format("[%02d:%02d]",
                (System.currentTimeMillis() / 1000 / 60) % 60,
                (System.currentTimeMillis() / 1000) % 60);
        recentPackets.addLast(timestamp + " " + line);
        int max = displayLines.getValue().intValue();
        while (recentPackets.size() > max) {
            recentPackets.removeFirst();
        }
    }

    public List<String> getDisplayLines() {
        return new ArrayList<>(recentPackets);
    }

    public boolean shouldLogAny() {
        return logMovement.getValue() || logChat.getValue() || logInventory.getValue() || logAll.getValue();
    }

    public boolean shouldLogMovement() {
        return logMovement.getValue();
    }

    public boolean shouldLogChat() {
        return logChat.getValue();
    }

    public boolean shouldLogInventory() {
        return logInventory.getValue();
    }
}
