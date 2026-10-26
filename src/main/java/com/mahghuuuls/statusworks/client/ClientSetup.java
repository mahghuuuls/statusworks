package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.api.client.StatusworksClient;
import com.mahghuuuls.statusworks.network.StatusworksNetwork;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Starts the client side: settings, the store that receives the server's entries, the HUD, the detail view, the
 * particles, and the read-only view for addons.
 */
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
        HudRenderer hud = new HudRenderer(store, settings);
        MinecraftForge.EVENT_BUS.register(hud);
        MinecraftForge.EVENT_BUS.register(new DetailView(hud));
        MinecraftForge.EVENT_BUS.register(new ParticleEmitter(store, settings));
        StatusworksClient.install(new ClientStatusworksView(store));
    }
}
