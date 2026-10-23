package com.mahghuuuls.statusworks.api.callback;

/** Runs every configured number of active ticks while the status exists, first one interval after it was created. */
@FunctionalInterface
public interface StatusIntervalCallback {
    void onInterval(StatusContext context);
}
