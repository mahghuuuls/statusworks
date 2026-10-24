package com.mahghuuuls.statusworks.core.contribution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per entity, every Statusworks contribution currently applied, by contribution key. Saved with the entity, so a
 * load reapplies exactly what was applied before, and the next reconciliation compares against it.
 * {@link ContributionManager} owns every change.
 */
public final class ContributionRecord {

    private final LinkedHashMap<String, Applied> entries = new LinkedHashMap<>();

    /** The entries by key; read-only. */
    public Map<String, Applied> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    void put(String key, Applied applied) {
        entries.put(key, applied);
    }

    void remove(String key) {
        entries.remove(key);
    }

    /** Restores one saved entry; used by the save format only. */
    public void restore(String key, Applied applied) {
        entries.put(key, applied);
    }
}
