package com.mahghuuuls.statusworks.api.callback;

import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;

/**
 * What a pool callback is told: the entity, the pool, the amount before and after, the capacity, and the removal
 * reason when the pool ended. For "became empty", "became full", and "target reached", before and after are the
 * amounts around the change that arrived; for "removed", after is 0.
 */
public final class PoolContext {

    private final EntityLivingBase entity;
    private final String definitionId;
    private final double amountBefore;
    private final double amountAfter;
    private final double capacity;
    @Nullable private final String reason;

    public PoolContext(EntityLivingBase entity, String definitionId, double amountBefore, double amountAfter,
                       double capacity, @Nullable String reason) {
        this.entity = entity;
        this.definitionId = definitionId;
        this.amountBefore = amountBefore;
        this.amountAfter = amountAfter;
        this.capacity = capacity;
        this.reason = reason;
    }

    public EntityLivingBase entity() {
        return entity;
    }

    public String definitionId() {
        return definitionId;
    }

    public double amountBefore() {
        return amountBefore;
    }

    public double amountAfter() {
        return amountAfter;
    }

    public double capacity() {
        return capacity;
    }

    /** Why the pool ended, for a removed callback; otherwise null. */
    @Nullable
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return definitionId + " " + amountBefore + "->" + amountAfter + " of " + capacity
                + (reason != null ? " reason=" + reason : "");
    }
}
