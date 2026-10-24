package com.mahghuuuls.statusworks.api.callback;

/** Runs when a mark ends, with the reason in {@link MarkContext#reason()}. */
@FunctionalInterface
public interface MarkRemovedCallback {
    void onRemoved(MarkContext context);
}
