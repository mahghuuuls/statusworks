package com.mahghuuuls.statusworks.api;

import net.minecraft.entity.Entity;

/**
 * Operations and queries on cooldowns. Every method works on the logical server only; on the client, and for a
 * dead entity, a non-living entity, a fake player, or an unknown or disabled definition, operations report
 * failure and queries report "not running". Call it from the server thread only.
 *
 * <p>Times are active ticks; a duration below 1 means the definition's default. A cooldown that is not running
 * has no instance.</p>
 */
public interface CooldownService {

    /** Starts the cooldown with its default duration; a running cooldown keeps its time. */
    CooldownChange start(Entity entity, String id);

    /** Starts the cooldown; a running cooldown keeps its time. */
    CooldownChange start(Entity entity, String id, int ticks);

    /** Sets a new remaining time, starting the cooldown if it is not running. */
    CooldownChange restart(Entity entity, String id, int ticks);

    /** Adds time to a running cooldown. */
    CooldownChange extend(Entity entity, String id, int ticks);

    /** Removes time from a running cooldown; reaching 0, or going past it, ends it with reason {@code removed}. */
    CooldownChange reduce(Entity entity, String id, int ticks);

    /** Ends a running cooldown now, with reason {@code removed}. */
    CooldownChange clear(Entity entity, String id);

    /**
     * Starts the cooldown and returns true only when it is not running; otherwise changes nothing and returns
     * false. It runs no author action, and nothing undoes it if the author's action later fails. A call that
     * fails (client side, dead or invalid entity, unknown or disabled id) also returns false; {@link #isRunning}
     * and the server log tell the cases apart.
     */
    boolean startIfReady(Entity entity, String id);

    /** {@link #startIfReady(Entity, String)} with a duration. */
    boolean startIfReady(Entity entity, String id, int ticks);

    boolean isRunning(Entity entity, String id);

    /** Remaining active ticks, or 0 when not running. */
    int remaining(Entity entity, String id);
}
