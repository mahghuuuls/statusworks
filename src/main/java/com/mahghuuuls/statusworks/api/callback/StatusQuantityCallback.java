package com.mahghuuuls.statusworks.api.callback;

/** Runs on every change of a status's total count, with the count before and after. */
@FunctionalInterface
public interface StatusQuantityCallback {
    void onQuantityChanged(StatusContext context);
}
