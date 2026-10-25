package com.mahghuuuls.statusworks;

import com.mahghuuuls.statusworks.client.ClientSetup;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Physical client start-up: the HUD, detail view, and client settings. Loaded only on the physical client. */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ClientSetup.init(event.getModConfigurationDirectory(), StatusworksMod.LOGGER);
    }
}
