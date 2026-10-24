package com.mahghuuuls.statusworks.api.contribution;

import net.minecraft.entity.EntityLivingBase;

/**
 * An addon's own contribution target, for a resource Statusworks does not own (for example another mod's stamina).
 * Register it with {@link com.mahghuuuls.statusworks.api.StatusworksApi#registerContributionType} during
 * startup; definitions then name it by {@link #id()}.
 *
 * <p>Statusworks tells the type each new value of each contribution on an entity it applies to, and when a
 * contribution ends; a pull-based addon instead asks
 * {@link com.mahghuuuls.statusworks.api.ContributionService#currentValues}. Calls run on the server thread through
 * Statusworks' callback guard, so an exception is logged and counted, never thrown at the caller.</p>
 */
public interface ContributionType {

    /** The id definitions use, for example {@code jass:max_stamina}. */
    String id();

    /** Whether this type applies to the entity; entities it does not apply to are never notified. */
    boolean appliesTo(EntityLivingBase entity);

    /**
     * The contribution with this key now has this value on the entity. It is sent again with the same value each
     * time the entity joins a world (loading, a dimension change, a respawn), so it must be safe to repeat.
     */
    void onValue(EntityLivingBase entity, String key, double value);

    /** The contribution with this key ended on the entity. */
    void onEnd(EntityLivingBase entity, String key);
}
