package com.mahghuuuls.statusworks.api.client;

import javax.annotation.Nullable;

/** Where an addon finds the {@link StatusworksClientView}. */
public final class StatusworksClient {

    private static volatile StatusworksClientView view;

    private StatusworksClient() {
    }

    /** The client view, or null on a dedicated server and before the client has started. */
    @Nullable
    public static StatusworksClientView view() {
        return view;
    }

    /**
     * Installed once by Statusworks on the physical client; addons do not call it.
     *
     * @throws IllegalStateException when a view is already installed
     */
    public static synchronized void install(StatusworksClientView clientView) {
        if (view != null) {
            throw new IllegalStateException("the Statusworks client view is installed by Statusworks only");
        }
        view = clientView;
    }
}
