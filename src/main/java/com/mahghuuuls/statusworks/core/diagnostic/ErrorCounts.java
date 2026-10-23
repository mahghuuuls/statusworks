package com.mahghuuuls.statusworks.core.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Failure counts per callback (definition id and callback point), for the {@code errors} report. Holds data only;
 * the callback runner decides what a count means.
 */
public final class ErrorCounts {

    /** The counts of one callback. */
    public static final class Entry {
        public final String key;
        int total;
        int consecutive;
        boolean disabled;

        Entry(String key) {
            this.key = key;
        }

        public int total() {
            return total;
        }

        public int consecutive() {
            return consecutive;
        }

        public boolean disabled() {
            return disabled;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Records a failure and returns the entry with the new counts. */
    public synchronized Entry failure(String key) {
        Entry e = entries.computeIfAbsent(key, Entry::new);
        e.total++;
        e.consecutive++;
        return e;
    }

    /** Records a success, which ends a run of failures. */
    public synchronized void success(String key) {
        Entry e = entries.get(key);
        if (e != null) {
            e.consecutive = 0;
        }
    }

    public synchronized void disable(String key) {
        entries.computeIfAbsent(key, Entry::new).disabled = true;
    }

    public synchronized boolean isDisabled(String key) {
        Entry e = entries.get(key);
        return e != null && e.disabled;
    }

    /** A copy of every entry, in the order failures first appeared. */
    public synchronized List<Entry> snapshot() {
        List<Entry> copy = new ArrayList<>();
        for (Entry e : entries.values()) {
            Entry c = new Entry(e.key);
            c.total = e.total;
            c.consecutive = e.consecutive;
            c.disabled = e.disabled;
            copy.add(c);
        }
        return copy;
    }
}
