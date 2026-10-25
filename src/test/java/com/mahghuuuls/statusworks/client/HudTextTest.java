package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTextTest {

    private static HudDefinition row(byte kind, int limit, boolean count, boolean amount, boolean time, int decimals) {
        return new HudDefinition(0, "t:x", kind, "X", false, null, false, null, 0, null, count, amount, time, decimals,
                0, limit, "gradual");
    }

    private static HudEntry entry(int count, double amount, double capacity, int remaining, String other) {
        return new HudEntry("k", 0, other == null ? HudEntry.OWN : HudEntry.MARK_SOURCE, count, amount, capacity,
                remaining, true, other, Collections.<HudEntry.Group>emptyList());
    }

    @Test
    void timesReadInSecondsThenMinutesAndSeconds() {
        assertEquals("1:05", HudText.time(65 * 20));
        assertEquals("4", HudText.time(80));
        assertEquals("59", HudText.time(59 * 20));
        assertEquals("1:00", HudText.time(60 * 20));
        assertEquals("1", HudText.time(1), "a part of a second shows as a whole one");
        assertEquals("0", HudText.time(0));
        assertEquals("", HudText.time(-1), "no timer");
    }

    @Test
    void amountsUseTheirDecimals() {
        assertEquals("12.3/50", HudText.amount(12.3, 50, 1));
        assertEquals("12/50", HudText.amount(12, 50, 0));
        assertEquals("0.25/1.50", HudText.amount(0.25, 1.5, 2));
    }

    @Test
    void theFieldsOfAGradualStatusAndOfAPool() {
        HudDefinition gradual = row(HudDefinition.STATUS, 3, true, true, true, 0);
        assertEquals(Arrays.asList("3", "1:05"), HudText.fields(gradual, entry(3, 0, 0, 1300, null), 1300));
        HudDefinition single = row(HudDefinition.STATUS, 1, true, true, true, 0);
        assertEquals(Collections.singletonList("1:05"), HudText.fields(single, entry(1, 0, 0, 1300, null), 1300),
                "no count at limit 1");
        HudDefinition hiddenFields = row(HudDefinition.STATUS, 3, false, true, false, 0);
        assertTrue(HudText.fields(hiddenFields, entry(3, 0, 0, 1300, null), 1300).isEmpty());
        HudDefinition pool = row(HudDefinition.POOL, 1, true, true, true, 1);
        assertEquals(Collections.singletonList("12.3/50"), HudText.fields(pool, entry(0, 12.3, 50, -1, null), -1));
        HudDefinition mark = row(HudDefinition.MARK, 1, true, true, true, 0);
        assertEquals(Arrays.asList("Zombie", "15"), HudText.fields(mark, entry(0, 0, 0, 300, "Zombie"), 300));
        assertFalse(HudText.showsCount(row(HudDefinition.COOLDOWN, 5, true, true, true, 0)));
    }

    @Test
    void theClientCountsDownExceptAnInactiveMark() {
        HudEntry own = entry(1, 0, 0, 100, null);
        assertEquals(60, HudText.remainingNow(own, 100, 40));
        assertEquals(0, HudText.remainingNow(own, 100, 400));
        assertEquals(-1, HudText.remainingNow(own, -1, 40));
        HudEntry inactive = new HudEntry("m", 0, HudEntry.MARK_SOURCE, 0, 0, 0, 100, false, "Zombie",
                Collections.<HudEntry.Group>emptyList());
        assertEquals(100, HudText.remainingNow(inactive, 100, 40));
    }

    @Test
    void entriesOverTheMaximumShowAsPlusN() {
        assertEquals(4, HudText.shown(6, 4));
        assertEquals("+2", HudText.more(6, 4));
        assertEquals("", HudText.more(3, 4));
        assertEquals(3, HudText.shown(3, 4));
    }
}
