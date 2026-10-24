package com.mahghuuuls.statusworks.core.lifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides what an entity leaving its world means. 1.12 has no leave event, so the decision combines what the
 * world listener sees with what this class remembered just before: a dimension travel or a logout, each
 * remembered for one server tick.
 *
 * <p>Only {@link Removal#DEATH} and {@link Removal#GONE} are ends; an unload, a logout, or a travel never ends
 * state, because the state is saved with the entity and comes back with it. Statuses end on death through the
 * death event itself; the classification here keeps the processed set right and tells the mark store what
 * happened to a mark participant.</p>
 */
public final class RemovalClassifier {

    /** What a removal means. */
    public enum Removal {
        /** The entity died. */
        DEATH("death"),
        /** The entity is gone for good without dying, for example a despawn or a removal by another mod. */
        GONE("gone"),
        /** The entity's chunk or world unloaded; it comes back when it loads again. */
        UNLOAD("unload"),
        /** A player logged out; the player comes back on the next login. */
        LOGOUT("logout"),
        /** The entity moved to another dimension; it comes back there. */
        TRAVEL("travel");

        private final String text;

        Removal(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }

        /** Whether the entity's state ends with this removal. */
        public boolean ends() {
            return this == DEATH || this == GONE;
        }
    }

    /** How long, in server ticks, a travel or logout signal is remembered. */
    static final long MEMORY_TICKS = 1;

    private final Map<UUID, Long> travelling = new HashMap<>();
    private final Map<UUID, Long> loggingOut = new HashMap<>();

    /**
     * The decision table, in order of precedence: a remembered travel, then a remembered logout, then death
     * (no health left), then an entity removed without being marked dead (its chunk or world unloaded), and
     * otherwise gone.
     */
    public static Removal classify(boolean travelled, boolean loggedOut, boolean died, boolean markedDead) {
        if (travelled) {
            return Removal.TRAVEL;
        }
        if (loggedOut) {
            return Removal.LOGOUT;
        }
        if (died) {
            return Removal.DEATH;
        }
        if (!markedDead) {
            return Removal.UNLOAD;
        }
        return Removal.GONE;
    }

    /** Classifies one removal at server tick {@code tick}. */
    public Removal classify(UUID entity, boolean died, boolean markedDead, long tick) {
        return classify(recent(travelling, entity, tick), recent(loggingOut, entity, tick), died, markedDead);
    }

    public void noteTravel(UUID entity, long tick) {
        travelling.put(entity, tick);
    }

    public void noteLogout(UUID entity, long tick) {
        loggingOut.put(entity, tick);
    }

    /** Forgets signals older than the memory; called once per server tick. */
    public void forget(long tick) {
        expire(travelling, tick);
        expire(loggingOut, tick);
    }

    private static boolean recent(Map<UUID, Long> signals, UUID entity, long tick) {
        Long at = signals.get(entity);
        return at != null && tick - at <= MEMORY_TICKS;
    }

    private static void expire(Map<UUID, Long> signals, long tick) {
        if (!signals.isEmpty()) {
            signals.values().removeIf(at -> tick - at > MEMORY_TICKS);
        }
    }
}
