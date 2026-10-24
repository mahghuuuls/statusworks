package com.mahghuuuls.statusworks.api.callback;

/** Runs when an operation changes a pool's amount; never for a progression step. */
@FunctionalInterface
public interface PoolAmountCallback {
    void onAmountChanged(PoolContext context);
}
