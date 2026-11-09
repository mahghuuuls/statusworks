package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The one owner of contribution groups and their signals.
 *
 * <p>For each entity object and each contribution type that asked for group signals and applies to it, it keeps the
 * group's availability and sends the signals: an invalidation before an update changes the type's entries, a settled
 * signal with the complete group after the update committed, a pair at every join, an invalidation for the available
 * groups of an object replaced at a respawn. A refused or failing invalidation, or an update that Statusworks could
 * not finish, leaves the group failed: no settled signal, no availability, until a retry or a later change confirms
 * an invalidation and delivers the settled group. Retries are counted per failure series, at most
 * {@value #RETRY_LIMIT}, then one warning; a failing update inside a retry counts as a try of the current series.
 * Signal errors are logged once per type and signal, then counted, and never switch the signal off. A type that
 * stops applying to the entity gets a last invalidation and is unavailable; a type that never applied gets
 * nothing.</p>
 *
 * <p>The caller hands it the update itself ({@link #update}, with {@link #commit} inside; {@link #join};
 * {@link #retry}), so the order of invalidation, commit and settled signal, and what a failure means, are decided
 * here. The caller runs these inside the entity's serialized contribution pass, so the state never changes under a
 * signal.</p>
 *
 * <p>It keeps one index outside the entity objects: the entities with a failed group still to retry. It has to,
 * because such an entity may have no other state that keeps it in the tick driver's set; it is released when the
 * entity leaves its world, is replaced, or its series ends.</p>
 *
 * <p>It reaches the game only through {@link Side}, so its rules are tested without Minecraft. Server thread
 * only.</p>
 */
public final class GroupBoundary<E> {

    public static final int RETRY_LIMIT = 60;

    /** What the boundary needs from the game. */
    public interface Side<E> {
        /** The ids of the registered contribution types with group signals, in registration order. */
        List<String> groupTypes();

        /** Whether the type applies to the entity; may throw. */
        boolean applies(E entity, String typeId);

        /** Calls the type's invalidation; may throw. */
        boolean invalidate(E entity, String typeId);

        /** Calls the type's settled signal with the complete group; may throw. */
        void settled(E entity, String typeId, Map<String, Double> group);

        /** The committed values of the type on the entity, by contribution key. */
        Map<String, Double> committed(E entity, String typeId);

        /** Whether contribution work for the entity is waiting (the pass serializer's answer). */
        boolean waiting(E entity);

        /** The entity's group state, or null when it has none (client side, no capability). */
        @Nullable
        GroupStates states(E entity);

        String label(E entity);
    }

    /** Sentinel answer of a call that threw. */
    private static final Object ERROR = new Object();

    private final Side<E> side;
    private final ErrorCounts counts;
    private final BiConsumer<String, Throwable> errorLog;
    private final Consumer<String> warning;
    private final BooleanSupplier diagnosticOn;
    private final Consumer<String> diagnostic;
    /** Entities with a failed group whose series is not used up. */
    private final Set<E> withFailures = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Entities whose retry runs now, with the types already tried in it. */
    private final Map<E, Set<String>> retrying = new IdentityHashMap<>();

    /**
     * @param errorLog writes the first error of a type's signal, or of a failed update, with its stack trace
     * @param warning writes the warning after the last failed retry of a series
     * @param diagnosticOn whether the diagnostic log is on; lines are built only then
     * @param diagnostic writes one diagnostic log line
     */
    public GroupBoundary(Side<E> side, ErrorCounts counts, BiConsumer<String, Throwable> errorLog,
                         Consumer<String> warning, BooleanSupplier diagnosticOn, Consumer<String> diagnostic) {
        this.side = side;
        this.counts = counts;
        this.errorLog = errorLog;
        this.warning = warning;
        this.diagnosticOn = diagnosticOn;
        this.diagnostic = diagnostic;
    }

    /**
     * Runs one update of the entity's contributions. {@code body} computes the change and calls {@link #commit} with
     * the types it touches. When Statusworks fails inside, every applying group of the entity fails and the error
     * goes on to the caller, except a runtime failure during a retry, which is logged and counts as a failed try.
     */
    public void update(E entity, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            failAll(entity);
            if (retrying.containsKey(entity) && t instanceof RuntimeException) {
                ErrorCounts.Entry entry = counts.failure("group update");
                if (entry.total() == 1) {
                    errorLog.accept("Statusworks: updating contributions failed during a group retry; later failures"
                            + " are counted, see /statusworks errors", t);
                }
                return;
            }
            throw t;
        }
    }

    /**
     * Inside {@link #update}: sends the invalidations of the touched group types, runs {@code commitAndNotices}
     * (the commit and the per-entry notices), then the settled groups of the confirmed types.
     */
    public void commit(E entity, Collection<String> touchedTypes, Runnable commitAndNotices) {
        GroupStates states = live(entity);
        int epoch = states == null ? 0 : states.epoch;
        Set<String> confirmed = new LinkedHashSet<>();
        if (states != null && !touchedTypes.isEmpty()) {
            for (String type : side.groupTypes()) {
                if (touchedTypes.contains(type) && invalidate(entity, states, type)) {
                    confirmed.add(type);
                }
            }
        }
        commitAndNotices.run();
        if (states == null || live(entity) == null || states.epoch != epoch) {
            return;
        }
        for (String type : confirmed) {
            if (states.availability(type) == GroupAvailability.UPDATING) {
                settle(entity, states, type);
            }
        }
    }

    /**
     * The entity object joined a world: runs {@code joinUpdate} (the join's own update), then a pair for every
     * group type that applies, even with an empty group. Nothing is sent when the object left or was replaced during
     * that update.
     */
    public void join(E entity, Runnable joinUpdate) {
        GroupStates states = side.states(entity);
        int epoch = states == null ? 0 : states.epoch;
        try {
            joinUpdate.run();
        } catch (Throwable t) {
            if (states != null && !states.retired && states.epoch == epoch) {
                states.joined = true;
                failAll(entity);
            }
            throw t;
        }
        if (states == null || states.retired || states.epoch != epoch) {
            return;
        }
        states.joined = true;
        states.availableWhenLeft.clear();
        for (String type : side.groupTypes()) {
            if (invalidate(entity, states, type)) {
                settle(entity, states, type);
            }
        }
    }

    /** The entity object left its world (unload, removal, the start of a dimension change): nothing is current. */
    public void leave(E entity) {
        withFailures.remove(entity);
        GroupStates states = side.states(entity);
        if (states == null) {
            return;
        }
        states.epoch++;
        if (!states.joined) {
            return;
        }
        states.joined = false;
        states.availableWhenLeft.clear();
        for (Map.Entry<String, GroupStates.Entry> e : states.byType.entrySet()) {
            if (e.getValue().availability == GroupAvailability.AVAILABLE) {
                states.availableWhenLeft.add(e.getKey());
            }
            change(entity, e.getKey(), e.getValue(), GroupAvailability.UNAVAILABLE);
        }
    }

    /**
     * The entity object was replaced by a new one (respawn, End exit): its available groups get an invalidation,
     * then every group is unavailable for good. Groups that were available when the object left its world just
     * before (Forge removes the old player first) count as available here.
     */
    public void retire(E entity) {
        withFailures.remove(entity);
        GroupStates states = side.states(entity);
        if (states == null || states.retired) {
            return;
        }
        states.epoch++;
        for (Map.Entry<String, GroupStates.Entry> e : states.byType.entrySet()) {
            if (e.getValue().availability == GroupAvailability.AVAILABLE
                    || states.availableWhenLeft.contains(e.getKey())) {
                Object answer = call(e.getKey(), "invalidate", () -> side.invalidate(entity, e.getKey()));
                note(entity, e.getKey(), () -> "invalidate (replaced object) " + describe(answer));
            }
            change(entity, e.getKey(), e.getValue(), GroupAvailability.UNAVAILABLE);
        }
        states.availableWhenLeft.clear();
        states.retired = true;
        states.joined = false;
    }

    /** The entities that have a failed group whose retries are not used up. */
    public List<E> withFailures() {
        return new ArrayList<>(withFailures);
    }

    /**
     * One retry: runs {@code passUpdate} (an update that brings the contributions in line), then, for each failed
     * group whose series is not used up and that the update did not already try, an invalidation and, when
     * confirmed, the settled group. Each try counts once; after the last failed try, one warning. A runtime failure
     * inside the update is contained and counts as a failed try. Nothing more is sent when the object left or was
     * replaced during the update.
     */
    public void retry(E entity, Runnable passUpdate) {
        GroupStates states = live(entity);
        if (states == null) {
            withFailures.remove(entity);
            return;
        }
        int epoch = states.epoch;
        Set<String> tried = new LinkedHashSet<>();
        retrying.put(entity, tried);
        try {
            update(entity, passUpdate);
            if (live(entity) == null || states.epoch != epoch) {
                return;
            }
            for (String type : side.groupTypes()) {
                GroupStates.Entry e = states.byType.get(type);
                if (e == null || e.availability != GroupAvailability.FAILED || e.attempts >= RETRY_LIMIT
                        || tried.contains(type)) {
                    continue;
                }
                if (invalidate(entity, states, type)) {
                    settle(entity, states, type);
                }
            }
        } finally {
            retrying.remove(entity);
        }
        boolean more = false;
        for (Map.Entry<String, GroupStates.Entry> e : states.byType.entrySet()) {
            GroupStates.Entry entry = e.getValue();
            if (entry.availability != GroupAvailability.FAILED) {
                continue;
            }
            if (entry.attempts >= RETRY_LIMIT) {
                if (!entry.warned) {
                    entry.warned = true;
                    warning.accept("Statusworks: the contribution group of " + e.getKey() + " on "
                            + side.label(entity) + " is still failed after " + RETRY_LIMIT + " retries; it stays"
                            + " unavailable until the entity's next change, join or clone");
                }
            } else {
                more = true;
            }
        }
        if (!more) {
            withFailures.remove(entity);
        }
    }

    /**
     * The group read: available only for a joined, current object whose type applies and is settled, with no
     * contribution work waiting. A type that no longer applies reads unavailable; when the question fails, a
     * non-unavailable group reads failed. Then a failed group reads failed, waiting work reads pending, and the rest
     * reads its stored state.
     */
    public ContributionGroup read(E entity, String typeId) {
        GroupStates states = live(entity);
        if (states == null || !side.groupTypes().contains(typeId)) {
            return ContributionGroup.of(GroupAvailability.UNAVAILABLE);
        }
        GroupAvailability a = states.availability(typeId);
        if (a == GroupAvailability.UNAVAILABLE) {
            return ContributionGroup.of(a);
        }
        Object applies = call(typeId, "applies_to", () -> side.applies(entity, typeId));
        if (applies == Boolean.FALSE) {
            return ContributionGroup.of(GroupAvailability.UNAVAILABLE);
        }
        if (applies == ERROR || a == GroupAvailability.FAILED) {
            return ContributionGroup.of(GroupAvailability.FAILED);
        }
        if (side.waiting(entity)) {
            return ContributionGroup.of(GroupAvailability.PENDING);
        }
        return a == GroupAvailability.AVAILABLE ? ContributionGroup.available(side.committed(entity, typeId))
                : ContributionGroup.of(a);
    }

    /** Every applying group of the entity fails; a type that does not apply is unavailable, without a signal. */
    private void failAll(E entity) {
        GroupStates states = live(entity);
        if (states == null) {
            return;
        }
        Set<String> tried = retrying.get(entity);
        boolean any = false;
        for (String type : side.groupTypes()) {
            GroupStates.Entry e = states.entry(type);
            if (call(type, "applies_to", () -> side.applies(entity, type)) == Boolean.FALSE) {
                change(entity, type, e, GroupAvailability.UNAVAILABLE);
                continue;
            }
            if (tried != null && tried.contains(type)) {
                // Already tried in this retry (its invalidation counted): it fails without a new series or a
                // second try.
                change(entity, type, e, GroupAvailability.FAILED);
                any = true;
                continue;
            }
            if (tried == null || e.availability != GroupAvailability.FAILED) {
                startSeries(e);
            }
            if (tried != null) {
                e.attempts++;
                tried.add(type);
            }
            change(entity, type, e, GroupAvailability.FAILED);
            any = true;
        }
        if (any) {
            withFailures.add(entity);
        }
    }

    @Nullable
    private GroupStates live(E entity) {
        GroupStates states = side.states(entity);
        return states == null || !states.joined || states.retired ? null : states;
    }

    /**
     * Sends one invalidation. Confirmed: the group is updating. A type that no longer applies gets a last
     * invalidation when it was not unavailable, and becomes unavailable. Otherwise the group fails; outside a retry a
     * new series starts, inside one the try counts.
     */
    private boolean invalidate(E entity, GroupStates states, String type) {
        Object applies = call(type, "applies_to", () -> side.applies(entity, type));
        GroupStates.Entry e = states.entry(type);
        if (applies == Boolean.FALSE) {
            if (e.availability != GroupAvailability.UNAVAILABLE) {
                Object answer = call(type, "invalidate", () -> side.invalidate(entity, type));
                note(entity, type, () -> "invalidate (no longer applies) " + describe(answer));
                change(entity, type, e, GroupAvailability.UNAVAILABLE);
            }
            return false;
        }
        Object confirmed = applies == ERROR ? ERROR : call(type, "invalidate", () -> side.invalidate(entity, type));
        note(entity, type, () -> "invalidate " + (applies == ERROR ? "skipped, applies_to failed"
                : describe(confirmed)));
        Set<String> tried = retrying.get(entity);
        if (tried != null) {
            e.attempts++;
            tried.add(type);
        }
        if (confirmed == Boolean.TRUE) {
            change(entity, type, e, GroupAvailability.UPDATING);
            return true;
        }
        if (tried == null) {
            startSeries(e);
        }
        change(entity, type, e, GroupAvailability.FAILED);
        withFailures.add(entity);
        return false;
    }

    private void settle(E entity, GroupStates states, String type) {
        GroupStates.Entry e = states.entry(type);
        Map<String, Double> group = Collections.unmodifiableMap(side.committed(entity, type));
        change(entity, type, e, GroupAvailability.AVAILABLE);
        startSeries(e);
        note(entity, type, () -> "settled");
        call(type, "settled", () -> {
            side.settled(entity, type, group);
            return Boolean.TRUE;
        });
    }

    private static void startSeries(GroupStates.Entry e) {
        e.attempts = 0;
        e.warned = false;
    }

    private void change(E entity, String type, GroupStates.Entry e, GroupAvailability to) {
        if (e.availability != to) {
            GroupAvailability from = e.availability;
            e.availability = to;
            note(entity, type, () -> "availability " + from + " -> " + to);
        }
    }

    /** Runs one call under the boundary's guard; {@link #ERROR} when it threw. Never disabled. */
    private Object call(String type, String signal, Supplier<Object> body) {
        String key = "group:" + type + " " + signal;
        try {
            Object result = body.get();
            counts.success(key);
            return result;
        } catch (Throwable t) {
            ErrorCounts.Entry entry = counts.failure(key);
            if (entry.total() == 1) {
                errorLog.accept("Statusworks: group signal " + key + " failed; later failures are counted, see"
                        + " /statusworks errors", t);
            }
            return ERROR;
        }
    }

    private static String describe(Object answer) {
        return answer == ERROR ? "error" : answer == Boolean.TRUE ? "confirmed" : "refused";
    }

    private void note(E entity, String type, Supplier<String> text) {
        if (diagnosticOn.getAsBoolean()) {
            diagnostic.accept("group type=" + type + " entity=" + side.label(entity) + " " + text.get()
                    + " values=" + side.committed(entity, type));
        }
    }
}
