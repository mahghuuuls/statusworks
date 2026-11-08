package com.mahghuuuls.statusworks.core.contribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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
 * {@link #runDeferred(Predicate)}, which the server calls once per tick, kept units keeping their kind, and the limit
 * listener is told. Entities are compared by identity, so a new entity object for the same UUID is a different
 * entity.</p>
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
    private final Consumer<E> onLimit;

    /**
     * @param onLimit told when an entity reached the limit and its remaining passes were deferred
     */
    PassSerializer(Consumer<E> onLimit) {
        this.onLimit = onLimit;
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
                deferred.put(entity, waitingUnits);
            }
            throw t;
        } finally {
            running.remove(entity);
            pending.remove(entity);
        }
    }

    /** Runs what each entity left waiting, as a new chain, for the entities that are still where a pass may run. */
    void runDeferred(Predicate<E> stillHere) {
        if (deferred.isEmpty()) {
            return;
        }
        List<Map.Entry<E, Pending>> due = new ArrayList<>(deferred.entrySet());
        deferred.clear();
        for (Map.Entry<E, Pending> e : due) {
            Pending p = e.getValue();
            if (!stillHere.test(e.getKey()) || p.isEmpty()) {
                continue;
            }
            Runnable first = p.next();
            deferred.put(e.getKey(), p);
            request(e.getKey(), first, p.again);
        }
    }

    boolean isRunning(E entity) {
        return running.contains(entity);
    }

    boolean isDeferred(E entity) {
        return deferred.containsKey(entity);
    }
}
