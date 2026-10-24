package com.mahghuuuls.statusworks.api;

import net.minecraft.entity.Entity;

import javax.annotation.Nullable;

/**
 * Operations and queries on pools. Every method works on the logical server only; on the client, and for a dead
 * entity, a non-living entity, a fake player, or an unknown or disabled definition, operations report failure and
 * queries report nothing. Call it from the server thread only, as Minecraft's own world changes require.
 *
 * <p>Add, subtract, and set create a missing instance with its starting amount and then apply the change; every
 * other operation needs an existing instance. Amounts stay between 0 and the capacity. Queries never change or
 * create anything.</p>
 */
public interface PoolService {

    /** Adds to the amount, creating the instance if needed. */
    PoolChange add(Entity entity, String id, double amount);

    /** Subtracts from the amount, creating the instance if needed. */
    PoolChange subtract(Entity entity, String id, double amount);

    /** Sets the amount, creating the instance if needed. */
    PoolChange set(Entity entity, String id, double amount);

    /**
     * Sets this instance's capacity (above 0); lowering it clamps the amount, raising it keeps the amount. A clamp
     * runs "amount changed", and arriving at the new capacity runs "became full". An instance whose capacity was
     * set this way keeps it when the definition's capacity changes later, even when the value equals the definition's.
     */
    PoolChange setCapacity(Entity entity, String id, double capacity);

    /** Sets the remaining lifetime in active ticks (1 or more), replacing any earlier one. */
    PoolChange setLifetime(Entity entity, String id, int ticks);

    /** Extends the remaining lifetime by active ticks (1 or more); with no lifetime, starts one. */
    PoolChange extendLifetime(Entity entity, String id, int ticks);

    /** Pauses the progression rule; a {@code to_target} deadline keeps its remaining time. */
    PoolChange pause(Entity entity, String id);

    /** Resumes a paused progression rule. */
    PoolChange resume(Entity entity, String id);

    /** Ends the instance with reason {@code removed}. */
    PoolChange remove(Entity entity, String id);

    /** Whether the entity has an instance of the pool; an empty pool still exists. */
    boolean has(Entity entity, String id);

    /** The instance's current values, or null when it is absent. */
    @Nullable
    PoolSnapshot get(Entity entity, String id);
}
