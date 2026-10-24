package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.Overflow;
import com.mahghuuuls.statusworks.api.definition.RefreshRule;

/**
 * Every rule of a mark in one place: what marking a target does at the target limit, the refresh rules with the
 * maximum remaining time, durations, expiry, the interval, the inactive timeout, the activity rule, and death.
 * Times are active-tick ages: a mark counts the active ticks it has lived, and its timer is the age at which it
 * runs out ({@code -1} for none). Plain values in and out; no Minecraft types.
 */
public final class MarkLimits {

    /** No end age: the mark never expires. */
    public static final long NO_END = -1;

    /** What marking a target does. */
    public enum Decision {
        /** The source already marks this target: refresh that mark. */
        REFRESH,
        /** Below the limit: create a mark. */
        CREATE,
        /** At the limit with {@code replace_oldest}: end the source's oldest mark, then create. */
        REPLACE_OLDEST,
        /** At the limit with {@code reject}: create nothing. */
        REJECT
    }

    private MarkLimits() {
    }

    /**
     * @param held how many marks of the definition the source holds now, active and inactive
     */
    public static Decision decide(MarkDefinition definition, boolean alreadyMarked, int held) {
        if (alreadyMarked) {
            return Decision.REFRESH;
        }
        int limit = definition.targetLimit();
        if (limit == MarkDefinition.UNLIMITED || held < limit) {
            return Decision.CREATE;
        }
        return definition.overflow() == Overflow.REJECT ? Decision.REJECT : Decision.REPLACE_OLDEST;
    }

    /** The duration of a call: its ticks when 1 or more, otherwise the definition's default (maybe none). */
    public static int duration(MarkDefinition definition, int ticks) {
        return ticks >= 1 ? ticks : definition.defaultDuration();
    }

    /** The end age of a new mark created at {@code age}. */
    public static long endAge(long age, int duration) {
        return duration < 1 ? NO_END : age + duration;
    }

    /**
     * The end age after marking an already marked target: {@code reset} starts the duration over, {@code extend}
     * adds it to the remaining time up to the maximum (never below what remains), {@code preserve} changes
     * nothing. A duration of none makes a reset or an extended mark never expire.
     */
    public static long refresh(MarkDefinition definition, long age, long endAge, int duration) {
        RefreshRule rule = definition.refresh();
        if (rule == RefreshRule.PRESERVE) {
            return endAge;
        }
        if (duration < 1 || (rule == RefreshRule.EXTEND && endAge == NO_END)) {
            return NO_END;
        }
        if (rule == RefreshRule.EXTEND) {
            long remaining = Math.max(0, endAge - age);
            long extended = remaining + duration;
            int maximum = definition.maximumRemaining();
            if (maximum >= 1 && extended > maximum) {
                extended = Math.max(remaining, maximum);
            }
            return age + extended;
        }
        return age + duration;
    }

    /** Remaining active ticks, or {@code -1} for a mark that never expires. */
    public static int remaining(long age, long endAge) {
        if (endAge == NO_END) {
            return -1;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, endAge - age));
    }

    public static boolean expired(long age, long endAge) {
        return endAge != NO_END && age >= endAge;
    }

    /**
     * Whether the coming active tick completes an interval, so the interval callback runs before that tick's
     * timer: the first run is one interval after creation, counting active ticks only.
     */
    public static boolean intervalDue(MarkDefinition definition, long age) {
        int interval = definition.interval();
        return interval >= 1 && (age + 1) % interval == 0;
    }

    /** Whether an inactive mark has been inactive for its definition's maximum (a maximum of 0 never times out). */
    public static boolean timedOut(MarkDefinition definition, int inactiveTicks) {
        int maximum = definition.maximumInactive();
        return maximum >= 1 && inactiveTicks >= maximum;
    }

    /**
     * The activity rule: both participants are loaded and alive, in the same world, and within the maximum
     * distance when the definition sets one. Distance alone never ends a mark.
     *
     * @param distanceSquared the squared distance between the participants (used only when both are in one world)
     */
    public static boolean active(MarkDefinition definition, boolean sourceLoaded, boolean targetLoaded,
                                 boolean sameWorld, double distanceSquared) {
        if (!sourceLoaded || !targetLoaded || !sameWorld) {
            return false;
        }
        double maximum = definition.maximumDistance();
        return !(maximum > 0) || distanceSquared <= maximum * maximum;
    }

    /**
     * Whether a mark survives the death of one participant: only a player participant, and only when the
     * definition keeps the mark on that side's death. A mob's death always ends its marks.
     */
    public static boolean survivesDeath(MarkDefinition definition, boolean diedAsSource, boolean player) {
        return player && (diedAsSource ? definition.keepOnSourceDeath() : definition.keepOnTargetDeath());
    }
}
