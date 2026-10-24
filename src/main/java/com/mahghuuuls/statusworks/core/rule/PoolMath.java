package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.ProgressionState;
import com.mahghuuuls.statusworks.api.definition.Direction;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolStart;
import com.mahghuuuls.statusworks.api.definition.Progression;

/**
 * Every rule of a pool in one place: the starting amount, clamping to the range, capacity changes, the lifetime,
 * pausing, the four progression rules in closed form over any number of active ticks, the {@code to_target}
 * deadline, the cutoff, and boundary arrival. Plain Java with no Minecraft types.
 *
 * <p>Each rule is a closed form over N ticks, so advancing 20 ticks once and 1 tick twenty times give the same
 * amount, and the result never depends on how often the instance is updated. A {@code to_target} rule moves the
 * current amount toward the target over the remaining time to its deadline, so an outside change is picked up at
 * the next step and the deadline holds.</p>
 *
 * <p>The definition passed in is assumed valid (the registry disables invalid ones).</p>
 */
public final class PoolMath {

    public static final double TICKS_PER_SECOND = 20.0;
    /** The default cutoff, as a share of the capacity. */
    public static final double DEFAULT_CUTOFF_SHARE = 0.001;

    private PoolMath() {
    }

    /** A new instance: its starting amount, capacity, lifetime, and {@code to_target} deadline. */
    public static PoolState create(PoolDefinition definition) {
        PoolState state = new PoolState();
        state.capacity = definition.capacity();
        PoolStart start = definition.start();
        if (start == PoolStart.FULL) {
            state.amount = state.capacity;
        } else if (start == PoolStart.AMOUNT) {
            state.amount = clamp(definition.startAmount(), state.capacity);
        }
        if (definition.lifetime() >= 1) {
            state.lifetimeEnd = definition.lifetime();
        }
        if (definition.progression() == Progression.TO_TARGET) {
            state.deadline = definition.targetDuration();
        }
        return state;
    }

    /** The amount kept between 0 and the capacity; not-a-number becomes 0. */
    public static double clamp(double amount, double capacity) {
        if (Double.isNaN(amount)) {
            return 0;
        }
        return Math.max(0, Math.min(capacity, amount));
    }

    public static void add(PoolState state, double delta) {
        state.amount = clamp(state.amount + delta, state.capacity);
    }

    public static void set(PoolState state, double amount) {
        state.amount = clamp(amount, state.capacity);
    }

    /** Sets the capacity (above 0); lowering it clamps the amount, raising it keeps the amount. */
    public static void setCapacity(PoolState state, double capacity) {
        state.capacity = capacity;
        state.capacitySet = true;
        state.amount = clamp(state.amount, capacity);
    }

    public static void setLifetime(PoolState state, int ticks) {
        state.lifetimeEnd = state.age + ticks;
    }

    /** Extends the lifetime; with none, starts one of that length. */
    public static void extendLifetime(PoolState state, int ticks) {
        state.lifetimeEnd = (state.lifetimeEnd < 0 ? state.age : state.lifetimeEnd) + ticks;
    }

    public static void setPaused(PoolState state, boolean paused) {
        state.paused = paused;
    }

    public static boolean isEmpty(double amount) {
        return amount <= 0;
    }

    public static boolean isFull(double amount, double capacity) {
        return amount >= capacity;
    }

    /** Whether a change arrived at 0: it was above 0 before and is 0 now. */
    public static boolean becameEmpty(double before, double after) {
        return !isEmpty(before) && isEmpty(after);
    }

    /** Whether a change arrived at the capacity: it was below the capacity before and is at it now. */
    public static boolean becameFull(double before, double capacityBefore, double after, double capacityAfter) {
        return !isFull(before, capacityBefore) && isFull(after, capacityAfter);
    }

