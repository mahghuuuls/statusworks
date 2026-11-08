package com.mahghuuuls.statusworks.api.contribution;

import net.minecraft.entity.EntityLivingBase;

import java.util.Map;

/**
 * A contribution type that also receives group signals. Register it like any
 * {@link ContributionType}; implementing this interface asks for the signals.
 *
 * <p>For every entity the type applies to, Statusworks calls {@link #invalidate} before the type's contribution
 * group on that entity can change, and {@link #settled} with the complete committed group after the change. Both run
 * on the server thread, never later than the change itself, and are never switched off after errors. Pairs come in
 * order: an invalidation, the per-entry notices of that change, then its settled signal. Every join of the entity to
 * a world (loading, login, respawn, End exit, dimension change) sends a pair too, even with an empty group.</p>
 *
 * <p>Use {@link com.mahghuuuls.statusworks.api.ContributionService#group} before trusting a result: only
 * {@link GroupAvailability#AVAILABLE} is current.</p>
 *
 * <p>When a change, a join or a retry finds that the type no longer applies to an entity it had a group on,
 * the type gets a last invalidation and no settled signal; the group is unavailable from then on. {@link #appliesTo} is also asked when the group is read, so it must
 * be cheap and must not read the group itself.</p>
 */
public interface ContributionGroupType extends ContributionType {

    /**
     * The group of this type on the entity is about to change. Stop treating the earlier result as current, then
     * return {@code true} to confirm. Returning {@code false} or throwing makes the group
     * {@link GroupAvailability#FAILED} until Statusworks retries and a later invalidation is confirmed.
     */
    boolean invalidate(EntityLivingBase entity);

    /**
     * The group of this type on the entity is committed: {@code group} holds every value by contribution key,
     * read-only; empty means the entity has none of this type. The group is available while this runs.
     */
    void settled(EntityLivingBase entity, Map<String, Double> group);
}
