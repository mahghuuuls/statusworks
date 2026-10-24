package com.mahghuuuls.statusworks.api.callback;

/** Runs every interval of active ticks while the mark is active; an inactive mark's interval pauses. */
@FunctionalInterface
public interface MarkIntervalCallback {
    void onInterval(MarkContext context);
}
