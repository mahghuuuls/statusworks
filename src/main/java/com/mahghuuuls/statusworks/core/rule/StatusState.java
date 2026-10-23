package com.mahghuuuls.statusworks.core.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The data of one status instance, in plain Java. Times are counted in the instance's own active ticks:
 * {@code age} grows by one for every tick the entity is updated, and deadlines are ages, so pausing the entity
 * pauses every timer without any work. {@link StatusRules} owns every change to this data.
 */
public final class StatusState {

    /** One group of stacks: the whole status for shared ownership, or one source's stacks for per-source. */
    public static final class Group {
        int count;
        /** Age at which the shared or gradual timer runs out, or -1 when there is none. */
        long deadline = -1;
        /** Duration of the latest application, used to restart a gradual timer. */
        int lastDuration;
        /** Independent stacks: age of expiry to the number of stacks that expire then. */
        final TreeMap<Long, Integer> stacks = new TreeMap<>();

        public int count() {
            return count;
        }

        public long deadline() {
            return deadline;
        }

        public int lastDuration() {
            return lastDuration;
        }

        /** Independent stacks by expiry age; read-only. */
        public Map<Long, Integer> stacks() {
            return Collections.unmodifiableMap(stacks);
        }

        /** Restores saved data; used by the save format only. */
        public void restore(int count, long deadline, int lastDuration) {
            this.count = count;
            this.deadline = deadline;
            this.lastDuration = lastDuration;
        }

        /** Restores one saved independent stack entry; used by the save format only. */
        public void restoreStack(long expiryAge, int count) {
            stacks.put(expiryAge, count);
        }
    }

    long age;
    final LinkedHashMap<UUID, Group> groups = new LinkedHashMap<>();

    public long age() {
        return age;
    }

    /** Groups by source key; read-only. {@link StatusRules} owns every change. */
    public Map<UUID, Group> groups() {
        return Collections.unmodifiableMap(groups);
    }

    /** Restores the saved age; used by the save format only. */
    public void restoreAge(long age) {
        this.age = age;
    }

    /** Adds a restored group; used by the save format only. */
    public Group restoreGroup(UUID key) {
        Group group = new Group();
        groups.put(key, group);
        return group;
    }

    public int total() {
        int total = 0;
        for (Group group : groups.values()) {
            total += group.count;
        }
        return total;
    }

    public boolean isEmpty() {
        return total() == 0;
    }
}