    /**
     * Advances the instance by {@code ticks} active ticks: the progression rule unless paused, then both clocks.
     *
     * @return whether a {@code to_target} rule reached its target in these ticks
     */
    public static boolean advance(PoolDefinition definition, PoolState state, long ticks) {
        boolean reached = false;
        if (ticks <= 0) {
            return false;
        }
        if (!state.paused) {
            reached = progress(definition, state, ticks);
            state.progressAge += ticks;
        }
        state.age += ticks;
        return reached;
    }

    private static boolean progress(PoolDefinition definition, PoolState state, long ticks) {
        Progression rule = definition.progression();
        double seconds = ticks / TICKS_PER_SECOND;
        double sign = definition.direction() == Direction.DRAIN ? -1 : 1;
        double amount = state.amount;
        if (rule == Progression.FIXED) {
            amount += sign * definition.rate() * seconds;
        } else if (rule == Progression.PERCENT_OF_CAPACITY) {
            amount += sign * definition.rate() / 100.0 * state.capacity * seconds;
        } else if (rule == Progression.PERCENT_OF_CURRENT) {
            amount *= Math.pow(1 + sign * definition.rate() / 100.0, seconds);
            if (sign < 0 && amount < cutoff(definition, state)) {
                amount = 0;
            }
        } else if (rule == Progression.TO_TARGET) {
            if (state.targetDone || state.deadline < 0) {
                return false;
            }
            long remaining = state.deadline - state.progressAge;
            double target = clamp(definition.target(), state.capacity);
            if (ticks >= remaining) {
                state.amount = target;
                state.targetDone = true;
                return true;
            }
            amount += (target - amount) * ticks / (double) remaining;
        } else {
            return false;
        }
        state.amount = clamp(amount, state.capacity);
        return false;
    }

    /** The amount below which a {@code percent_of_current} drain snaps to 0. */
    public static double cutoff(PoolDefinition definition, PoolState state) {
        return definition.cutoff() >= 0 ? definition.cutoff() : state.capacity * DEFAULT_CUTOFF_SHARE;
    }

    public static boolean expired(PoolState state) {
        return state.lifetimeEnd >= 0 && state.age >= state.lifetimeEnd;
    }

    /** Active ticks until the lifetime ends, or -1 when there is none. */
    public static int remainingLifetime(PoolState state) {
        return state.lifetimeEnd < 0 ? -1 : clampTicks(state.lifetimeEnd - state.age);
    }

    /** Unpaused active ticks until the {@code to_target} deadline, or -1 when there is none or it was reached. */
    public static int remainingToDeadline(PoolDefinition definition, PoolState state) {
        if (definition.progression() != Progression.TO_TARGET || state.targetDone || state.deadline < 0) {
            return -1;
        }
        return clampTicks(state.deadline - state.progressAge);
    }

    public static ProgressionState progressionState(PoolDefinition definition, PoolState state) {
        Progression rule = definition.progression();
        if (rule == null || rule == Progression.NONE || (rule == Progression.TO_TARGET && state.targetDone)) {
            return ProgressionState.NONE;
        }
        return state.paused ? ProgressionState.PAUSED : ProgressionState.ACTIVE;
    }

    /**
     * Fits a restored instance to its definition as registered now: unless an operation set the capacity, the
     * instance takes the definition's capacity (so a pack that lowers or raises it reaches saved pools); a
     * capacity that is not above 0 always does; the amount is clamped; and a {@code to_target} rule added since
     * the save gets its deadline from now. The lifetime is kept.
     *
     * @return whether anything changed
     */
    public static boolean fit(PoolDefinition definition, PoolState state) {
        boolean changed = false;
        if (!(state.capacity > 0) || (!state.capacitySet && state.capacity != definition.capacity())) {
            state.capacity = definition.capacity();
            state.capacitySet = false;
            changed = true;
        }
        double clamped = clamp(state.amount, state.capacity);
        if (clamped != state.amount) {
            state.amount = clamped;
            changed = true;
        }
        if (definition.progression() == Progression.TO_TARGET && state.deadline < 0 && !state.targetDone) {
            state.deadline = state.progressAge + definition.targetDuration();
            changed = true;
        }
        return changed;
    }

    private static int clampTicks(long ticks) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, ticks));
    }
}
