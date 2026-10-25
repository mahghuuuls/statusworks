package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.StatusworksNetwork;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;

import java.io.File;

/** Starts the client side: settings, the store that receives the server's entries, the HUD, and the detail view. */
public final class ClientSetup {

    private ClientSetup() {
    }

    /** Called once from the client proxy in pre-init, on the physical client only. */
    public static void init(File configDirectory, Logger logger) {
        ClientSettings settings = new ClientSettings(new File(configDirectory, "statusworks-client.cfg"));
        settings.load();
        ClientStore store = new ClientStore(settings, logger);
        StatusworksNetwork.setReceiver(store);
        MinecraftForge.EVENT_BUS.register(store);
        MinecraftForge.EVENT_BUS.register(new HudRenderer(store, settings));
        MinecraftForge.EVENT_BUS.register(new DetailView(store, settings));
    }
}
