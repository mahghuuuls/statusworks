package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.RefreshRule;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every rule of a status in one place: limits and partial fits, the four expiration modes, the three refresh
 * rules with the maximum remaining time, refresh on rejected stacks, per-source groups, removal order, setting
 * a count, the remaining-time queries, death rules, and fitting saved state to a changed definition. Plain Java with no Minecraft types, so every rule is unit-tested.
 *
 * <p>The definition passed in is assumed valid (the registry disables invalid ones).</p>
 */
public final class StatusRules {

    /** Group key of stacks applied without a source, and of every stack of a shared status. */
    public static final UUID NO_SOURCE = new UUID(0L, 0L);

    private StatusRules() {
    }

    /** The result of one application. */
    public static final class Applied {
        public final int added;
        public final int rejected;
        public final int totalBefore;
        public final int totalAfter;

        Applied(int added, int rejected, int totalBefore, int totalAfter) {
            this.added = added;
            this.rejected = rejected;
            this.totalBefore = totalBefore;
            this.totalAfter = totalAfter;
        }
    }

    /** The group key an application from this source falls into. */
    public static UUID groupKey(StatusDefinition definition, @Nullable UUID source) {
        if (definition.ownership() == Ownership.PER_SOURCE && source != null) {
            return source;
        }
        return NO_SOURCE;
    }

    /**
     * Applies stacks to a group.
     *
     * @param duration ticks for this application; a value below 1 means the definition's default
     */
    public static Applied apply(StatusDefinition definition, StatusState state, UUID key, int stacks, int duration) {
        int totalBefore = state.total();
        int ticks = duration >= 1 ? duration : definition.defaultDuration();
        StatusState.Group group = state.groups.get(key);
        boolean newGroup = group == null || group.count == 0;
        if (group == null) {
            group = new StatusState.Group();
            state.groups.put(key, group);
        }
        int room = Math.max(0, definition.limit() - group.count);
        int added = Math.min(stacks, room);
        int rejected = stacks - added;
        ExpirationMode mode = definition.expiration();

        if (mode == ExpirationMode.INDEPENDENT) {
            if (added > 0) {
                group.stacks.merge(state.age + ticks, added, Integer::sum);
                group.count += added;
            }
        } else if (mode == ExpirationMode.NONE) {
            group.count += added;
            group.deadline = -1;
        } else if (newGroup) {
            if (added > 0) {
                group.count += added;
                group.deadline = state.age + ticks;
                group.lastDuration = ticks;
            }
        } else {
            if (added > 0 || definition.refreshOnReject()) {
                refresh(definition, state, group, ticks);
            }
            group.count += added;
        }
        if (group.count == 0) {
            state.groups.remove(key);
        }
        return new Applied(added, rejected, totalBefore, state.total());
    }

    private static void refresh(StatusDefinition definition, StatusState state, StatusState.Group group, int ticks) {
        RefreshRule rule = definition.refresh();
        if (rule == RefreshRule.RESET) {
            group.deadline = state.age + ticks;
        } else if (rule == RefreshRule.EXTEND) {
            long remaining = Math.max(0, group.deadline - state.age);
            long extended = remaining + ticks;
            int maximum = definition.maximumRemaining();
            if (maximum >= 1 && extended > maximum) {
                extended = Math.max(remaining, maximum);
            }
            group.deadline = state.age + extended;
        }
        // PRESERVE leaves the timer alone.
        group.lastDuration = ticks;
    }

    /**
     * Whether the coming tick completes an interval, so the interval callback runs before that tick's timers:
     * the first run is one interval after creation, then every interval, counting active ticks only.
     */
    public static boolean intervalDue(StatusDefinition definition, StatusState state) {
        int interval = definition.interval();
        return interval >= 1 && (state.age() + 1) % interval == 0;
    }

    /**
     * Advances the instance by one active tick and applies expirations.
     *
     * @return the number of stacks that expired in this tick
     */
    public static int tick(StatusDefinition definition, StatusState state) {
        state.age++;
        int expired = 0;
        ExpirationMode mode = definition.expiration();
        Iterator<Map.Entry<UUID, StatusState.Group>> it = state.groups.entrySet().iterator();
        while (it.hasNext()) {
            StatusState.Group group = it.next().getValue();
            if (mode == ExpirationMode.SHARED) {
                if (group.deadline >= 0 && state.age >= group.deadline) {
                    expired += group.count;
                    group.count = 0;
                }
            } else if (mode == ExpirationMode.GRADUAL) {
                if (group.deadline >= 0 && state.age >= group.deadline && group.count > 0) {
                    group.count--;
                    expired++;
                    group.deadline = group.count > 0 ? state.age + group.lastDuration : -1;
                }
            } else if (mode == ExpirationMode.INDEPENDENT) {
                while (!group.stacks.isEmpty() && group.stacks.firstKey() <= state.age) {
                    int n = group.stacks.pollFirstEntry().getValue();
                    group.count -= n;
                    expired += n;
                }
            }
            if (group.count <= 0) {
                it.remove();
            }
        }
        return expired;
    }

