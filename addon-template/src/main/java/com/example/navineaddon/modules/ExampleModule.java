package com.example.navineaddon.modules;

import nv.navineclient.module.Module;
import nv.navineclient.module.settings.BooleanSetting;
import nv.navineclient.util.ChatUtils;

public class ExampleModule extends Module {
    private final BooleanSetting greetOnEnable = new BooleanSetting("Greet", "Show message on enable", true);

    public ExampleModule() {
        super("Example", "Placeholder addon module", Category.MISC);
        addSetting(greetOnEnable);
    }

    @Override
    public void onEnable() {
        if (greetOnEnable.getValue()) {
            ChatUtils.message("Example addon module enabled");
        }
    }

    @Override
    public void onDisable() {
    }
}
