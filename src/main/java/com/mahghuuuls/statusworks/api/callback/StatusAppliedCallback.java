package com.mahghuuuls.statusworks.api.callback;

/** Runs once when a status instance is created on an entity; a refresh never repeats it. */
@FunctionalInterface
public interface StatusAppliedCallback {
    void onApplied(StatusContext context);
}
