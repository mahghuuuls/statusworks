package com.mahghuuuls.statusworks.network;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryDiffTest {

    private static HudEntry own(String key, int count, int remaining) {
        return new HudEntry(key, 0, HudEntry.OWN, count, 0, 0, remaining, true, null,
                Collections.<HudEntry.Group>emptyList());
    }

    private static HudEntry pool(double amount) {
        return new HudEntry("p", 1, HudEntry.OWN, 0, amount, 10, -1, true, null,
                Collections.<HudEntry.Group>emptyList());
    }

    private static HudEntry mark(int remaining, boolean active) {
        return new HudEntry("m", 2, HudEntry.MARK_SOURCE, 0, 0, 0, remaining, active, "Zombie",
                Collections.<HudEntry.Group>emptyList());
    }

    private static Map<String, HudEntry> of(HudEntry... entries) {
        Map<String, HudEntry> m = new LinkedHashMap<>();
        for (HudEntry e : entries) {
            m.put(e.key, e);
        }
        return m;
    }

    @Test
    void anEntryThatDoesNotChangeIsSentOnce() {
        EntryDiff diff = new EntryDiff();
        assertEquals(1, diff.update(of(own("none", 1, -1)), 0).changed.size());
        for (long t = 1; t < 200; t++) {
            assertTrue(diff.update(of(own("none", 1, -1)), t).isEmpty(), "tick " + t);
        }
    }

    @Test
    void aRunningTimerIsNotSentAgainWhileTheClientCountdownMatches() {
        EntryDiff diff = new EntryDiff();
        diff.update(of(own("s", 2, 200)), 0);
        for (long t = 1; t <= 150; t++) {
            assertTrue(diff.update(of(own("s", 2, (int) (200 - t))), t).isEmpty(), "tick " + t);
        }
    }

    @Test
    void aRefreshACountChangeAndARemovalAreSent() {
        EntryDiff diff = new EntryDiff();
        diff.update(of(own("s", 2, 200)), 0);
        EntryDiff.Changes refresh = diff.update(of(own("s", 2, 200)), 50);
        assertEquals(1, refresh.changed.size(), "the timer was reset: 200 instead of the expected 150");
        assertEquals(1, diff.update(of(own("s", 3, 149)), 51).changed.size(), "a count change");
        EntryDiff.Changes gone = diff.update(of(), 52);
        assertEquals(Collections.singletonList("s"), gone.removed);
        assertTrue(diff.update(of(), 53).isEmpty());
    }

    @Test
    void aPoolIsSentOnlyWhenItsRoundedAmountChanges() {
        EntryDiff diff = new EntryDiff();
        diff.update(of(pool(5.0)), 0);
        assertTrue(diff.update(of(pool(5.0)), 1).isEmpty());
        assertEquals(1, diff.update(of(pool(5.1)), 2).changed.size());
    }

    @Test
    void anInactiveMarkStandsStillAndIsSentWhenItsActivityChanges() {
        EntryDiff diff = new EntryDiff();
        diff.update(of(mark(300, false)), 0);
        assertTrue(diff.update(of(mark(300, false)), 100).isEmpty(), "inactive: the client does not count down");
        assertEquals(1, diff.update(of(mark(300, true)), 101).changed.size());
        assertTrue(diff.update(of(mark(280, true)), 121).isEmpty());
    }

    @Test
    void clearingMakesTheNextUpdateAFullSnapshot() {
        EntryDiff diff = new EntryDiff();
        diff.update(of(own("a", 1, -1), own("b", 1, -1)), 0);
        diff.clear();
        assertEquals(2, diff.update(of(own("a", 1, -1), own("b", 1, -1)), 1).changed.size());
    }
}
