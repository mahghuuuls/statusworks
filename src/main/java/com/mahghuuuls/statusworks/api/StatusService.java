package com.mahghuuuls.statusworks.api;

import net.minecraft.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Operations and queries on statuses. Every method works on the logical server only; on the client, and for a
 * dead entity, a non-living entity, or an unknown or disabled definition, operations report failure and per-id
 * queries report nothing (a dead player's kept statuses still read until the respawn). Queries never change
 * state. Call it from the server thread only, as Minecraft's own world
 * changes require.
 */
public interface StatusService {

    /** Stands for the group of stacks applied with no source entity. */
    UUID NO_SOURCE = new UUID(0L, 0L);

    /** Applies stacks with the definition's default duration and no source. */
    StackChange apply(Entity entity, String id, int stacks);

    /**
     * Applies stacks.
     *
     * @param source the entity that applied them, or null for none
     * @param durationTicks the duration of this application, or a value below 1 for the definition's default
     */
    StackChange apply(Entity entity, String id, int stacks, @Nullable Entity source, int durationTicks);

    /** Removes stacks, the ones closest to expiring first. */
    CountChange remove(Entity entity, String id, int stacks);

    /** Removes stacks of one source's group ({@link #NO_SOURCE} for stacks applied without a source). */
    CountChange remove(Entity entity, String id, int stacks, UUID source);

    /**
     * Sets the count of a group directly, clamped to the limit. For a shared status the source is ignored; for a
     * per-source status it names the group ({@link #NO_SOURCE} for stacks applied without a source).
     * New stacks get the definition's default duration; running timers do not change. A total of 0 ends the status.
     */
    CountChange setCount(Entity entity, String id, int count, UUID source);

    /** Ends the status on the entity. */
    CountChange clear(Entity entity, String id);

    /** Whether the entity has the status. */
    boolean has(Entity entity, String id);

    /** The total count across all groups. */
    int count(Entity entity, String id);

    /** The count of one group. */
    int count(Entity entity, String id, UUID source);

    /**
     * The remaining time in ticks: for {@code shared}, the timer; for {@code gradual}, the time to the next stack
     * loss; for {@code independent}, the next expiration; -1 for {@code none} or when the status is absent. For a
     * per-source status this is the soonest across its groups.
     */
    int remainingTicks(Entity entity, String id);

    /** The remaining time of one group, as {@link #remainingTicks(Entity, String)} describes. */
    int remainingTicks(Entity entity, String id, UUID source);

    /** The groups of a per-source status by source UUID ({@link #NO_SOURCE} for the no-source group). */
    List<UUID> sources(Entity entity, String id);

    /**
     * A read-only snapshot of the visible statuses active on the entity now, with each status's presentation,
     * total count, remaining time and, for a per-source status, its groups (see {@link StatusSnapshot}). Every
     * active status whose definition is not author-hidden is listed, whether or not it has particles; a player's
     * own HUD hiding does not apply. Names are given as the definition writes them, text or translation key, and are
     * not translated; a definition without a name gives the id's path, as Statusworks' HUD shows it. Taking a
     * snapshot changes nothing.
     *
     * <p>Server thread only. The snapshot is empty on the client, for a non-living entity, a fake player or an
     * entity without Statusworks state, and for an entity object that is not in a world (before its join, after it
     * left, for example a mob whose chunk unloaded, or a player object replaced at a respawn); unlike the per-id
     * queries, which still read an object that left. From the entity's join on, also while Forge reports the added
     * entity to listeners such as {@code PlayerEvent.StartTracking}, it lists the entity's statuses. A dead entity
     * still in its world lists what it kept: nothing for a mob, the kept statuses of a player until the respawn. It
     * reads only this entity's own state. Nothing is sent to clients: a mod that shows the statuses elsewhere
     * synchronizes them itself, and decides what it shows to whom.</p>
     *
     * @since 1.4.0
     */
    default StatusSnapshot snapshot(Entity entity) {
        return StatusSnapshot.EMPTY;
    }
}
