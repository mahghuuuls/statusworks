package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.ParticleAudience;

/**
 * Who sees the particles of one entry on one entity, and when to check again. Plain values; no Minecraft types. A
 * player can see particles only on an entity its client has: the entity itself, or one the server tracks for it.
 */
final class AudienceRules {

    /** Range without long distance, in blocks; clients also draw particles only this close to the camera. */
    static final double NEAR = 32;
    /** Ticks between checks of who may see an entity. */
    static final int RECHECK_TICKS = 20;

    private AudienceRules() {
    }

    /** One player that could see the entity's particles. */
    static final class Viewer {
        /** Whether the player is the entity that carries the entry (for a mark, its target). */
        final boolean self;
        /** Whether the server tracks the entity for this player, so its client has it. */
        final boolean tracking;
        final double distanceSq;
        /** For a mark: whether the player is its source. */
        final boolean source;

        Viewer(boolean self, boolean tracking, double distanceSq, boolean source) {
            this.self = self;
            this.tracking = tracking;
            this.distanceSq = distanceSq;
            this.source = source;
        }
    }

    /** Whether an entity shows particles at all: not a spectator, and alive. */
    static boolean emits(boolean spectator, boolean alive) {
        return !spectator && alive;
    }

    /**
     * Whether the viewer sees the particles of an entry with this audience. The audience picks who; the range applies
     * to everyone but the entity itself: within {@link #NEAR} blocks, or anywhere the client has the entity with
     * long distance. Participant audiences apply to marks only.
     */
    static boolean sees(boolean mark, ParticleAudience audience, boolean far, Viewer v) {
        if (!v.self && !(v.tracking && (far || v.distanceSq <= NEAR * NEAR))) {
            return false;
        }
        switch (audience) {
            case SOURCE:
                return mark && v.source;
            case TARGET:
                return mark && v.self;
            case BOTH:
                return mark && (v.source || v.self);
            default:
                return true;
        }
    }

    /**
     * The tick the check of an entity is due from: the last tick at or before {@code tick} on the entity's own
     * slot, so checks spread over the ticks and a check is never older than {@link #RECHECK_TICKS}.
     */
    static long slotStart(long tick, int entityId) {
        long slot = Math.floorMod(entityId, RECHECK_TICKS);
        return tick - Math.floorMod(tick - slot, RECHECK_TICKS);
    }

    /** Whether a check made from {@code checkedFrom} is still current at {@code tick}. */
    static boolean current(long checkedFrom, long tick) {
        return tick - checkedFrom < RECHECK_TICKS;
    }
}
