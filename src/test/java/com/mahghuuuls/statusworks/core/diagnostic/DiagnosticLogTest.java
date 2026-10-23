package com.mahghuuuls.statusworks.core.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticLogTest {

    private final List<String> lines = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(10_000);

    private DiagnosticLog log(boolean on, int limit) {
        return new DiagnosticLog(lines::add, clock::get, on, limit);
    }

    private static final String[] KEYS = {"Zombie", "42", "uuid-1"};

    @Test
    void offByDefaultWritesNothing() {
        DiagnosticLog log = log(false, 20);
        log.change("a:s", KEYS, "Zombie#42", "status", "0", "1", "operation", null);
        assertTrue(lines.isEmpty());
    }

    @Test
    void lineCarriesEveryField() {
        DiagnosticLog log = log(true, 20);
        log.change("a:s", KEYS, "Zombie#42", "status", "1", "0", "timer", "expired");
        assertEquals(1, lines.size());
        assertEquals("[statusworks-diag] change entity=Zombie#42 kind=status def=a:s old=1 new=0 cause=timer reason=expired",
                lines.get(0));
    }

    @Test
    void filtersLimitToOneDefinitionOrEntity() {
        DiagnosticLog log = log(true, 20);
        log.configure(true, "a:s", null);
        log.change("a:other", KEYS, "Zombie#42", "status", "0", "1", "operation", null);
        log.change("a:s", KEYS, "Zombie#42", "status", "0", "1", "operation", null);
        log.configure(true, null, "43");
        log.change("a:s", KEYS, "Zombie#42", "status", "1", "2", "operation", null);
        log.configure(true, null, "zombie");
        log.change("a:s", KEYS, "Zombie#42", "status", "2", "3", "operation", null);
        assertEquals(2, lines.size());
    }

    @Test
    void capDropsAndSummarizesInTheNextSecond() {
        DiagnosticLog log = log(true, 20);
        for (int i = 0; i < 100; i++) {
            log.change("a:s", KEYS, "Zombie#42", "status", "0", "1", "operation", null);
        }
        assertEquals(20, lines.size());
        log.tick();
        assertEquals(20, lines.size(), "the second is not over yet");
        clock.addAndGet(1000);
        log.tick();
        assertEquals(21, lines.size(), "the summary comes when the second is over, without a further line");
        assertEquals("[statusworks-diag] dropped 80 lines over the limit of 20 per second", lines.get(20));
        log.change("a:s", KEYS, "Zombie#42", "status", "1", "2", "operation", null);
        assertEquals(22, lines.size());
    }

    @Test
    void timingsAverageAndHighest() {
        Timings t = new Timings();
        t.add(1000);
        t.endTick();
        t.add(3000);
        t.endTick();
        assertEquals(2.0, t.averageMicros(), 1e-9);
        assertEquals(3.0, t.highestMicros(), 1e-9);
        assertEquals(2, t.sampleCount());
    }

    @Test
    void warningsOncePerKey() {
        Warnings w = new Warnings(lines::add);
        for (int i = 0; i < 50; i++) {
            w.once("unknown:a:x", "unknown id a:x");
        }
        assertEquals(1, lines.size());
    }
}
