package com.mahghuuuls.statusworks.api.callback;

/** Runs each time a mark becomes inactive: a participant unloaded, logged out, changed dimension, died with the mark kept, or moved beyond the maximum distance. */
@FunctionalInterface
public interface MarkInactiveCallback {
    void onInactive(MarkContext context);
}
