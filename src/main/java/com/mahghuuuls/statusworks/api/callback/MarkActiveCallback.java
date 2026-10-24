package com.mahghuuuls.statusworks.api.callback;

/** Runs each time an inactive mark becomes active again: both participants are loaded in one dimension and within the maximum distance. */
@FunctionalInterface
public interface MarkActiveCallback {
    void onActive(MarkContext context);
}
