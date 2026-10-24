package com.mahghuuuls.statusworks.api.callback;

/** Runs when a pool instance ends, with the removal reason. */
@FunctionalInterface
public interface PoolRemovedCallback {
    void onRemoved(PoolContext context);
}
