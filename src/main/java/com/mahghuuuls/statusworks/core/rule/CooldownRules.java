package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;

import javax.annotation.Nullable;

/**
 * Every rule of a cooldown: start (a running one keeps its time), restart, extend, reduce (0 ends it), start only
 * if ready, the remaining time, and expiry over active ticks. Plain Java with no Minecraft types. A cooldown that
 * is not running is represented by no state at all.
 */
public final class CooldownRules {

    private CooldownRules() {
    }

    /** The duration a call asks for: its own when 1 or more, otherwise the definition's default. */
    public static int duration(CooldownDefinition definition, int ticks) {
        return ticks >= 1 ? ticks : definition.defaultDuration();
    }

    /** A newly started cooldown of {@code ticks}. */
    public static CooldownState start(int ticks) {
        CooldownState state = new CooldownState();
        state.end = ticks;
        return state;
    }

    /**
     * A start: when the cooldown is running, it keeps its time and this returns null; otherwise this returns the
     * newly started cooldown. "Start" and "start only if ready" both follow this rule.
     */
    @Nullable
    public static CooldownState startIfReady(@Nullable CooldownState current, int ticks) {
        return running(current) ? null : start(ticks);
    }

    /**
     * A restart: a running cooldown gets the new remaining time and is returned; a stopped one is started anew.
     */
    public static CooldownState restartOrStart(@Nullable CooldownState current, int ticks) {
        if (!running(current)) {
            return start(ticks);
        }
        restart(current, ticks);
        return current;
    }

    /** Sets a new remaining time. */
    public static void restart(CooldownState state, int ticks) {
        state.end = state.age + ticks;
    }

    public static void extend(CooldownState state, int ticks) {
        state.end += ticks;
    }

    /** Removes time; returns whether the cooldown has ended (no time left, including exactly 0). */
    public static boolean reduce(CooldownState state, int ticks) {
        state.end -= ticks;
        return !running(state);
    }

    /** Advances by one active tick; returns whether the cooldown expired in it. */
    public static boolean tick(CooldownState state) {
        state.age++;
        return !running(state);
    }

    public static boolean running(@Nullable CooldownState state) {
        return state != null && state.end > state.age;
    }

    /** Remaining active ticks, or 0 when not running. */
    public static int remaining(@Nullable CooldownState state) {
        if (!running(state)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, state.end - state.age);
    }
}
