package com.mahghuuuls.statusworks.core.contribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Runs contribution passes one at a time per entity.
 *
 * <p>A pass requested while a pass for the same entity runs is not started inside it: it runs after the current
 * pass returns, so every pass sees a committed record. A plain request becomes the entity's plain next pass; a kept
 * request (a join, a retry) runs its own unit after the current pass, every kept unit in the order requested. When
 * passes keep requesting more passes, at most {@value #MAX_PASSES_IN_A_ROW} run in a row; what remains waits for
 * {@link #runDeferred(Predicate)} of the next server tick, kept units keeping their kind, and the limit listener is
 * told. The server calls {@link #nextTick()} when a tick starts and {@code runDeferred} when it ends; what waited
 * since an earlier tick runs then, and what was deferred during the same tick (by a world tick, a command, or the
 * deferred passes themselves) waits for the next one. Entities are compared by identity, so a new entity object for
 * the same UUID is a different entity.</p>
 *
 * <p>{@link #hasWaitingWork(Object)} tells whether passes for an entity still have to run: a follow-up of its running
 * pass, deferred passes, or deferred passes due in a drain in progress that have not had their turn yet.</p>
 *
 * <p>Used on the server thread only.</p>
 */
final class PassSerializer<E> {

    static final int MAX_PASSES_IN_A_ROW = 8;

    /** What still has to run for one entity: kept units in order, and whether a plain pass is wanted. */
    private static final class Pending {
        final List<Runnable> kept = new ArrayList<>(1);
        boolean plain;
        Runnable again;
        /** The tick in which these passes were deferred. */
        long deferredAt;

        boolean isEmpty() {
            return kept.isEmpty() && !plain;
        }

        /** The next pass: the next kept unit, or the plain pass. */
        Runnable next() {
            if (!kept.isEmpty()) {
                return kept.remove(0);
            }
            plain = false;
            return again;
        }
    }

    private final Set<E> running = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<E, Pending> pending = new IdentityHashMap<>();
    private final Map<E, Pending> deferred = new IdentityHashMap<>();
    /** The entities taken from {@link #deferred} by a drain in progress whose turn has not come yet. */
    private final Set<E> due = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<E> onLimit;
    private final Consumer<E> onDrain;
    private long tick;

    /**
     * @param onLimit told when an entity reached the limit and its remaining passes were deferred
     */
    PassSerializer(Consumer<E> onLimit) {
        this(onLimit, e -> { });
    }

    /**
     * @param onLimit told when an entity reached the limit and its remaining passes were deferred
     * @param onDrain told when an entity's deferred passes start to run
     */
    PassSerializer(Consumer<E> onLimit, Consumer<E> onDrain) {
        this.onLimit = onLimit;
        this.onDrain = onDrain;
    }

    /**
     * Runs {@code first} now, then {@code again} as long as plain passes for the entity are requested while one runs,
     * at most {@value #MAX_PASSES_IN_A_ROW} passes in a row. While a pass for the entity runs, only records the
     * request as a plain pass.
     */
    void request(E entity, Runnable first, Runnable again) {
        request(entity, first, again, false);
    }

    /**
     * As {@link #request(Object, Runnable, Runnable)}; with {@code keep}, a request recorded while a pass runs still
     * runs its own {@code first} after that pass, instead of becoming a plain pass. A request recorded while a pass
     * runs sets the plain follow-up to its own {@code again}; the last one recorded wins.
     */
    void request(E entity, Runnable first, Runnable again, boolean keep) {
        if (running.contains(entity)) {
            Pending p = pending.computeIfAbsent(entity, k -> new Pending());
            p.again = again;
            if (keep) {
                p.kept.add(first);
            } else {
                p.plain = true;
            }
            return;
        }
        Pending waiting = deferred.remove(entity);
        running.add(entity);
        try {
            Pending p = new Pending();
            p.again = again;
            if (waiting != null) {
                // A pass that runs now covers a waiting plain pass; waiting kept units still run after it.
                p.kept.addAll(waiting.kept);
            }
            pending.put(entity, p);
            Runnable next = first;
            int passes = 0;
            while (true) {
                next.run();
                passes++;
                if (p.isEmpty()) {
                    return;
                }
                if (passes >= MAX_PASSES_IN_A_ROW) {
                    p.deferredAt = tick;
                    deferred.put(entity, p);
                    onLimit.accept(entity);
                    return;
                }
                next = p.next();
            }
        } catch (Throwable t) {
            // A failing pass loses its plain follow-up, but kept units (a join, a retry) wait for the next tick.
            Pending p = pending.get(entity);
            if (p != null && !p.kept.isEmpty()) {
                Pending waitingUnits = new Pending();
                waitingUnits.kept.addAll(p.kept);
                waitingUnits.again = p.again;
                waitingUnits.deferredAt = tick;
                deferred.put(entity, waitingUnits);
            }
            throw t;
        } finally {
            running.remove(entity);
            pending.remove(entity);
        }
    }

    /** Marks the start of a server tick: passes deferred before this call may run at this tick's end. */
    void nextTick() {
        tick++;
    }

    /**
     * Runs what each entity left waiting since an earlier tick, as a new chain, for the entities that are still where
     * a pass may run. What was deferred during this tick stays waiting for the next tick. Until its turn, each due
     * entity still has waiting work; when a pass of the drain fails, the passes that had no turn yet are dropped.
     */
    void runDeferred(Predicate<E> stillHere) {
        if (deferred.isEmpty()) {
            return;
        }
        List<E> dueEntities = new ArrayList<>();
        List<Pending> duePasses = new ArrayList<>();
        for (Iterator<Map.Entry<E, Pending>> it = deferred.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<E, Pending> e = it.next();
            if (e.getValue().deferredAt < tick) {
                dueEntities.add(e.getKey());
                duePasses.add(e.getValue());
                it.remove();
            }
        }
        due.addAll(dueEntities);
        try {
            for (int i = 0; i < dueEntities.size(); i++) {
                E entity = dueEntities.get(i);
                Pending p = duePasses.get(i);
                due.remove(entity);
                if (!stillHere.test(entity) || p.isEmpty()) {
                    continue;
                }
                onDrain.accept(entity);
                Runnable first = p.next();
                deferred.put(entity, p);
                request(entity, first, p.again);
            }
        } finally {
            due.clear();
        }
    }

    /**
     * Whether passes for the entity still have to run: a plain pass or kept units recorded while its pass runs, passes
     * deferred by the limit, or deferred passes due in the drain in progress that have not had their turn. The pass
     * that is running is not waiting work, and neither is its empty bookkeeping.
     */
    boolean hasWaitingWork(E entity) {
        Pending p = pending.get(entity);
        if (p != null && !p.isEmpty()) {
            return true;
        }
        Pending d = deferred.get(entity);
        return (d != null && !d.isEmpty()) || due.contains(entity);
    }

    boolean isRunning(E entity) {
        return running.contains(entity);
    }

    boolean isDeferred(E entity) {
        return deferred.containsKey(entity);
    }
}
