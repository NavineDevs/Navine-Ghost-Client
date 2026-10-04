package com.navine.clientaddon;

import com.navine.clientaddon.modules.ClientAddonExampleModule;
import nv.navineclient.NavineAddon;

import java.util.List;

public class NavineClientAddon extends NavineAddon {
    public static final String CATEGORY_ID = "navineclientaddon";

    @Override
    public void onRegisterCategories() {
        registerCategory(CATEGORY_ID, "Navine Addon");
    }

    @Override
    public void onInitialize() {
        registerModule(new ClientAddonExampleModule());
        LOG.info("NavineClientAddon initialized");
    }

    @Override
    public String getPackage() {
        return "com.navine.clientaddon";
    }

    @Override
    public String getName() {
        return "Navine Client Addon";
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
