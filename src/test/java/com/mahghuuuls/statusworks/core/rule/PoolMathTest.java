package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.Direction;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolStart;
import com.mahghuuuls.statusworks.api.definition.ProgressionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolMathTest {

    private static final double EPS = 1e-9;

    private static PoolDefinition.Builder pool() {
        return PoolDefinition.builder("t:p").capacity(10);
    }

    /** Advances one tick at a time and reports whether the target was reached on the way. */
    private static boolean ticks(PoolDefinition d, PoolState s, int n) {
        boolean reached = false;
        for (int i = 0; i < n; i++) {
            reached |= PoolMath.advance(d, s, 1);
        }
        return reached;
    }

    @Test
    void startRulesApplyAtCreation() {
        assertEquals(0, PoolMath.create(pool().build()).amount(), EPS);
        assertEquals(20, PoolMath.create(pool().capacity(20).start(PoolStart.FULL).build()).amount(), EPS);
        assertEquals(4, PoolMath.create(pool().startAmount(4).build()).amount(), EPS);
        assertEquals(10, PoolMath.create(pool().startAmount(40).build()).amount(), EPS, "clamped to the capacity");
    }

    @Test
    void addSubtractAndSetStayInRange() {
        PoolState s = PoolMath.create(pool().startAmount(9.5).build());
        PoolMath.add(s, 2);
        assertEquals(10, s.amount(), EPS);
        PoolMath.add(s, -25);
        assertEquals(0, s.amount(), EPS);
        PoolMath.set(s, 7.25);
        assertEquals(7.25, s.amount(), EPS);
        PoolMath.set(s, Double.NaN);
        assertEquals(0, s.amount(), EPS);
    }

    @Test
    void loweringTheCapacityClampsAndRaisingItKeepsTheAmount() {
        PoolState s = PoolMath.create(pool().startAmount(8).build());
        PoolMath.setCapacity(s, 5);
        assertEquals(5, s.amount(), EPS);
        PoolMath.setCapacity(s, 20);
        assertEquals(5, s.amount(), EPS);
        assertEquals(20, s.capacity(), EPS);
    }

    @Test
    void fixedDrainsTwoPerSecond() {
        PoolDefinition d = pool().start(PoolStart.FULL).fixed(Direction.DRAIN, 2).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 20);
        assertEquals(8, s.amount(), EPS);
        ticks(d, s, 200);
        assertEquals(0, s.amount(), EPS, "stops at 0");
    }

    @Test
    void aSteadyRateArrivesOnItsExactTickDespiteFloatingLeftovers() {
        PoolDefinition drain = pool().start(PoolStart.FULL).fixed(Direction.DRAIN, 2).build();
        PoolState s = PoolMath.create(drain);
        ticks(drain, s, 99);
        assertTrue(s.amount() > 0);
        ticks(drain, s, 1);
        assertEquals(0.0, s.amount(), "exactly 0 on tick 100, not a leftover such as 1.9E-14");

        PoolDefinition fill = pool().fixed(Direction.FILL, 2).build();
        PoolState f = PoolMath.create(fill);
        ticks(fill, f, 99);
        assertTrue(f.amount() < 10);
        ticks(fill, f, 1);
        assertEquals(10.0, f.amount(), "exactly full on tick 100");

        assertEquals(0.0, PoolMath.clamp(1e-12, 10));
        assertEquals(10.0, PoolMath.clamp(10 - 1e-12, 10));
        assertEquals(1e-6, PoolMath.clamp(1e-6, 10), 0.0, "a real small amount is kept");

        PoolState full = PoolMath.create(pool().start(PoolStart.FULL).build());
        PoolMath.setCapacity(full, 10.000000005);
        assertEquals(10.0, full.amount(), 0.0, "raising the capacity never moves the amount, even by a snap");
    }

    @Test
    void percentOfCapacityFillsAShareOfTheCapacity() {
        PoolDefinition d = pool().capacity(50).percentOfCapacity(Direction.FILL, 10).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 20);
        assertEquals(5, s.amount(), EPS);
        ticks(d, s, 400);
        assertEquals(50, s.amount(), EPS, "stops at the capacity");
    }

    @Test
    void percentOfCurrentCompounds() {
        PoolDefinition d = pool().startAmount(8).percentOfCurrent(Direction.DRAIN, 50).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 40);
        assertEquals(2, s.amount(), 1e-6);
    }

    @Test
    void percentOfCurrentFillFromZeroStaysAtZero() {
        PoolDefinition d = pool().percentOfCurrent(Direction.FILL, 50).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 100);
        assertEquals(0, s.amount(), EPS);
    }

    @Test
    void theDefaultCutoffSnapsToZeroBelowATenthOfAPercent() {
        PoolDefinition d = pool().capacity(100).start(PoolStart.FULL).percentOfCurrent(Direction.DRAIN, 50).build();
        PoolState s = PoolMath.create(d);
        double last = s.amount();
        for (int i = 0; i < 20 * 20 && s.amount() > 0; i++) {
            last = s.amount();
            PoolMath.advance(d, s, 1);
        }
        assertEquals(0, s.amount(), EPS, "a pure compounding drain never reaches 0");
        assertTrue(last >= 0.1, "it was at or above the cutoff one tick before");
    }

    @Test
    void aCustomCutoffSnapsEarlier() {
        PoolDefinition d = pool().capacity(100).start(PoolStart.FULL).percentOfCurrent(Direction.DRAIN, 50)
                .cutoff(5).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 20 * 4);
        assertEquals(6.25, s.amount(), 1e-6);
        ticks(d, s, 20);
        assertEquals(0, s.amount(), EPS, "3.125 is below the cutoff of 5");
    }

    @Test
    void toTargetReachesTheTargetAtTheDeadline() {
        PoolDefinition d = pool().capacity(20).startAmount(10).toTarget(0, 100).build();
        PoolState s = PoolMath.create(d);
        assertFalse(ticks(d, s, 50));
        assertEquals(5, s.amount(), EPS);
        assertTrue(ticks(d, s, 50));
        assertEquals(0, s.amount(), EPS);
        assertEquals(ProgressionState.NONE, PoolMath.progressionState(d, s));
        ticks(d, s, 50);
        assertEquals(0, s.amount(), EPS);
    }

    @Test
    void toTargetKeepsItsDeadlineAfterAnOutsideChange() {
        PoolDefinition d = pool().capacity(20).startAmount(10).toTarget(0, 100).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 50);
        PoolMath.add(s, 5);
        assertEquals(10, s.amount(), EPS);
        ticks(d, s, 25);
        assertEquals(5, s.amount(), EPS, "half the remaining way in half the remaining time");
        assertTrue(ticks(d, s, 25));
        assertEquals(0, s.amount(), EPS);
    }

    @Test
    void resultsDoNotDependOnHowOftenTheInstanceIsUpdated() {
        PoolDefinition[] rules = {
                pool().start(PoolStart.FULL).fixed(Direction.DRAIN, 0.7).build(),
                pool().capacity(50).percentOfCapacity(Direction.FILL, 3).build(),
                pool().capacity(100).start(PoolStart.FULL).percentOfCurrent(Direction.DRAIN, 37).build(),
                pool().startAmount(1).percentOfCurrent(Direction.FILL, 25).build(),
                pool().capacity(20).startAmount(10).toTarget(3, 90).build(),
        };
        for (PoolDefinition d : rules) {
            PoolState everyTick = PoolMath.create(d);
            PoolState every20 = PoolMath.create(d);
            for (int i = 0; i < 100; i++) {
                PoolMath.advance(d, everyTick, 1);
            }
            for (int i = 0; i < 5; i++) {
                PoolMath.advance(d, every20, 20);
            }
            assertEquals(everyTick.amount(), every20.amount(), 0.001, d.progressionText());
        }
    }

    @Test
    void pausingStopsProgressionAndKeepsTheRemainingTime() {
        PoolDefinition fill = pool().fixed(Direction.FILL, 2).build();
        PoolState s = PoolMath.create(fill);
        ticks(fill, s, 20);
        PoolMath.setPaused(s, true);
        ticks(fill, s, 40);
        assertEquals(2, s.amount(), EPS);
        assertEquals(ProgressionState.PAUSED, PoolMath.progressionState(fill, s));
        PoolMath.setPaused(s, false);
        ticks(fill, s, 20);
        assertEquals(4, s.amount(), EPS);

        PoolDefinition target = pool().capacity(20).startAmount(10).toTarget(0, 100).build();
        PoolState t = PoolMath.create(target);
        ticks(target, t, 40);
        PoolMath.setPaused(t, true);
        ticks(target, t, 300);
        assertEquals(60, PoolMath.remainingToDeadline(target, t));
        PoolMath.setPaused(t, false);
        assertTrue(ticks(target, t, 60));
        assertEquals(0, t.amount(), EPS);
    }

    @Test
    void theLifetimeCountsActiveTicksAndIgnoresAmountChanges() {
        PoolDefinition d = pool().lifetime(100).build();
        PoolState s = PoolMath.create(d);
        ticks(d, s, 50);
        PoolMath.add(s, 3);
        ticks(d, s, 49);
        assertFalse(PoolMath.expired(s));
        ticks(d, s, 1);
        assertTrue(PoolMath.expired(s));
        PoolState paused = PoolMath.create(d);
        PoolMath.setPaused(paused, true);
        ticks(d, paused, 100);
        assertTrue(PoolMath.expired(paused), "pausing the progression does not pause the lifetime");
    }

    @Test
    void lifetimeOperationsSetAndExtend() {
        PoolDefinition d = pool().build();
        PoolState s = PoolMath.create(d);
        assertEquals(-1, PoolMath.remainingLifetime(s));
        PoolMath.extendLifetime(s, 30);
        assertEquals(30, PoolMath.remainingLifetime(s));
        ticks(d, s, 10);
        PoolMath.extendLifetime(s, 30);
        assertEquals(50, PoolMath.remainingLifetime(s));
        PoolMath.setLifetime(s, 5);
        assertEquals(5, PoolMath.remainingLifetime(s));
    }

    @Test
    void arrivalsAreDetectedOnceAndNeverWhileHeld() {
        assertTrue(PoolMath.becameFull(9, 10, 10, 10));
        assertFalse(PoolMath.becameFull(10, 10, 10, 10), "held full while more is added");
        assertTrue(PoolMath.becameFull(8, 10, 5, 5), "a lowered capacity can make it full");
        assertFalse(PoolMath.becameFull(10, 10, 10, 20), "a raised capacity makes it not full");
        assertTrue(PoolMath.becameEmpty(0.5, 0));
        assertFalse(PoolMath.becameEmpty(0, 0));
    }

    @Test
    void aDrainArrivesAtZeroOnlyOnce() {
        PoolDefinition d = pool().start(PoolStart.FULL).fixed(Direction.DRAIN, 2).build();
        PoolState s = PoolMath.create(d);
        int arrivals = 0;
        for (int i = 0; i < 200; i++) {
            double before = s.amount();
            PoolMath.advance(d, s, 1);
            if (PoolMath.becameEmpty(before, s.amount())) {
                arrivals++;
            }
        }
        assertEquals(1, arrivals);
    }

    @Test
    void fitTakesAChangedDefinitionCapacityUnlessAnOperationSetIt() {
        PoolDefinition lowered = pool().capacity(50).build();
        PoolState saved = new PoolState();
        saved.restore(80, 100, false, 0, -1, false, 0, -1, false);
        assertTrue(PoolMath.fit(lowered, saved));
        assertEquals(50, saved.capacity(), EPS);
        assertEquals(50, saved.amount(), EPS);
        assertFalse(PoolMath.fit(lowered, saved));

        PoolState operationSet = new PoolState();
        operationSet.restore(80, 100, true, 0, -1, false, 0, -1, false);
        assertFalse(PoolMath.fit(lowered, operationSet));
        assertEquals(100, operationSet.capacity(), EPS);
    }

    @Test
    void fitClampsAndAddsAMissingDeadline() {
        PoolDefinition plain = pool().build();
        PoolState s = new PoolState();
        s.restore(15, 10, false, 40, -1, false, 40, -1, false);
        assertTrue(PoolMath.fit(plain, s));
        assertEquals(10, s.amount(), EPS);
        assertFalse(PoolMath.fit(plain, s), "fits only once");

        PoolDefinition target = pool().capacity(20).toTarget(0, 100).build();
        PoolState t = new PoolState();
        t.restore(10, 20, false, 40, -1, false, 40, -1, false);
        assertTrue(PoolMath.fit(target, t));
        assertEquals(100, PoolMath.remainingToDeadline(target, t));

        PoolState bad = new PoolState();
        bad.restore(3, 0, false, 0, -1, false, 0, -1, false);
        assertTrue(PoolMath.fit(plain, bad));
        assertEquals(10, bad.capacity(), EPS);
    }
}
