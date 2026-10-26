package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.ParticleAudience;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudienceRulesTest {

    private static AudienceRules.Viewer near(double blocks) {
        return new AudienceRules.Viewer(false, true, blocks * blocks, false);
    }

    @Test
    void nearbyPlayersSeeWithin32BlocksAndLongDistanceReachesEveryTracker() {
        assertTrue(AudienceRules.sees(false, ParticleAudience.NEARBY, false, near(30)));
        assertTrue(AudienceRules.sees(false, ParticleAudience.NEARBY, false, near(32)));
        assertFalse(AudienceRules.sees(false, ParticleAudience.NEARBY, false, near(34)));
        assertTrue(AudienceRules.sees(false, ParticleAudience.NEARBY, true, near(70)), "long distance");
        AudienceRules.Viewer self = new AudienceRules.Viewer(true, true, 0, false);
        assertTrue(AudienceRules.sees(false, ParticleAudience.NEARBY, false, self), "the entity itself");
    }

    @Test
    void aPlayerWhoseClientDoesNotHaveTheEntitySeesNothing() {
        AudienceRules.Viewer notTracking = new AudienceRules.Viewer(false, false, 4, true);
        assertFalse(AudienceRules.sees(false, ParticleAudience.NEARBY, true, notTracking));
        assertFalse(AudienceRules.sees(true, ParticleAudience.SOURCE, false, notTracking), "a far source");
    }

    @Test
    void markAudiencesReachOnlyTheirParticipants() {
        AudienceRules.Viewer source = new AudienceRules.Viewer(false, true, 20 * 20, true);
        AudienceRules.Viewer farSource = new AudienceRules.Viewer(false, true, 50 * 50, true);
        AudienceRules.Viewer target = new AudienceRules.Viewer(true, true, 0, false);
        AudienceRules.Viewer bystander = near(2);
        assertTrue(AudienceRules.sees(true, ParticleAudience.SOURCE, false, source));
        assertFalse(AudienceRules.sees(true, ParticleAudience.SOURCE, false, farSource), "the range applies too");
        assertTrue(AudienceRules.sees(true, ParticleAudience.SOURCE, true, farSource), "unless long distance");
        assertFalse(AudienceRules.sees(true, ParticleAudience.SOURCE, false, target));
        assertFalse(AudienceRules.sees(true, ParticleAudience.SOURCE, false, bystander), "a player next to the target");
        assertTrue(AudienceRules.sees(true, ParticleAudience.TARGET, false, target));
        assertFalse(AudienceRules.sees(true, ParticleAudience.TARGET, false, source));
        assertTrue(AudienceRules.sees(true, ParticleAudience.BOTH, false, source));
        assertTrue(AudienceRules.sees(true, ParticleAudience.BOTH, false, target));
        assertFalse(AudienceRules.sees(true, ParticleAudience.BOTH, false, bystander));
        assertTrue(AudienceRules.sees(true, ParticleAudience.NEARBY, false, bystander));
        assertFalse(AudienceRules.sees(false, ParticleAudience.SOURCE, false, source), "statuses have no source");
    }

    @Test
    void viewersAreCheckedOnTheEntitysOwnSlotAtMostEveryTwentyTicks() {
        assertEquals(100, AudienceRules.slotStart(100, 0));
        assertEquals(100, AudienceRules.slotStart(119, 0));
        assertEquals(120, AudienceRules.slotStart(120, 0));
        assertEquals(87, AudienceRules.slotStart(100, 7));
        assertEquals(107, AudienceRules.slotStart(107, 27), "id 27 shares slot 7");
        assertEquals(93, AudienceRules.slotStart(100, -7), "a negative id still has a slot");
        long from = AudienceRules.slotStart(95, 7);
        assertTrue(AudienceRules.current(from, 106));
        assertFalse(AudienceRules.current(from, 107), "due again on its next slot");
        for (int id = -40; id < 40; id++) {
            for (long t = 0; t < 60; t++) {
                long s = AudienceRules.slotStart(t, id);
                assertTrue(s <= t && t - s < AudienceRules.RECHECK_TICKS);
            }
        }
    }

    @Test
    void spectatorsAndInactiveMarksEmitNothing() {
        assertTrue(AudienceRules.emits(false, true));
        assertFalse(AudienceRules.emits(true, true));
        assertFalse(AudienceRules.emits(false, false));
    }
}
