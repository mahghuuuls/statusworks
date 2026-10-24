package com.mahghuuuls.statusworks.api.callback;

/** Runs once each time a pool arrives at 0; never at creation or load. */
@FunctionalInterface
public interface PoolEmptyCallback {
    void onEmpty(PoolContext context);
}
