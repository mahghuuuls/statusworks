package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.DeathRule;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.RefreshRule;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusRulesTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final UUID K = StatusRules.NO_SOURCE;

    private static StatusDefinition.Builder def() {
        return StatusDefinition.builder("t:s").limit(5).defaultDuration(100);
    }

    private static void ticks(StatusDefinition d, StatusState s, int n) {
        for (int i = 0; i < n; i++) {
            StatusRules.tick(d, s);
        }
    }

    private static StatusState saved(StatusDefinition as, int stacks, int duration, int ticked) {
        StatusState s = new StatusState();
        StatusRules.apply(as, s, K, stacks, duration);
        ticks(as, s, ticked);
        return s;
    }

    @Test
    void fitLeavesStateThatMatchesItsDefinitionAlone() {
        StatusDefinition d = def().build();
        StatusState s = saved(d, 3, 100, 10);
        assertTrue(!StatusRules.fit(d, s));
        assertEquals(3, s.total());
        assertEquals(90, StatusRules.remaining(d, s, null));
    }

    @Test
    void fitClampsACountAboveALoweredLimit() {
        StatusState s = saved(def().limit(10).build(), 8, 100, 0);
        StatusDefinition now = def().limit(5).build();
        assertTrue(StatusRules.fit(now, s));
        assertEquals(5, s.total());
    }

    @Test
    void fitFromSharedToIndependentGivesOneEntryAtTheOldDeadline() {
        StatusState s = saved(def().build(), 3, 100, 40);
        StatusDefinition now = def().expiration(ExpirationMode.INDEPENDENT).build();
        assertTrue(StatusRules.fit(now, s));
        assertEquals(java.util.Collections.singletonMap(60, 3), StatusRules.stackRemainders(s, K));
        ticks(now, s, 60);
        assertEquals(0, s.total());
    }

    @Test
    void fitFromIndependentToSharedTakesTheSoonestDeadline() {
        StatusDefinition was = def().expiration(ExpirationMode.INDEPENDENT).build();
        StatusState s = new StatusState();
        StatusRules.apply(was, s, K, 1, 30);
        StatusRules.apply(was, s, K, 2, 80);
        StatusDefinition now = def().build();
        assertTrue(StatusRules.fit(now, s));
        assertEquals(3, s.total());
        assertEquals(30, StatusRules.remaining(now, s, null));
        assertTrue(StatusRules.stackRemainders(s, K).isEmpty());
    }

    @Test
    void fitToNoneDropsTimersAndFromNoneGivesTheDefaultDuration() {
        StatusState s = saved(def().build(), 2, 100, 10);
        StatusDefinition none = def().expiration(ExpirationMode.NONE).build();
        assertTrue(StatusRules.fit(none, s));
        assertEquals(-1, StatusRules.remaining(none, s, null));
        ticks(none, s, 500);
        assertEquals(2, s.total());

        StatusDefinition gradual = def().expiration(ExpirationMode.GRADUAL).defaultDuration(50).build();
        assertTrue(StatusRules.fit(gradual, s));
        assertEquals(50, StatusRules.remaining(gradual, s, null));
        ticks(gradual, s, 50);
        assertEquals(1, s.total(), "the gradual timer works after the fit");
    }

    @Test
    void fitFromPerSourceToSharedMergesTheGroups() {
        StatusDefinition was = def().ownership(Ownership.PER_SOURCE).build();
        StatusState s = new StatusState();
        StatusRules.apply(was, s, A, 2, 40);
        StatusRules.apply(was, s, B, 2, 90);
        StatusRules.apply(was, s, K, 2, 70);
        StatusDefinition now = def().limit(5).build();
        assertTrue(StatusRules.fit(now, s));
        assertEquals(java.util.Collections.singleton(K), s.groups().keySet());
        assertEquals(5, s.total());
        assertEquals(40, StatusRules.remaining(now, s, null));
    }

    @Test
    void fitKeepsALongApplicationUnderAMaximum() {
        StatusDefinition d = def().refresh(RefreshRule.EXTEND).maximumRemaining(300).build();
        StatusState s = saved(d, 1, 500, 0);
        assertTrue(!StatusRules.fit(d, s), "the maximum caps extend refreshes only");
        assertEquals(500, StatusRules.remaining(d, s, null));
        StatusDefinition i = def().expiration(ExpirationMode.INDEPENDENT).maximumRemaining(300).build();
        StatusState indep = saved(i, 2, 500, 0);
        assertTrue(!StatusRules.fit(i, indep));
        assertEquals(500, StatusRules.remaining(i, indep, null));
    }

    @Test
    void deathRulesDecideWhatSurvivesAndWhatACloneCopies() {
        StatusDefinition clear = def().build();
        StatusDefinition keep = def().deathRule(DeathRule.KEEP).build();
        assertTrue(!StatusRules.survivesDeath(clear, true));
        assertTrue(StatusRules.survivesDeath(keep, true));
        assertTrue(!StatusRules.survivesDeath(keep, false), "a mob's death ends everything");
        assertTrue(!StatusRules.copiedOnClone(clear, true));
        assertTrue(StatusRules.copiedOnClone(keep, true));
        assertTrue(StatusRules.copiedOnClone(clear, false), "leaving the End copies everything");
        assertTrue(StatusRules.copiedOnClone(keep, false));
    }

    @Test
    void intervalIsDueAtActiveTicks20And40And60() {
        StatusDefinition d = def().defaultDuration(60).onInterval(20, c -> { }).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 0);
        StringBuilder due = new StringBuilder();
        for (int tick = 1; tick <= 60; tick++) {
            if (StatusRules.intervalDue(d, s)) {
                due.append(tick).append(' ');
            }
            StatusRules.tick(d, s);
        }
        assertEquals("20 40 60 ", due.toString());
        assertEquals(0, s.total());
    }

    @Test
    void noIntervalIsNeverDue() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 0);
        for (int tick = 1; tick <= 50; tick++) {
            assertTrue(!StatusRules.intervalDue(d, s));
            StatusRules.tick(d, s);
        }
    }

    @Test
    void limitOneMakesAPlainStatus() {
        StatusDefinition d = def().limit(1).build();
        StatusState s = new StatusState();
        StatusRules.Applied r = StatusRules.apply(d, s, K, 3, 0);
        assertEquals(1, r.added);
        assertEquals(2, r.rejected);
        assertEquals(1, s.total());
    }

    @Test
    void defaultAndGivenDurations() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 0);
        assertEquals(100, StatusRules.remaining(d, s, null));
        StatusState s2 = new StatusState();
        StatusRules.apply(d, s2, K, 1, 40);
        assertEquals(40, StatusRules.remaining(d, s2, null));
    }

    @Test
    void sharedExpiresAllTogether() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 3, 100);
        ticks(d, s, 99);
        assertEquals(3, s.total());
        assertEquals(1, StatusRules.remaining(d, s, null));
        assertEquals(3, StatusRules.tick(d, s));
        assertEquals(0, s.total());
        assertTrue(s.groups().isEmpty());
    }

    @Test
    void gradualLosesOneStackPerExpiry() {
        StatusDefinition d = def().expiration(ExpirationMode.GRADUAL).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 3, 100);
        ticks(d, s, 100);
        assertEquals(2, s.total());
        assertEquals(100, StatusRules.remaining(d, s, null));
        ticks(d, s, 100);
        assertEquals(1, s.total());
        ticks(d, s, 100);
        assertEquals(0, s.total());
    }

    @Test
    void independentStacksKeepTheirOwnTimers() {
        StatusDefinition d = def().limit(10).expiration(ExpirationMode.INDEPENDENT).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 100);
        ticks(d, s, 50);
        StatusRules.apply(d, s, K, 1, 100);
        assertEquals(50, StatusRules.remaining(d, s, null));
        ticks(d, s, 50);
        assertEquals(1, s.total());
        ticks(d, s, 50);
        assertEquals(0, s.total());
    }

    @Test
    void independentApplicationDoesNotChangeExistingTimers() {
        StatusDefinition d = def().limit(10).expiration(ExpirationMode.INDEPENDENT).refresh(RefreshRule.RESET).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 2, 30);
        StatusRules.apply(d, s, K, 1, 500);
        ticks(d, s, 30);
        assertEquals(1, s.total());
    }

    @Test
    void noneNeverExpires() {
        StatusDefinition d = def().expiration(ExpirationMode.NONE).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 2, 10);
        ticks(d, s, 10000);
        assertEquals(2, s.total());
        assertEquals(-1, StatusRules.remaining(d, s, null));
    }

    @Test
    void resetSetsTheNewDuration() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 100);
        ticks(d, s, 90);
        StatusRules.apply(d, s, K, 1, 100);
        assertEquals(100, StatusRules.remaining(d, s, null));
    }

    @Test
    void extendAddsAndHonorsTheMaximum() {
        StatusDefinition d = def().refresh(RefreshRule.EXTEND).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 100);
        ticks(d, s, 70);
        StatusRules.apply(d, s, K, 1, 100);
        assertEquals(130, StatusRules.remaining(d, s, null));

        StatusDefinition capped = def().refresh(RefreshRule.EXTEND).maximumRemaining(120).build();
        StatusState c = new StatusState();
        StatusRules.apply(capped, c, K, 1, 100);
        ticks(capped, c, 70);
        StatusRules.apply(capped, c, K, 1, 100);
        assertEquals(120, StatusRules.remaining(capped, c, null));
    }

    @Test
    void preserveKeepsTheTimer() {
        StatusDefinition d = def().refresh(RefreshRule.PRESERVE).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 100);
        ticks(d, s, 70);
        StatusRules.apply(d, s, K, 1, 100);
        assertEquals(30, StatusRules.remaining(d, s, null));
        assertEquals(2, s.total());
    }

    @Test
    void partialFitAddsWhatFits() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 4, 0);
        StatusRules.Applied r = StatusRules.apply(d, s, K, 3, 0);
        assertEquals(1, r.added);
        assertEquals(2, r.rejected);
        assertEquals(5, s.total());
    }

    @Test
    void rejectedStacksRefreshByDefault() {
        StatusDefinition d = def().limit(2).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 2, 100);
        ticks(d, s, 90);
        StatusRules.apply(d, s, K, 1, 100);
        assertEquals(100, StatusRules.remaining(d, s, null));
    }

    @Test
    void rejectedStacksDoNotRefreshWhenTurnedOff() {
        StatusDefinition d = def().limit(2).refreshOnReject(false).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 2, 100);
        ticks(d, s, 90);
        StatusRules.apply(d, s, K, 1, 100);
        assertEquals(10, StatusRules.remaining(d, s, null));
    }

    @Test
    void rejectedIndependentStacksChangeNothing() {
        StatusDefinition d = def().limit(2).expiration(ExpirationMode.INDEPENDENT).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 2, 100);
        ticks(d, s, 90);
        StatusRules.Applied r = StatusRules.apply(d, s, K, 1, 100);
        assertEquals(0, r.added);
        assertEquals(10, StatusRules.remaining(d, s, null));
    }

    @Test
    void perSourceGroupsAreIndependent() {
        StatusDefinition d = def().limit(3).ownership(Ownership.PER_SOURCE).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, StatusRules.groupKey(d, A), 3, 100);
        StatusRules.apply(d, s, StatusRules.groupKey(d, B), 2, 40);
        StatusRules.apply(d, s, StatusRules.groupKey(d, null), 1, 100);
        assertEquals(3, StatusRules.count(s, A));
        assertEquals(2, StatusRules.count(s, B));
        assertEquals(1, StatusRules.count(s, K));
        assertEquals(6, s.total());
        assertEquals(40, StatusRules.remaining(d, s, null));
        assertEquals(100, StatusRules.remaining(d, s, A));
        ticks(d, s, 40);
        assertEquals(0, StatusRules.count(s, B));
        assertEquals(3, StatusRules.count(s, A));
    }

    @Test
    void sharedOwnershipIgnoresTheSource() {
        StatusDefinition d = def().build();
        assertEquals(K, StatusRules.groupKey(d, A));
    }

    @Test
    void removeTakesTheSoonestIndependentStacksFirst() {
        StatusDefinition d = def().limit(10).expiration(ExpirationMode.INDEPENDENT).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 20);
        StatusRules.apply(d, s, K, 1, 50);
        StatusRules.apply(d, s, K, 1, 90);
        assertEquals(2, StatusRules.remove(d, s, null, 2));
        assertEquals(1, s.total());
        assertEquals(90, StatusRules.remaining(d, s, null));
    }

    @Test
    void removeFromSharedKeepsTheTimer() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 3, 100);
        ticks(d, s, 30);
        StatusRules.remove(d, s, null, 1);
        assertEquals(2, s.total());
        assertEquals(70, StatusRules.remaining(d, s, null));
    }

    @Test
    void removeWithASourceTouchesOnlyThatGroup() {
        StatusDefinition d = def().ownership(Ownership.PER_SOURCE).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, A, 2, 100);
        StatusRules.apply(d, s, B, 2, 10);
        assertEquals(2, StatusRules.remove(d, s, A, 5));
        assertEquals(0, StatusRules.count(s, A));
        assertEquals(2, StatusRules.count(s, B));
    }

    @Test
    void removeWithoutASourceTakesTheSoonestGroupFirst() {
        StatusDefinition d = def().ownership(Ownership.PER_SOURCE).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, A, 2, 100);
        StatusRules.apply(d, s, B, 2, 10);
        StatusRules.remove(d, s, null, 3);
        assertEquals(1, StatusRules.count(s, A));
        assertEquals(0, StatusRules.count(s, B));
    }

    @Test
    void removeWithoutASourceTakesTheSoonestIndependentStacksAcrossGroups() {
        StatusDefinition d = def().limit(10).expiration(ExpirationMode.INDEPENDENT).ownership(Ownership.PER_SOURCE).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, A, 1, 20);
        StatusRules.apply(d, s, A, 1, 90);
        StatusRules.apply(d, s, B, 1, 50);
        assertEquals(2, StatusRules.remove(d, s, null, 2));
        assertEquals(1, s.total());
        assertEquals(1, StatusRules.count(s, A));
        assertEquals(0, StatusRules.count(s, B));
        assertEquals(90, StatusRules.remaining(d, s, null));
    }

    @Test
    void removeSurvivesAnIndependentGroupWithoutStackEntries() {
        // Saved data written under a shared definition and loaded under an independent one.
        StatusDefinition shared = def().build();
        StatusDefinition indep = def().expiration(ExpirationMode.INDEPENDENT).build();
        StatusState s = new StatusState();
        StatusRules.apply(shared, s, K, 3, 100);
        assertEquals(2, StatusRules.remove(indep, s, null, 2));
        assertEquals(1, s.total());
    }

    @Test
    void remainingNeverOverflows() {
        StatusDefinition d = def().refresh(RefreshRule.EXTEND).build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, Integer.MAX_VALUE);
        StatusRules.apply(d, s, K, 1, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, StatusRules.remaining(d, s, null));
    }

    @Test
    void noSourceKeyMatchesTheApiConstant() {
        assertEquals(com.mahghuuuls.statusworks.api.StatusService.NO_SOURCE, StatusRules.NO_SOURCE);
    }

    @Test
    void setCountClampsAndKeepsTimers() {
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.setCount(d, s, K, 9);
        assertEquals(5, s.total());
        assertEquals(100, StatusRules.remaining(d, s, null));
        ticks(d, s, 30);
        StatusRules.setCount(d, s, K, 2);
        assertEquals(2, s.total());
        assertEquals(70, StatusRules.remaining(d, s, null));
        StatusRules.setCount(d, s, K, 0);
        assertTrue(s.isEmpty());
    }

    @Test
    void ageAndRemainingMoveOnlyThroughTick() {
        // Arithmetic only: the pause itself (not calling tick for an entity the game did not update) lives in
        // TickDriver and is checked in game.
        StatusDefinition d = def().build();
        StatusState s = new StatusState();
        StatusRules.apply(d, s, K, 1, 100);
        ticks(d, s, 10);
        assertEquals(90, StatusRules.remaining(d, s, null));
        assertEquals(10, s.age());
    }
}
