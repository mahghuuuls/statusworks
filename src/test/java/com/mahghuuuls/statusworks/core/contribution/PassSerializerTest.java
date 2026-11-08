package com.mahghuuuls.statusworks.core.contribution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
}
