package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.Overflow;
import com.mahghuuuls.statusworks.api.definition.RefreshRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkLimitsTest {

    private static MarkDefinition.Builder mark() {
        return MarkDefinition.builder("t:m");
    }

    @Test
    void theLimitDecidesBetweenCreateReplaceAndReject() {
        MarkDefinition two = mark().targetLimit(2).build();
        assertEquals(MarkLimits.Decision.CREATE, MarkLimits.decide(two, false, 0));
        assertEquals(MarkLimits.Decision.CREATE, MarkLimits.decide(two, false, 1));
        assertEquals(MarkLimits.Decision.REPLACE_OLDEST, MarkLimits.decide(two, false, 2), "replace_oldest is the default");
        assertEquals(MarkLimits.Decision.REFRESH, MarkLimits.decide(two, true, 2), "a marked target is refreshed, never replaced");
        MarkDefinition reject = mark().targetLimit(2).overflow(Overflow.REJECT).build();
        assertEquals(MarkLimits.Decision.REJECT, MarkLimits.decide(reject, false, 2));
        MarkDefinition unlimited = mark().unlimitedTargets().build();
        assertEquals(MarkLimits.Decision.CREATE, MarkLimits.decide(unlimited, false, 10_000));
        assertEquals(MarkLimits.Decision.REPLACE_OLDEST, MarkLimits.decide(mark().build(), false, 1), "limit 1 by default");
    }

    @Test
    void resetRefreshStartsTheDurationOverAndKeepsOneMark() {
        MarkDefinition d = mark().defaultDuration(100).build();
        long end = MarkLimits.endAge(0, 100);
        // REQ-054: 10 ticks left on a 100-tick mark, then reset: 100 remain.
        assertEquals(10, MarkLimits.remaining(90, end));
        long refreshed = MarkLimits.refresh(d, 90, end, MarkLimits.duration(d, 0));
        assertEquals(100, MarkLimits.remaining(90, refreshed));
    }

    @Test
    void extendAddsUpToTheMaximumAndPreserveChangesNothing() {
        MarkDefinition extend = mark().refresh(RefreshRule.EXTEND).maximumRemaining(250).build();
        assertEquals(90, MarkLimits.remaining(10, MarkLimits.refresh(extend, 10, 50, 50)));
        assertEquals(250, MarkLimits.remaining(10, MarkLimits.refresh(extend, 10, 210, 100)), "capped at the maximum");
        assertEquals(300, MarkLimits.remaining(0, MarkLimits.refresh(extend, 0, 300, 100)),
                "never below what remains");
        MarkDefinition preserve = mark().refresh(RefreshRule.PRESERVE).build();
        assertEquals(40, MarkLimits.remaining(10, MarkLimits.refresh(preserve, 10, 50, 100)));
    }

    @Test
    void noDurationNeverExpires() {
        MarkDefinition none = mark().noDuration().build();
        int duration = MarkLimits.duration(none, 0);
        long end = MarkLimits.endAge(0, duration);
        assertEquals(MarkLimits.NO_END, end);
        assertEquals(-1, MarkLimits.remaining(1_000_000, end));
        assertFalse(MarkLimits.expired(Long.MAX_VALUE - 1, end));
        assertEquals(50, MarkLimits.duration(none, 50), "a call's own duration wins");
        assertEquals(MarkLimits.NO_END, MarkLimits.refresh(none, 5, 50, duration), "a reset to none never expires");
    }

    @Test
    void expiryAndIntervalCountActiveTicks() {
        MarkDefinition d = mark().defaultDuration(3).onInterval(2, c -> { }).build();
        long end = MarkLimits.endAge(0, 3);
        assertFalse(MarkLimits.expired(2, end));
        assertTrue(MarkLimits.expired(3, end));
        assertFalse(MarkLimits.intervalDue(d, 0));
        assertTrue(MarkLimits.intervalDue(d, 1), "the first interval completes on the second active tick");
        assertFalse(MarkLimits.intervalDue(mark().build(), 1), "no interval callback, no interval");
    }

    @Test
    void theInactiveTimeoutCountsOnlyWithAMaximum() {
        MarkDefinition timed = mark().maximumInactive(200).build();
        int inactive = 0;
        int ticksUntilTimeout = 0;
        while (!MarkLimits.timedOut(timed, inactive)) {
            inactive++;
            ticksUntilTimeout++;
        }
        assertEquals(200, ticksUntilTimeout);
        assertFalse(MarkLimits.timedOut(mark().build(), 1_000_000), "no maximum by default");
    }

    @Test
    void activityNeedsBothLoadedInOneWorldAndWithinTheDistance() {
        MarkDefinition free = mark().build();
        assertTrue(MarkLimits.active(free, true, true, true, 1000.0 * 1000.0), "distance alone never pauses");
        assertFalse(MarkLimits.active(free, true, false, false, 0), "target unloaded");
        assertFalse(MarkLimits.active(free, false, true, false, 0), "source unloaded");
        assertFalse(MarkLimits.active(free, true, true, false, 0), "different dimensions");
        MarkDefinition near = mark().maximumDistance(32).build();
        assertFalse(MarkLimits.active(near, true, true, true, 40 * 40), "40 blocks: inactive");
        assertTrue(MarkLimits.active(near, true, true, true, 30 * 30), "30 blocks: active");
        assertTrue(MarkLimits.active(near, true, true, true, 32 * 32), "exactly at the maximum: active");
    }

    @Test
    void deathEndsMarksUnlessAPlayerSideKeepsThem() {
        MarkDefinition plain = mark().build();
        assertFalse(MarkLimits.survivesDeath(plain, true, true));
        assertFalse(MarkLimits.survivesDeath(plain, false, true));
        MarkDefinition keepSource = mark().keepOnSourceDeath(true).build();
        assertTrue(MarkLimits.survivesDeath(keepSource, true, true));
        assertFalse(MarkLimits.survivesDeath(keepSource, false, true), "the target side is not kept");
        assertFalse(MarkLimits.survivesDeath(keepSource, true, false), "a mob's death always ends");
        assertTrue(MarkLimits.survivesDeath(mark().keepOnTargetDeath(true).build(), false, true));
    }
}