    /**
     * Removes up to {@code stacks} stacks. With a key, only that group loses stacks; without one, groups lose
     * stacks in order of their soonest expiry, and independent stacks closest to expiring go first.
     *
     * @return the number removed
     */
    public static int remove(StatusDefinition definition, StatusState state, @Nullable UUID key, int stacks) {
        int left = stacks;
        while (left > 0) {
            UUID groupKey = key != null ? key : soonestGroup(definition, state);
            StatusState.Group group = groupKey == null ? null : state.groups.get(groupKey);
            if (group == null || group.count <= 0) {
                break;
            }
            left -= takeSoonest(definition, group, left);
            if (group.count <= 0) {
                state.groups.remove(groupKey);
            }
        }
        return stacks - left;
    }

    /** The group holding the stacks closest to expiring, or null when there are none; ties keep group order. */
    @Nullable
    private static UUID soonestGroup(StatusDefinition definition, StatusState state) {
        UUID best = null;
        long bestAge = 0;
        for (Map.Entry<UUID, StatusState.Group> entry : state.groups.entrySet()) {
            long age = soonest(definition, entry.getValue());
            if (best == null || age < bestAge) {
                best = entry.getKey();
                bestAge = age;
            }
        }
        return best;
    }

    /** The age of a group's next expiry, or Long.MAX_VALUE when it has none. */
    private static long soonest(StatusDefinition definition, StatusState.Group group) {
        if (definition.expiration() == ExpirationMode.INDEPENDENT) {
            return group.stacks.isEmpty() ? Long.MAX_VALUE : group.stacks.firstKey();
        }
        return group.deadline >= 0 ? group.deadline : Long.MAX_VALUE;
    }

    /**
     * Takes stacks from a group, soonest first: for independent stacks only from the earliest expiry entry, so
     * the caller can look across groups again; for one shared timer, as many as wanted.
     */
    private static int takeSoonest(StatusDefinition definition, StatusState.Group group, int wanted) {
        int take;
        Map.Entry<Long, Integer> first = group.stacks.firstEntry();
        if (definition.expiration() == ExpirationMode.INDEPENDENT && first != null) {
            take = Math.min(wanted, first.getValue());
            if (take == first.getValue()) {
                group.stacks.pollFirstEntry();
            } else {
                group.stacks.put(first.getKey(), first.getValue() - take);
            }
        } else {
            take = Math.min(wanted, group.count);
        }
        group.count -= take;
        if (group.count <= 0) {
            group.deadline = -1;
        }
        return take;
    }

    /** Ends every group of the instance. */
    public static void clear(StatusState state) {
        state.groups.clear();
    }

