package com.mahghuuuls.statusworks.api.callback;

/** Runs once each time a pool arrives at its capacity; never at creation or load. */
@FunctionalInterface
public interface PoolFullCallback {
    void onFull(PoolContext context);
}
