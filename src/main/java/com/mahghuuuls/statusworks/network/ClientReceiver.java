package com.mahghuuuls.statusworks.network;

import java.util.List;

/**
 * Where the client side hands received messages. The physical client installs one at start; a dedicated server
 * never does, so this package never loads client classes. Called on the network thread: the receiver moves the
 * work to the client's main thread.
 */
public interface ClientReceiver {

    void onTable(List<HudDefinition> table);

    void onEntries(boolean snapshot, List<HudEntry> changed, List<String> removed);

    /** Entries of an entity the player may see because they have particles. */
    void onAudience(int entity, boolean snapshot, List<HudEntry> changed, List<String> removed);

    /** The player sees nothing on the entity any more. */
    void onAudienceRemove(int entity);
}
