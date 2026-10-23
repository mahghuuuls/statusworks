package com.mahghuuuls.statusworks.core.lifecycle;

import com.mahghuuuls.statusworks.core.lifecycle.RemovalClassifier.Removal;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemovalClassifierTest {

    private static final UUID E = new UUID(3, 4);

    @Test
    void decisionTable() {
        // travelled, loggedOut, died, markedDead
        assertEquals(Removal.TRAVEL, RemovalClassifier.classify(true, false, false, true));
        assertEquals(Removal.TRAVEL, RemovalClassifier.classify(true, true, true, true));
        assertEquals(Removal.LOGOUT, RemovalClassifier.classify(false, true, false, true));
        assertEquals(Removal.LOGOUT, RemovalClassifier.classify(false, true, true, true),
                "a player who logs out on the death screen is not removed by death");
        assertEquals(Removal.DEATH, RemovalClassifier.classify(false, false, true, true));
        assertEquals(Removal.UNLOAD, RemovalClassifier.classify(false, false, false, false));
        assertEquals(Removal.GONE, RemovalClassifier.classify(false, false, false, true));
    }

    @Test
    void onlyDeathAndGoneEndState() {
        for (Removal r : Removal.values()) {
            assertEquals(r == Removal.DEATH || r == Removal.GONE, r.ends(), r.text());
        }
    }

    @Test
    void travelAndLogoutAreRememberedForOneTick() {
        RemovalClassifier c = new RemovalClassifier();
        c.noteTravel(E, 10);
        assertEquals(Removal.TRAVEL, c.classify(E, false, true, 10));
        assertEquals(Removal.TRAVEL, c.classify(E, false, true, 11));
        c.forget(12);
        assertEquals(Removal.GONE, c.classify(E, false, true, 12));

        c.noteLogout(E, 20);
        c.forget(21);
        assertEquals(Removal.LOGOUT, c.classify(E, false, true, 21));
        assertEquals(Removal.GONE, c.classify(E, false, true, 22), "an old signal no longer counts");
    }

    @Test
    void signalsBelongToOneEntity() {
        RemovalClassifier c = new RemovalClassifier();
        c.noteTravel(new UUID(9, 9), 5);
        assertFalse(c.classify(E, false, true, 5) == Removal.TRAVEL);
        assertTrue(c.classify(E, true, true, 5) == Removal.DEATH);
    }
}
