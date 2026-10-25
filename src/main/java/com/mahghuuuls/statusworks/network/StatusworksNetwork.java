package com.mahghuuuls.statusworks.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;

/** The {@code statusworks} channel. Server to client only. */
public final class StatusworksNetwork {

    public static final String CHANNEL_NAME = "statusworks";

    @Nullable static SimpleNetworkWrapper channel;
    /** Installed by the physical client; null on a dedicated server. */
    @Nullable static volatile ClientReceiver receiver;

    private StatusworksNetwork() {
    }

    /** Opens the channel and registers the messages; called once in pre-init on both sides. */
    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(TableMessage.Handler.class, TableMessage.class, 0, Side.CLIENT);
        channel.registerMessage(EntriesMessage.Handler.class, EntriesMessage.class, 1, Side.CLIENT);
    }

    /** Called by the physical client at start. */
    public static void setReceiver(ClientReceiver clientReceiver) {
        receiver = clientReceiver;
    }
}
