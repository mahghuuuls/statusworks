package com.mahghuuuls.statusworks.api.client;

import java.util.List;
import java.util.Map;

/**
 * The client side of the API, for addons that draw their own visuals: what this client received from the server, and
 * nothing else. It never contains an author-hidden definition, and it lists another entity's entries only while the
 * server sends them to this player for their particles. Read it on the client's main thread.
 */
public interface StatusworksClientView {

    /** The player's own entries, also the ones the player hid on its HUD. */
    List<ClientEntry> ownEntries();

    /**
     * Entries with particles that this player is sent, by network entity id: of entities near it, of a mark's target
     * when the player is in the mark's audience, and of the player itself when it is in its own statuses' audience.
     */
    Map<Integer, List<ClientEntry>> nearbyEntries();
}
