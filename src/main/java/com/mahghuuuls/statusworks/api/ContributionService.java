package com.mahghuuuls.statusworks.api;

import net.minecraft.entity.Entity;

import java.util.Map;

/** Queries on managed contributions. Works on the logical server only; elsewhere it reports nothing. */
public interface ContributionService {

    /**
     * The current values of every contribution to the contribution type on the entity, by contribution key, in a
     * read-only map; empty when there are none.
     */
    Map<String, Double> currentValues(Entity entity, String typeId);

    /**
     * The contribution group of a type that asked for group signals ({@link
     * com.mahghuuuls.statusworks.api.contribution.ContributionGroupType}) on this entity object: its availability
     * and, when available, its complete values. Unavailable on the client, for an entity that has not joined a world
     * yet or was replaced, and for a type without group signals or that does not apply to the entity. The type's
     * {@code appliesTo} is asked at the moment of the read: when it says no, or fails, that read answers unavailable
     * or failed, whatever the stored state; it changes nothing until the next change, join or retry.
     */
    com.mahghuuuls.statusworks.api.contribution.ContributionGroup group(Entity entity, String typeId);
}
