package com.mahghuuuls.statusworks.api.callback;

/** Runs once when a mark is created (result {@code created} or {@code replaced}); never on a refresh or when an inactive mark becomes active again. */
@FunctionalInterface
public interface MarkAppliedCallback {
    void onApplied(MarkContext context);
}
