package com.mahghuuuls.statusworks.core.contribution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassSerializerTest {

    private final List<String> limits = new ArrayList<>();
    private final PassSerializer<String> passes = new PassSerializer<>(limits::add);

    @Test
    void aPassRequestedDuringAPassRunsAfterIt() {
        List<String> order = new ArrayList<>();
        boolean[] nested = {false};
        Runnable again = () -> order.add("second pass");
        passes.request("a", () -> {
            order.add("first pass start");
            if (!nested[0]) {
                nested[0] = true;
                passes.request("a", again, again);
            }
            order.add("first pass end");
        }, again);
        assertEquals(Arrays.asList("first pass start", "first pass end", "second pass"), order);
        assertTrue(limits.isEmpty());
        assertFalse(passes.isRunning("a"));
    }

    @Test
    void anotherEntityRunsAtOnceInsideAPass() {
        List<String> order = new ArrayList<>();
        passes.request("a", () -> {
            order.add("a start");
            passes.request("b", () -> order.add("b"), () -> order.add("b again"));
            order.add("a end");
        }, () -> order.add("a again"));
        assertEquals(Arrays.asList("a start", "b", "a end"), order);
    }

    @Test
    void passesDeferredDuringATickWaitForTheNextTickEvenWhenTheTickEndRunsDeferredPasses() {
        // A chain started before the tick's end (a world tick, a command) runs 8 passes; its remaining passes run at
        // the next tick's end, not at this tick's end.
        int[] runs = {0};
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            passes.request("a", loop[0], loop[0]);
        };
        passes.nextTick();
        passes.request("a", loop[0], loop[0]);
        assertEquals(8, runs[0]);
        passes.runDeferred(e -> true);
        assertEquals(8, runs[0], "the end of the same tick runs nothing deferred during it");
        assertTrue(passes.isDeferred("a"));
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertEquals(16, runs[0], "the next tick's end runs the next 8");
        passes.runDeferred(e -> true);
        assertEquals(16, runs[0], "passes deferred by the deferred chain wait for the tick after");
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertEquals(24, runs[0]);
    }

    @Test
    void theTickEndRunsOnlyWhatWasDeferredInAnEarlierTick() {
        int[] runs = {0, 0};
        Runnable[] loopA = new Runnable[1];
        Runnable[] loopB = new Runnable[1];
        loopA[0] = () -> {
            runs[0]++;
            passes.request("a", loopA[0], loopA[0]);
        };
        loopB[0] = () -> {
            runs[1]++;
            passes.request("b", loopB[0], loopB[0]);
        };
        passes.nextTick();
        passes.request("a", loopA[0], loopA[0]);
        passes.nextTick();
        passes.request("b", loopB[0], loopB[0]);
        passes.runDeferred(e -> true);
        assertEquals(16, runs[0], "a was deferred in the earlier tick and runs");
        assertEquals(8, runs[1], "b was deferred in this tick and waits");
        assertTrue(passes.isDeferred("b"));
    }

    @Test
    void eightPassesRunInARowThenTheNextWaitsForTheNextTick() {
        int[] runs = {0};
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            passes.request("a", loop[0], loop[0]);
        };
        passes.request("a", loop[0], loop[0]);
        assertEquals(PassSerializer.MAX_PASSES_IN_A_ROW, runs[0]);
        assertEquals(Arrays.asList("a"), limits);
        assertTrue(passes.isDeferred("a"));

        passes.nextTick();

        passes.runDeferred(e -> true);
        assertEquals(2 * PassSerializer.MAX_PASSES_IN_A_ROW, runs[0], "the next tick runs a new chain of 8");
        assertEquals(2, limits.size());
    }

    @Test
    void aDeferredPassIsDroppedForAnEntityThatIsGone() {
        int[] runs = {0};
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            passes.request("a", loop[0], loop[0]);
        };
        passes.request("a", loop[0], loop[0]);
        passes.nextTick();
        passes.runDeferred(e -> false);
        assertEquals(PassSerializer.MAX_PASSES_IN_A_ROW, runs[0]);
        assertFalse(passes.isDeferred("a"));
    }

    @Test
    void aNewRequestReplacesTheDeferredPass() {
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> passes.request("a", loop[0], loop[0]);
        passes.request("a", loop[0], loop[0]);
        assertTrue(passes.isDeferred("a"));
        int[] fresh = {0};
        passes.request("a", () -> fresh[0]++, () -> fresh[0]++);
        assertEquals(1, fresh[0]);
        assertFalse(passes.isDeferred("a"), "a pass that ran now covers the waiting one");
    }

    @Test
    void aKeptRequestRecordedDuringAPassRunsItsOwnUnitAfterIt() {
        List<String> order = new ArrayList<>();
        boolean[] joined = {false};
        passes.request("a", () -> {
            order.add("pass");
            if (!joined[0]) {
                joined[0] = true;
                passes.request("a", () -> order.add("join unit"), () -> order.add("plain"), true);
                passes.request("a", () -> order.add("other"), () -> order.add("plain"));
            }
        }, () -> order.add("plain"));
        assertEquals(Arrays.asList("pass", "join unit", "plain"), order,
                "the kept unit runs as itself, then the recorded plain pass");
    }

    @Test
    void aKeptUnitDeferredByTheLimitKeepsItsKindAndRunsOnce() {
        List<String> order = new ArrayList<>();
        int[] runs = {0};
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            if (runs[0] == PassSerializer.MAX_PASSES_IN_A_ROW) {
                passes.request("a", () -> order.add("join unit"), () -> order.add("plain"), true);
            } else if (runs[0] < PassSerializer.MAX_PASSES_IN_A_ROW) {
                passes.request("a", loop[0], loop[0]);
            }
        };
        passes.request("a", loop[0], loop[0]);
        assertTrue(passes.isDeferred("a"));
        assertTrue(order.isEmpty());
        // A new outside request before the tick runs now and still keeps the waiting join unit.
        passes.request("a", () -> order.add("new pass"), () -> order.add("plain"));
        assertEquals(Arrays.asList("new pass", "join unit"), order);
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertEquals(Arrays.asList("new pass", "join unit"), order, "the join unit ran once");
    }

    @Test
    void aKeptUnitDeferredByTheLimitRunsAsItselfAtTheNextTick() {
        List<String> order = new ArrayList<>();
        int[] runs = {0};
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            if (runs[0] == PassSerializer.MAX_PASSES_IN_A_ROW) {
                passes.request("a", () -> {
                    order.add("join unit");
                    passes.request("a", () -> order.add("unused"), () -> order.add("plain"));
                }, () -> order.add("plain"), true);
            } else if (runs[0] < PassSerializer.MAX_PASSES_IN_A_ROW) {
                passes.request("a", loop[0], loop[0]);
            }
        };
        passes.request("a", loop[0], loop[0]);
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertEquals(Arrays.asList("join unit", "plain"), order,
                "the deferred unit runs once as itself; a pass it requests is the plain pass");
    }

    @Test
    void keptUnitsRecordedInAFailingPassWaitForTheNextTick() {
        List<String> order = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> passes.request("a", () -> {
            passes.request("a", () -> order.add("join unit"), () -> order.add("plain"), true);
            throw new IllegalStateException("test");
        }, () -> order.add("plain")));
        assertTrue(passes.isDeferred("a"));
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertEquals(Arrays.asList("join unit"), order);
    }

    @Test
    void twoKeptUnitsRecordedInOnePassBothRun() {
        List<String> order = new ArrayList<>();
        boolean[] first = {true};
        passes.request("a", () -> {
            if (first[0]) {
                first[0] = false;
                passes.request("a", () -> order.add("join"), () -> order.add("plain"), true);
                passes.request("a", () -> order.add("retry"), () -> order.add("plain"), true);
            }
        }, () -> order.add("plain"));
        assertEquals(Arrays.asList("join", "retry"), order);
    }

    @Test
    void twoEqualButDistinctEntityObjectsAreDifferentEntities() {
        // Minecraft entities are equal by entity id, and a respawned player reuses the old id; the serializer must
        // still treat the old and the new object as two entities.
        String oldObject = new String("player");
        String newObject = new String("player");
        List<String> order = new ArrayList<>();
        passes.request(oldObject, () -> {
            order.add("old start");
            passes.request(newObject, () -> order.add("new"), () -> order.add("new again"));
            order.add("old end");
        }, () -> order.add("old again"));
        assertEquals(Arrays.asList("old start", "new", "old end"), order);
    }

    @Test
    void aFailingPassLeavesTheEntityFreeForTheNextRequest() {
        assertThrows(IllegalStateException.class, () -> passes.request("a", () -> {
            passes.request("a", () -> { }, () -> { });
            throw new IllegalStateException("test");
        }, () -> { }));
        assertFalse(passes.isRunning("a"));
        int[] runs = {0};
        passes.request("a", () -> runs[0]++, () -> runs[0]++);
        assertEquals(1, runs[0], "the request recorded inside the failed pass does not run later");
    }

    // Waiting work (REQ-208): what the group read asks the serializer.

    /** A chain on {@code serializer} for {@code entity} that runs {@code total} passes, each requesting the next. */
    private static Runnable chain(PassSerializer<String> serializer, String entity, int total, int[] runs,
                                  Runnable inEachPass) {
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            inEachPass.run();
            if (runs[0] < total) {
                serializer.request(entity, loop[0], loop[0]);
            }
        };
        return loop[0];
    }

    @Test
    void theRunningPassAloneIsNotWaitingWork() {
        List<Boolean> seen = new ArrayList<>();
        passes.request("a", () -> seen.add(passes.hasWaitingWork("a")), () -> { });
        assertEquals(Arrays.asList(false), seen, "the running pass and its empty bookkeeping are not waiting work");
        assertFalse(passes.hasWaitingWork("a"));
    }

    @Test
    void aPlainFollowUpIsWaitingWorkUntilItRuns() {
        List<Boolean> seen = new ArrayList<>();
        boolean[] first = {true};
        Runnable again = () -> seen.add(passes.hasWaitingWork("a"));
        passes.request("a", () -> {
            if (first[0]) {
                first[0] = false;
                passes.request("a", again, again);
                seen.add(passes.hasWaitingWork("a"));
            }
        }, again);
        assertEquals(Arrays.asList(true, false), seen, "waiting while recorded; not while it runs as the last pass");
        assertFalse(passes.hasWaitingWork("a"));
    }

    @Test
    void aKeptUnitIsWaitingWork() {
        List<Boolean> seen = new ArrayList<>();
        boolean[] first = {true};
        passes.request("a", () -> {
            if (first[0]) {
                first[0] = false;
                passes.request("a", () -> seen.add(passes.hasWaitingWork("a")), () -> { }, true);
                seen.add(passes.hasWaitingWork("a"));
            }
        }, () -> { });
        assertEquals(Arrays.asList(true, false), seen);
    }

    @Test
    void passesDeferredByTheLimitAreWaitingWorkUntilTheyRun() {
        int[] runs = {0};
        Runnable loop = chain(passes, "a", PassSerializer.MAX_PASSES_IN_A_ROW + 1, runs, () -> { });
        passes.request("a", loop, loop);
        assertTrue(passes.isDeferred("a"));
        assertTrue(passes.hasWaitingWork("a"), "the deferred 9th pass waits");
        assertFalse(passes.hasWaitingWork("b"), "other entities are not affected");
        passes.runDeferred(e -> true);
        assertTrue(passes.hasWaitingWork("a"), "still waiting at the end of the tick that deferred it");
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertEquals(PassSerializer.MAX_PASSES_IN_A_ROW + 1, runs[0]);
        assertFalse(passes.hasWaitingWork("a"), "nothing waits after the deferred pass ran");
    }

    @Test
    void keptUnitsLeftByAFailingPassAreWaitingWork() {
        assertThrows(IllegalStateException.class, () -> passes.request("a", () -> {
            passes.request("a", () -> { }, () -> { }, true);
            throw new IllegalStateException("test");
        }, () -> { }));
        assertTrue(passes.hasWaitingWork("a"));
        passes.nextTick();
        passes.runDeferred(e -> true);
        assertFalse(passes.hasWaitingWork("a"));
    }

    @Test
    void aWaitingKeptUnitMergedIntoANewRequestIsWaitingWorkDuringItsFirstPass() {
        int[] runs = {0};
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            runs[0]++;
            if (runs[0] == PassSerializer.MAX_PASSES_IN_A_ROW) {
                passes.request("a", () -> { }, () -> { }, true);
            } else {
                passes.request("a", loop[0], loop[0]);
            }
        };
        passes.request("a", loop[0], loop[0]);
        assertTrue(passes.hasWaitingWork("a"));
        List<Boolean> seen = new ArrayList<>();
        passes.request("a", () -> seen.add(passes.hasWaitingWork("a")), () -> { });
        assertEquals(Arrays.asList(true), seen, "the merged kept unit still has to run after this pass");
        assertFalse(passes.hasWaitingWork("a"));
    }

    @Test
    void aDueEntityIsWaitingWorkUntilItsTurnInTheDrain() {
        // Two entities deferred in the same tick are due in the same drain. During the first one's pass, the second
        // has not run yet: a read of its group must see waiting work (the 1.2.0 drain held it only in local lists).
        List<String> drained = new ArrayList<>();
        PassSerializer<String> serializer = new PassSerializer<>(e -> { }, drained::add);
        Map<String, Boolean> otherSeenWaiting = new LinkedHashMap<>();
        int[] runsA = {0};
        int[] runsB = {0};
        int total = PassSerializer.MAX_PASSES_IN_A_ROW + 1;
        Runnable loopA = chain(serializer, "a", total, runsA, () -> {
            if (runsA[0] == total) {
                otherSeenWaiting.put("a sees b", serializer.hasWaitingWork("b"));
                otherSeenWaiting.put("a sees a", serializer.hasWaitingWork("a"));
            }
        });
        Runnable loopB = chain(serializer, "b", total, runsB, () -> {
            if (runsB[0] == total) {
                otherSeenWaiting.put("b sees a", serializer.hasWaitingWork("a"));
                otherSeenWaiting.put("b sees b", serializer.hasWaitingWork("b"));
            }
        });
        serializer.request("a", loopA, loopA);
        serializer.request("b", loopB, loopB);
        serializer.nextTick();
        serializer.runDeferred(e -> true);
        assertEquals(2, drained.size(), "the drain listener is told once per entity that runs");
        String first = drained.get(0);
        String second = drained.get(1);
        assertEquals(true, otherSeenWaiting.get(first + " sees " + second), "the entity not yet run is waiting");
        assertEquals(false, otherSeenWaiting.get(second + " sees " + first), "the entity that ran has nothing waiting");
        assertEquals(false, otherSeenWaiting.get("a sees a"), "an entity's own drained pass is not waiting work");
        assertEquals(false, otherSeenWaiting.get("b sees b"), "an entity's own drained pass is not waiting work");
        assertFalse(serializer.hasWaitingWork("a"));
        assertFalse(serializer.hasWaitingWork("b"));
    }

    @Test
    void theDrainListenerIsToldOncePerEntityNotPerPass() {
        List<String> drained = new ArrayList<>();
        PassSerializer<String> serializer = new PassSerializer<>(e -> { }, drained::add);
        int[] runs = {0};
        Runnable loop = chain(serializer, "a", PassSerializer.MAX_PASSES_IN_A_ROW + 3, runs, () -> { });
        serializer.request("a", loop, loop);
        serializer.nextTick();
        serializer.runDeferred(e -> true);
        assertEquals(PassSerializer.MAX_PASSES_IN_A_ROW + 3, runs[0], "the drain ran three passes");
        assertEquals(Arrays.asList("a"), drained);
    }

    @Test
    void aDueEntityThatIsGoneIsDroppedAndNotWaiting() {
        List<String> drained = new ArrayList<>();
        PassSerializer<String> serializer = new PassSerializer<>(e -> { }, drained::add);
        int total = PassSerializer.MAX_PASSES_IN_A_ROW + 1;
        Runnable loopA = chain(serializer, "a", total, new int[1], () -> { });
        Runnable loopB = chain(serializer, "b", total, new int[1], () -> { });
        serializer.request("a", loopA, loopA);
        serializer.request("b", loopB, loopB);
        serializer.nextTick();
        serializer.runDeferred(e -> !e.equals("b"));
        assertEquals(Arrays.asList("a"), drained);
        assertFalse(serializer.hasWaitingWork("b"));
    }

    @Test
    void aSkippedDueEntityStopsWaitingAtItsTurnNotOnlyAfterTheDrain() {
        // Whichever entity has the first turn is skipped (gone); the other then runs and reads the skipped one, whose
        // turn has passed, so it must not be waiting any more (not only after the drain's final clear).
        PassSerializer<String> serializer = new PassSerializer<>(e -> { });
        List<String> turns = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        int total = PassSerializer.MAX_PASSES_IN_A_ROW + 1;
        int[] runsA = {0};
        int[] runsB = {0};
        Runnable loopA = chain(serializer, "a", total, runsA, () -> {
            if (runsA[0] == total) {
                seen.put("a", serializer.hasWaitingWork("b"));
            }
        });
        Runnable loopB = chain(serializer, "b", total, runsB, () -> {
            if (runsB[0] == total) {
                seen.put("b", serializer.hasWaitingWork("a"));
            }
        });
        serializer.request("a", loopA, loopA);
        serializer.request("b", loopB, loopB);
        serializer.nextTick();
        serializer.runDeferred(e -> {
            turns.add(e);
            return turns.size() > 1;
        });
        String skipped = turns.get(0);
        String ran = turns.get(1);
        assertEquals(Collections.singletonMap(ran, false), seen,
                "the entity that ran sees the skipped " + skipped + " as not waiting");
    }

    @Test
    void aFailingPassInADrainLeavesNoEntityWaitingForever() {
        PassSerializer<String> serializer = new PassSerializer<>(e -> { });
        int total = PassSerializer.MAX_PASSES_IN_A_ROW + 1;
        int[] runsA = {0};
        Runnable loopA = chain(serializer, "a", total, runsA, () -> {
            if (runsA[0] == total) {
                throw new IllegalStateException("test");
            }
        });
        int[] runsB = {0};
        Runnable loopB = chain(serializer, "b", total, runsB, () -> {
            if (runsB[0] == total) {
                throw new IllegalStateException("test");
            }
        });
        serializer.request("a", loopA, loopA);
        serializer.request("b", loopB, loopB);
        serializer.nextTick();
        assertThrows(IllegalStateException.class, () -> serializer.runDeferred(e -> true));
        assertFalse(serializer.hasWaitingWork("a"));
        assertFalse(serializer.hasWaitingWork("b"), "a due entity that had no turn is dropped, as in 1.2.0");
    }
}
