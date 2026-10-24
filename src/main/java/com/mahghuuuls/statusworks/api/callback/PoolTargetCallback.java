package com.mahghuuuls.statusworks.api.callback;

/** Runs when a {@code to_target} rule reaches its target at its deadline. */
@FunctionalInterface
public interface PoolTargetCallback {
    void onTargetReached(PoolContext context);
}
