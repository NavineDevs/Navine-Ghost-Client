package com.example.navineaddon;

import com.example.navineaddon.commands.ExampleCommand;
import com.example.navineaddon.modules.ExampleModule;
import nv.navineclient.NavineAddon;

import java.util.List;

public class NavineExampleAddon extends NavineAddon {
    @Override
    public void onInitialize() {
        registerModule(new ExampleModule());
        registerCommand(new ExampleCommand());
        LOG.info("Navine Example addon initialized");
    }

    @Override
    public String getPackage() {
        return "com.example.navineaddon";
    }

    @Override
    public String getName() {
        return "Navine Example";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getAuthors() {
        return List.of("Navine Team");
    }
}
