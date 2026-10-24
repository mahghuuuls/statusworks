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
}
