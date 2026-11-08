package com.mahghuuuls.statusworks.core.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinRuleTest {

    @Test
    void aJoinThatWasNotCanceledAddsTheEntity() {
        assertTrue(JoinRule.added(false, false, false));
        assertTrue(JoinRule.added(false, true, false));
    }

    @Test
    void aCanceledJoinStillAddsAPlayerOrAForceSpawnedEntity() {
        assertTrue(JoinRule.added(true, true, false), "a player is added anyway");
        assertTrue(JoinRule.added(true, false, true), "a mob arriving through a portal is added anyway");
    }

    @Test
    void anyOtherCanceledJoinDoesNotAddTheEntity() {
        assertFalse(JoinRule.added(true, false, false));
    }
}
