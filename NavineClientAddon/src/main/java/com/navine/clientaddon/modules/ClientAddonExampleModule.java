package com.navine.clientaddon.modules;

import com.navine.clientaddon.NavineClientAddon;
import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.util.ChatUtils;

public class ClientAddonExampleModule extends Module {
    private final BooleanSetting greetOnEnable = new BooleanSetting("Greet", "Show message when enabled", true);

    public ClientAddonExampleModule() {
        super("Addon HUD", "Example module from NavineClientAddon", NavineClientAddon.CATEGORY_ID);
        addSetting(greetOnEnable);
    }

    @Override
    public void onEnable() {
        if (greetOnEnable.getValue()) {
            ChatUtils.message("NavineClientAddon module enabled");
        }
    }

    @Override
    public void onDisable() {
    }
}
