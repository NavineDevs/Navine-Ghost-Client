package com.example.navineaddon.commands;

import nv.navineclient.commands.Command;
import nv.navineclient.util.ChatUtils;

public class ExampleCommand extends Command {
    public ExampleCommand() {
        super("exampleaddon", "Example addon command", ".exampleaddon");
    }

    @Override
    public void onCommand(String[] args) {
        ChatUtils.message("Example Navine addon command works.");
    }
}
