package nv.navineclient;

import net.fabricmc.api.ClientModInitializer;

public final class FabricNavineEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new NavineClient().onInitializeClient();
    }
}
