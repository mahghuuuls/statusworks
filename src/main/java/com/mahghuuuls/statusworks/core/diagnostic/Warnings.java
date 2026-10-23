package com.mahghuuuls.statusworks.core.diagnostic;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Warnings that must appear once per key, such as an unknown definition id used every tick.
 */
public final class Warnings {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final Consumer<String> sink;

    public Warnings(Consumer<String> sink) {
        this.sink = sink;
    }

    /** Writes the message the first time the key is seen; later calls with the same key write nothing. */
    public void once(String key, String message) {
        if (seen.add(key)) {
            sink.accept(message);
        }
    }
}
