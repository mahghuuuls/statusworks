package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The contribution group state of one entity object. Held with the entity's capability and never
 * saved, so a new entity object (a load, a respawn, a mob's dimension change) starts with every group unavailable.
 * {@link GroupBoundary} owns every change.
 */
public final class GroupStates {

    static final class Entry {
        GroupAvailability availability = GroupAvailability.UNAVAILABLE;
        /** Retries made in the current series after a failure. */
        int attempts;
        /** Whether the warning for a used-up series was written. */
        boolean warned;
    }

    /** Whether the object is in a world and got its join signals. */
    boolean joined;
    /** Replaced by a new object at a respawn or End exit; never available again. */
    boolean retired;
    /** Counts leaves and replacements, so a join or retry sees that the object left during its own update. */
    int epoch;
    /**
     * The types that were available when the object last left its world. A replacement that follows (Forge removes
     * the old player before the clone event) still owes them their invalidation.
     */
    final Set<String> availableWhenLeft = new LinkedHashSet<>();
    final Map<String, Entry> byType = new LinkedHashMap<>();

    Entry entry(String typeId) {
        return byType.computeIfAbsent(typeId, k -> new Entry());
    }

    GroupAvailability availability(String typeId) {
        Entry e = byType.get(typeId);
        return e == null ? GroupAvailability.UNAVAILABLE : e.availability;
    }
}