    /** Remaining ticks of each independent stack entry of a group, soonest first, as remaining ticks to count. */
    public static Map<Integer, Integer> stackRemainders(StatusState state, UUID key) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        StatusState.Group group = state.groups.get(key);
        if (group != null) {
            for (Map.Entry<Long, Integer> entry : group.stacks.entrySet()) {
                result.put(clampTicks(entry.getKey() - state.age), entry.getValue());
            }
        }
        return result;
    }

    private static int clampTicks(long ticks) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, ticks));
    }

    /**
     * Sets one group's count, clamped to 0 and the limit. New stacks get the default duration; running timers
     * do not change.
     */
    public static void setCount(StatusDefinition definition, StatusState state, UUID key, int count) {
        int target = Math.max(0, Math.min(count, definition.limit()));
        StatusState.Group group = state.groups.get(key);
        int current = group == null ? 0 : group.count;
        if (target > current) {
            ExpirationMode mode = definition.expiration();
            if (group == null) {
                group = new StatusState.Group();
                state.groups.put(key, group);
            }
            int extra = target - current;
            if (mode == ExpirationMode.INDEPENDENT) {
                group.stacks.merge(state.age + definition.defaultDuration(), extra, Integer::sum);
            } else if (mode != ExpirationMode.NONE && current == 0) {
                group.deadline = state.age + definition.defaultDuration();
                group.lastDuration = definition.defaultDuration();
            }
            group.count = target;
        } else if (target < current) {
            remove(definition, state, key, current - target);
        }
    }

    /** The remaining ticks of one group, or of the soonest group when key is null; -1 when there is no timer. */
    public static int remaining(StatusDefinition definition, StatusState state, @Nullable UUID key) {
        long best = Long.MAX_VALUE;
        for (Map.Entry<UUID, StatusState.Group> entry : state.groups.entrySet()) {
            if (key != null && !key.equals(entry.getKey())) {
                continue;
            }
            best = Math.min(best, soonest(definition, entry.getValue()));
        }
        if (best == Long.MAX_VALUE) {
            return -1;
        }
        return clampTicks(best - state.age);
    }

    /** Whether an instance outlives its entity's death: only on a player, and only with the {@code keep} rule. */
    public static boolean survivesDeath(StatusDefinition definition, boolean player) {
        return DeathRules.survivesDeath(definition.deathRule(), player);
    }

    /**
     * Whether an instance is copied to the new player object of a clone: always when leaving the End, and after
     * a death only when it survives death.
     */
    public static boolean copiedOnClone(StatusDefinition definition, boolean wasDeath) {
        return DeathRules.copiedOnClone(definition.deathRule(), wasDeath);
    }

    /**
     * Fits a restored instance to its definition as registered now, so every saved instance keeps a working
     * timer under the current rules. Applied in this order:
     * <ol>
     *   <li>Ownership: when the status is no longer per-source, every group merges into the no-source group.</li>
     *   <li>Expiration mode: a group with independent stacks moving to a shared or gradual timer takes the
     *   soonest saved expiry; a group with one timer moving to independent stacks gives all its stacks one entry
     *   at that timer; a group without a timer gets the default duration; {@code none} drops every timer.</li>
     *   <li>Limit: a group above the limit loses its stacks closest to expiring first.</li>
     * </ol>
     *
     * <p>The maximum remaining time is not applied here: it caps {@code extend} refreshes only, so a longer saved
     * timer is valid under an unchanged definition, and a load cannot tell it from a lowered maximum.</p>
     *
     * @return whether anything changed
     */
    public static boolean fit(StatusDefinition definition, StatusState state) {
        boolean changed = false;
        if (definition.ownership() != Ownership.PER_SOURCE) {
            changed = mergeIntoNoSource(state);
        }
        ExpirationMode mode = definition.expiration();
        for (UUID key : new ArrayList<>(state.groups.keySet())) {
            StatusState.Group group = state.groups.get(key);
            if (group.count <= 0) {
                state.groups.remove(key);
                changed = true;
                continue;
            }
            if (mode == ExpirationMode.INDEPENDENT) {
                if (group.stacks.isEmpty()) {
                    long at = group.deadline >= 0 ? group.deadline : state.age + definition.defaultDuration();
                    group.stacks.put(at, group.count);
                    group.deadline = -1;
                    changed = true;
                }
            } else {
                if (!group.stacks.isEmpty()) {
                    group.deadline = group.stacks.firstKey();
                    group.stacks.clear();
                    changed = true;
                }
                if (mode == ExpirationMode.NONE) {
                    if (group.deadline >= 0) {
                        group.deadline = -1;
                        changed = true;
                    }
                } else {
                    if (group.deadline < 0) {
                        group.deadline = state.age + definition.defaultDuration();
                        changed = true;
                    }
                    if (group.lastDuration < 1) {
                        group.lastDuration = definition.defaultDuration();
                    }
                }
            }
            if (group.count > definition.limit()) {
                setCount(definition, state, key, definition.limit());
                changed = true;
            }
        }
        return changed;
    }

    /** Merges every group into the no-source group: counts add up, the soonest timer and the stacks are kept. */
    private static boolean mergeIntoNoSource(StatusState state) {
        boolean other = false;
        for (UUID key : state.groups.keySet()) {
            if (!NO_SOURCE.equals(key)) {
                other = true;
                break;
            }
        }
        if (!other) {
            return false;
        }
        StatusState.Group merged = new StatusState.Group();
        for (StatusState.Group group : state.groups.values()) {
            merged.count += group.count;
            if (group.deadline >= 0 && (merged.deadline < 0 || group.deadline < merged.deadline)) {
                merged.deadline = group.deadline;
            }
            merged.lastDuration = Math.max(merged.lastDuration, group.lastDuration);
            for (Map.Entry<Long, Integer> entry : group.stacks.entrySet()) {
                merged.stacks.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        state.groups.clear();
        state.groups.put(NO_SOURCE, merged);
        return true;
    }

    /** The count of one group. */
    public static int count(StatusState state, UUID key) {
        StatusState.Group group = state.groups.get(key);
        return group == null ? 0 : group.count;
    }
}
