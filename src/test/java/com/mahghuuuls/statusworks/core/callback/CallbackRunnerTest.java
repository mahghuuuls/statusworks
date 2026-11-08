package com.mahghuuuls.statusworks.core.callback;

import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackRunnerTest {

    private final ErrorCounts counts = new ErrorCounts();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final CallbackRunner runner =
            new CallbackRunner(counts, new Warnings(warnings::add), (message, t) -> errors.add(message));

    private static final Runnable THROWS = () -> {
        throw new IllegalStateException("test");
    };

    @Test
    void failureStaysInsideTheRunnerAndIsLoggedOnce() {
        for (int i = 0; i < 3; i++) {
            runner.run("a:s", "interval", THROWS);
        }
        assertEquals(1, errors.size());
        assertEquals(3, counts.snapshot().get(0).total());
        assertFalse(counts.isDisabled("a:s interval"));
    }

    @Test
    void tenFailuresInARowDisableWithOneWarning() {
        AtomicInteger calls = new AtomicInteger();
        Runnable call = () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("test");
        };
        for (int i = 0; i < 15; i++) {
            runner.run("a:s", "interval", call);
        }
        assertEquals(CallbackRunner.DISABLE_AFTER, calls.get());
        assertTrue(counts.isDisabled("a:s interval"));
        assertEquals(1, warnings.size());
        assertEquals(1, errors.size());
    }

    @Test
    void aSuccessEndsARunOfFailures() {
        for (int i = 0; i < 9; i++) {
            runner.run("a:s", "interval", THROWS);
        }
        runner.run("a:s", "interval", () -> { });
        for (int i = 0; i < 9; i++) {
            runner.run("a:s", "interval", THROWS);
        }
        assertFalse(counts.isDisabled("a:s interval"));
        assertEquals(18, counts.snapshot().get(0).total());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void countsAreKeptPerDefinitionAndPoint() {
        for (int i = 0; i < 10; i++) {
            runner.run("a:s", "interval", THROWS);
        }
        AtomicInteger other = new AtomicInteger();
        runner.run("a:s", "removed", other::incrementAndGet);
        runner.run("b:s", "interval", other::incrementAndGet);
        assertEquals(2, other.get());
    }

    @Test
    void nestedCallbacksRunAfterTheCurrentOneReturns() {
        List<String> order = new ArrayList<>();
        runner.run("a:s", "applied", () -> {
            order.add("outer start");
            runner.run("a:s", "quantity_changed", () -> order.add("inner"));
            order.add("outer end");
        });
        assertEquals(java.util.Arrays.asList("outer start", "outer end", "inner"), order);
    }

    @Test
    void queuedCallbacksKeepTheirOrder() {
        List<String> order = new ArrayList<>();
        runner.run("a:s", "applied", () -> {
            runner.run("a:s", "quantity_changed", () -> order.add("first"));
            runner.run("a:s", "removed", () -> order.add("second"));
        });
        assertEquals(java.util.Arrays.asList("first", "second"), order);
    }

    @Test
    void aSelfFeedingChainStopsAfterEightNestedRuns() {
        AtomicInteger runs = new AtomicInteger();
        Runnable[] feed = new Runnable[1];
        feed[0] = () -> {
            runs.incrementAndGet();
            runner.run("a:s", "quantity_changed", feed[0]);
        };
        runner.run("a:s", "quantity_changed", feed[0]);
        assertEquals(1 + CallbackRunner.MAX_DEPTH, runs.get());
        assertEquals(1, warnings.size());
        assertTrue(errors.isEmpty());
        assertFalse(runner.inCallback());
    }

    @Test
    void aFailingNestedCallbackDoesNotStopTheQueue() {
        List<String> order = new ArrayList<>();
        runner.run("a:s", "applied", () -> {
            runner.run("a:s", "quantity_changed", THROWS);
            runner.run("a:s", "removed", () -> order.add("after failure"));
        });
        assertEquals(java.util.Collections.singletonList("after failure"), order);
        assertEquals(1, errors.size());
    }

    @Test
    void theCallbacksOfOneChangeRunBeforeTheCallbacksTheyTrigger() {
        List<String> order = new ArrayList<>();
        runner.batch(() -> {
            runner.run("a:s", "applied", () -> {
                order.add("applied 0->2");
                runner.batch(() -> runner.run("a:s", "quantity_changed", () -> order.add("quantity 2->3")));
            });
            runner.run("a:s", "quantity_changed", () -> order.add("quantity 0->2"));
        });
        assertEquals(java.util.Arrays.asList("applied 0->2", "quantity 0->2", "quantity 2->3"), order);
        assertFalse(runner.inCallback());
    }

    @Test
    void aBatchInsideACallbackKeepsItsOrderAfterTheCurrentCallback() {
        List<String> order = new ArrayList<>();
        runner.run("a:s", "interval", () -> {
            runner.batch(() -> {
                runner.run("a:s", "quantity_changed", () -> order.add("quantity 1->0"));
                runner.run("a:s", "removed", () -> order.add("removed"));
            });
            order.add("interval end");
        });
        assertEquals(java.util.Arrays.asList("interval end", "quantity 1->0", "removed"), order);
    }

    @Test
    void aQuestionIsAnsweredNowAndAFailureGivesTheFallback() {
        assertTrue(runner.ask("type:t", "applies_to", () -> true, false));
        assertFalse(runner.ask("type:t", "applies_to", () -> {
            throw new IllegalStateException("test");
        }, false));
        assertEquals(1, errors.size());
        boolean[] askedInside = new boolean[1];
        runner.run("a:s", "applied", () -> askedInside[0] = runner.ask("type:t", "applies_to", () -> true, false));
        assertTrue(askedInside[0], "a question inside a callback is answered at once, not queued");
        assertFalse(runner.inCallback());
    }

    /** Runs {@code innermost} as a callback at depth {@code levels}: each level's callback triggers the next. */
    private void atDepth(int levels, Runnable innermost) {
        Runnable[] step = new Runnable[levels + 1];
        step[levels] = innermost;
        for (int i = levels - 1; i >= 0; i--) {
            Runnable next = step[i + 1];
            step[i] = () -> runner.run("a:s", "quantity_changed", next);
        }
        runner.run("a:s", "quantity_changed", step[0]);
    }

    @Test
    void aCallbackPastTheDepthLimitIsStoppedButANoticeThereStillRuns() {
        boolean[] callbackRan = new boolean[1];
        boolean[] noticeRan = new boolean[1];
        atDepth(CallbackRunner.MAX_DEPTH, () -> {
            runner.run("a:s", "removed", () -> callbackRan[0] = true);
            runner.deliver("type:test:power", "end", () -> noticeRan[0] = true);
        });
        assertFalse(callbackRan[0], "a callback chain deeper than the limit is stopped (REQ-094)");
        assertTrue(noticeRan[0], "an end notice is never stopped by the depth limit (REQ-087)");
        assertEquals(1, warnings.size());
        assertFalse(runner.inCallback());
    }

    @Test
    void aNoticeRunsAtOnceInsideACallbackAndInsideABatch() {
        List<String> order = new ArrayList<>();
        runner.run("a:s", "applied", () -> {
            runner.deliver("type:t", "value", () -> order.add("notice in callback"));
            order.add("callback end");
        });
        runner.batch(() -> {
            runner.deliver("type:t", "value", () -> order.add("notice in batch"));
            order.add("batch end");
        });
        assertEquals(java.util.Arrays.asList("notice in callback", "callback end", "notice in batch", "batch end"),
                order);
        assertFalse(runner.inCallback());
    }

    @Test
    void callbacksTriggeredByANoticeRunAfterItReturns() {
        List<String> order = new ArrayList<>();
        runner.deliver("type:t", "end", () -> {
            order.add("notice start");
            runner.run("a:s", "removed", () -> order.add("triggered callback"));
            order.add("notice end");
        });
        assertEquals(java.util.Arrays.asList("notice start", "notice end", "triggered callback"), order);
        assertFalse(runner.inCallback());
    }

    @Test
    void aCallbackRequestedAfterANoticeInsideACallbackIsStillDeferred() {
        List<String> order = new ArrayList<>();
        runner.run("a:s", "applied", () -> {
            runner.deliver("type:t", "value", () -> order.add("notice"));
            runner.run("a:s", "removed", () -> order.add("later callback"));
            order.add("callback end");
        });
        assertEquals(java.util.Arrays.asList("notice", "callback end", "later callback"), order);
    }

    @Test
    void aNoticeAtTheDepthLimitDoesNotRestartTheChain() {
        boolean[] ran = new boolean[1];
        atDepth(CallbackRunner.MAX_DEPTH, () -> runner.deliver("type:t", "value",
                () -> runner.run("a:s", "removed", () -> ran[0] = true)));
        assertFalse(ran[0], "a callback triggered by a notice at depth 8 would be depth 9 and is stopped");
        assertEquals(1, warnings.size());
    }

    @Test
    void aNoticeInsideABatchDoesNotRunTheBatchsCallbacksEarly() {
        List<String> order = new ArrayList<>();
        runner.batch(() -> {
            runner.run("a:s", "applied", () -> order.add("queued callback"));
            runner.deliver("type:t", "value", () -> order.add("notice"));
            order.add("batch end");
        });
        assertEquals(java.util.Arrays.asList("notice", "batch end", "queued callback"), order);
    }

    @Test
    void aNoticeThatFailsTenTimesInARowIsDisabledLikeACallback() {
        AtomicInteger calls = new AtomicInteger();
        Runnable call = () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("test");
        };
        for (int i = 0; i < 15; i++) {
            runner.deliver("type:t", "value", call);
        }
        assertEquals(CallbackRunner.DISABLE_AFTER, calls.get());
        assertTrue(counts.isDisabled("type:t value"));
        assertEquals(1, errors.size());
        assertEquals(1, warnings.size());
    }

    @Test
    void inCallbackIsTrueOnlyWhileACallbackRuns() {
        assertFalse(runner.inCallback());
        boolean[] inside = new boolean[1];
        runner.run("a:s", "applied", () -> inside[0] = runner.inCallback());
        assertTrue(inside[0]);
        assertFalse(runner.inCallback());
    }
}
