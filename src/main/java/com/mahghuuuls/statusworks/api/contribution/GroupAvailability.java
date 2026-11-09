package com.mahghuuuls.statusworks.api.contribution;

/** Whether a contribution group on an entity can be used as current. */
public enum GroupAvailability {
    /**
     * There is no group for this entity object yet (it has not joined a world, it was replaced at a respawn, or it
     * left its world), or the question was asked on the client or for an entity or type that has no group.
     */
    UNAVAILABLE,
    /** The invalidation was confirmed and the settled signal has not started yet. Do not use earlier values. */
    UPDATING,
    /**
     * An invalidation failed or Statusworks could not finish an update. Earlier values must not be used; Statusworks
     * retries, and only a confirmed invalidation makes the group available again, at the start of the settled signal
     * that follows (pending instead while contribution work for the entity waits). (A single read can also answer
     * failed when the type's {@code appliesTo} fails at that moment; see {@code ContributionService#group}.)
     */
    FAILED,
    /**
     * Contribution work for this entity object is waiting: an update was asked for while one ran, or updates were
     * held back to the next server tick because the entity's contributions kept changing. The group may be about to
     * change; earlier values must not be used, and no values are given. Read again later. Every type with group
     * signals on that entity object answers this while the work waits, also types the work will not change. When the
     * work changes nothing for the type, the group becomes available again with the same values and no signal.
     *
     * @since 1.3.0
     */
    PENDING,
    /** The group is committed; its values are complete. No values means the entity has none of that type. */
    AVAILABLE
}
