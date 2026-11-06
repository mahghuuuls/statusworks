package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudGroupsTest {

    private static HudDefinition row(byte kind, boolean harmful, boolean harmfulOnTarget) {
        return new HudDefinition(0, "t:x", kind, "X", false, null, false, null, 0, null, true, true, true, 0, 0, 1,
                "shared", null, -1, 0, false, harmful, harmfulOnTarget);
    }

    @Test
    void aPotionRowIsHarmfulExactlyWhenItsBadEffectFlagIsSet() {
        HudDefinition potion = row(HudDefinition.POTION, false, true);
        assertTrue(HudGroups.harmful(potion, HudEntry.OWN, true));
        assertFalse(HudGroups.harmful(potion, HudEntry.OWN, false));
        // A potion row's own values never decide it.
        assertFalse(HudGroups.harmful(row(HudDefinition.POTION, true, true), HudEntry.OWN, false));
    }

    @Test
    void aMarkUsesTheValueOfTheSideItIsSeenFrom() {
        HudDefinition hunt = row(HudDefinition.MARK, false, true);
        assertFalse(HudGroups.harmful(hunt, HudEntry.MARK_SOURCE, false));
        assertTrue(HudGroups.harmful(hunt, HudEntry.MARK_TARGET, false));
        HudDefinition heal = row(HudDefinition.MARK, false, false);
        assertFalse(HudGroups.harmful(heal, HudEntry.MARK_TARGET, false));
        HudDefinition curse = row(HudDefinition.MARK, true, false);
        assertTrue(HudGroups.harmful(curse, HudEntry.MARK_SOURCE, false));
        assertFalse(HudGroups.harmful(curse, HudEntry.MARK_TARGET, false));
    }

    /** A shown status entry of a definition with this value and this name. */
    private static ClientStore.Shown shown(String name, boolean harmful) {
        HudDefinition row = new HudDefinition(0, "t:" + name, HudDefinition.STATUS, name, false, null, false, null, 0,
                null, true, true, true, 0, 0, 1, "shared", null, -1, 0, false, harmful, true);
        HudEntry entry = new HudEntry(name, 0, HudEntry.OWN, 1, 0, 0, -1, true, null,
                Collections.<HudEntry.Group>emptyList());
        return new ClientStore.Shown(row, entry, -1, Collections.<Integer>emptyList());
    }

    private static List<String> names(List<ClientStore.Shown> shown) {
        List<String> out = new ArrayList<>();
        for (ClientStore.Shown s : shown) {
            out.add(s.row.name);
        }
        return out;
    }

    @Test
    void separatedTheBuffsComeFirstAndEachGroupKeepsTheOrderItWasGiven() {
        List<ClientStore.Shown> all = Arrays.asList(shown("a", false), shown("x", true), shown("b", false),
                shown("y", true));
        List<HudGroups.Group> groups = HudGroups.groups(all, 8, true);
        assertEquals(2, groups.size());
        assertEquals(Arrays.asList("a", "b"), names(groups.get(0).drawn));
        assertEquals(Arrays.asList("x", "y"), names(groups.get(1).drawn));
    }

    @Test
    void eachGroupHasItsOwnMaximumSoALongBuffListNeverHidesADebuff() {
        List<ClientStore.Shown> all = Arrays.asList(shown("a", false), shown("b", false), shown("c", false),
                shown("d", false), shown("x", true));
        List<HudGroups.Group> groups = HudGroups.groups(all, 2, true);
        assertEquals(Arrays.asList("a", "b"), names(groups.get(0).drawn));
        assertEquals(Arrays.asList("c", "d"), names(groups.get(0).rest));
        assertEquals("+2", groups.get(0).more);
        assertEquals(Arrays.asList("x"), names(groups.get(1).drawn));
        assertEquals("", groups.get(1).more);
    }

    @Test
    void notSeparatedEverythingIsOneGroupWithOneMaximum() {
        List<ClientStore.Shown> all = Arrays.asList(shown("x", true), shown("a", false), shown("b", false));
        List<HudGroups.Group> groups = HudGroups.groups(all, 2, false);
        assertEquals(1, groups.size());
        assertEquals(Arrays.asList("x", "a"), names(groups.get(0).drawn));
        assertEquals("+1", groups.get(0).more);
    }

    @Test
    void anEmptyGroupIsLeftOut() {
        assertEquals(1, HudGroups.groups(Arrays.asList(shown("x", true)), 8, true).size());
        assertTrue(HudGroups.groups(Collections.<ClientStore.Shown>emptyList(), 8, true).isEmpty());
        assertTrue(HudGroups.groups(Collections.<ClientStore.Shown>emptyList(), 8, false).isEmpty());
    }

    @Test
    void everyOtherEntryUsesItsDefinitionsValue() {
        for (byte kind : new byte[] {HudDefinition.STATUS, HudDefinition.POOL, HudDefinition.COOLDOWN}) {
            assertTrue(HudGroups.harmful(row(kind, true, false), HudEntry.OWN, false));
            assertFalse(HudGroups.harmful(row(kind, false, true), HudEntry.OWN, true));
        }
    }
}
