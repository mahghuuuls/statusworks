package com.mahghuuuls.statusworks.api.contribution;

/** Whether a contribution group on an entity can be used as current. */
public enum GroupAvailability {
    /**
     * There is no group for this entity object yet (it has not joined a world, it was replaced at a respawn, or it
     * left its world), or the question was asked on the client or for an entity or type that has no group.
     */
    UNAVAILABLE,
    /** The invalidation was confirmed and the settled signal is not delivered yet. Do not use earlier values. */
    UPDATING,
    /**
     * An invalidation failed or Statusworks could not finish an update. Earlier values must not be used; Statusworks
     * retries, and only a confirmed invalidation followed by a settled signal makes the group available again. (A
     * single read can also answer failed when the type's {@code appliesTo} fails at that moment; see
     * {@code ContributionService#group}.)
     */
    FAILED,
    /** The group is committed; its values are complete. No values means the entity has none of that type. */
    AVAILABLE
}
