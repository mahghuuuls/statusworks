package com.mahghuuuls.statusworks.api.callback;

/** Runs when a status instance ends, with the removal reason. */
@FunctionalInterface
public interface StatusRemovedCallback {
    void onRemoved(StatusContext context);
}
