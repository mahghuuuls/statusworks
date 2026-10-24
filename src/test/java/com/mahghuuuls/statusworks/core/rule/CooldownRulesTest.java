package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.DeathRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownRulesTest {

    private static CooldownState runningWith(int left) {
        CooldownState s = CooldownRules.start(100);
        for (int i = 0; i < 100 - left; i++) {
            CooldownRules.tick(s);
        }
        return s;
    }

    @Test
    void aStartedCooldownRunsForItsDurationAndExpires() {
        CooldownState s = CooldownRules.start(30);
        assertTrue(CooldownRules.running(s));
        assertEquals(30, CooldownRules.remaining(s));
        for (int i = 0; i < 29; i++) {
            assertFalse(CooldownRules.tick(s));
        }
        assertTrue(CooldownRules.tick(s));
        assertFalse(CooldownRules.running(s));
        assertEquals(0, CooldownRules.remaining(s));
    }

    @Test
    void restartExtendAndReduceFollowTheExamples() {
        CooldownState s = runningWith(30);
        assertEquals(30, CooldownRules.remaining(s));
        CooldownRules.restart(s, 100);
        assertEquals(100, CooldownRules.remaining(s));

        CooldownState e = runningWith(30);
        CooldownRules.extend(e, 20);
        assertEquals(50, CooldownRules.remaining(e));

        CooldownState r = runningWith(30);
        assertTrue(CooldownRules.reduce(r, 40), "reducing past 0 ends it");
        assertFalse(CooldownRules.running(r));

        CooldownState partial = runningWith(30);
        assertFalse(CooldownRules.reduce(partial, 10));
        assertEquals(20, CooldownRules.remaining(partial));
    }

    @Test
    void startingARunningCooldownKeepsItsTime() {
        CooldownState running = runningWith(30);
        assertNull(CooldownRules.startIfReady(running, 100));
        assertEquals(30, CooldownRules.remaining(running));
    }

    @Test
    void startIfReadyStartsOnceThenRefuses() {
        CooldownState first = CooldownRules.startIfReady(null, 60);
        assertNotNull(first, "not running: it starts");
        assertEquals(60, CooldownRules.remaining(first));
        CooldownRules.tick(first);
        assertNull(CooldownRules.startIfReady(first, 60), "running: it refuses");
        assertEquals(59, CooldownRules.remaining(first), "and the time is unchanged");
        for (int i = 0; i < 59; i++) {
            CooldownRules.tick(first);
        }
        assertNotNull(CooldownRules.startIfReady(first, 60), "expired: it starts again");
    }

    @Test
    void restartSetsTheTimeOrStartsAStoppedCooldown() {
        CooldownState running = runningWith(30);
        assertSame(running, CooldownRules.restartOrStart(running, 100));
        assertEquals(100, CooldownRules.remaining(running));
        CooldownState fresh = CooldownRules.restartOrStart(null, 45);
        assertEquals(45, CooldownRules.remaining(fresh));
    }

    @Test
    void reducingToExactlyZeroEndsIt() {
        CooldownState s = runningWith(30);
        assertTrue(CooldownRules.reduce(s, 30));
    }

    @Test
    void aMissingCooldownIsNotRunning() {
        assertFalse(CooldownRules.running(null));
        assertEquals(0, CooldownRules.remaining(null));
    }

    @Test
    void aDurationBelowOneMeansTheDefault() {
        CooldownDefinition d = CooldownDefinition.builder("t:c").defaultDuration(70).build();
        assertEquals(70, CooldownRules.duration(d, 0));
        assertEquals(15, CooldownRules.duration(d, 15));
    }

    @Test
    void playersKeepCooldownsThroughDeathByDefault() {
        CooldownDefinition keep = CooldownDefinition.builder("t:c").build();
        CooldownDefinition clear = CooldownDefinition.builder("t:d").deathRule(DeathRule.CLEAR).build();
        assertEquals(DeathRule.KEEP, keep.deathRule());
        assertTrue(DeathRules.survivesDeath(keep.deathRule(), true));
        assertFalse(DeathRules.survivesDeath(keep.deathRule(), false), "a mob's death ends everything");
        assertFalse(DeathRules.survivesDeath(clear.deathRule(), true));
        assertTrue(DeathRules.copiedOnClone(keep.deathRule(), true));
        assertFalse(DeathRules.copiedOnClone(clear.deathRule(), true));
    }
}
