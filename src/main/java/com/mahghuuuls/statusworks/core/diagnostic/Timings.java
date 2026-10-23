package com.mahghuuuls.statusworks.core.diagnostic;

/**
 * Statusworks' own server time per tick over the last 200 ticks. Work inside a tick adds nanoseconds; the end
 * of the server tick closes the sample. Reported in microseconds.
 */
public final class Timings {

    public static final int WINDOW = 200;

    private final long[] samples = new long[WINDOW];
    private int next;
    private int filled;
    private long current;

    /** Adds work done in the current tick. */
    public synchronized void add(long nanos) {
        current += nanos;
    }

    /** Closes the current tick's sample. */
    public synchronized void endTick() {
        samples[next] = current;
        next = (next + 1) % WINDOW;
        if (filled < WINDOW) {
            filled++;
        }
        current = 0;
    }

    public synchronized int sampleCount() {
        return filled;
    }

    /** Average microseconds per tick over the window, or 0 with no samples. */
    public synchronized double averageMicros() {
        if (filled == 0) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; i < filled; i++) {
            sum += samples[i];
        }
        return sum / (double) filled / 1000.0;
    }

    /** Highest microseconds of one tick over the window. */
    public synchronized double highestMicros() {
        long max = 0;
        for (int i = 0; i < filled; i++) {
            max = Math.max(max, samples[i]);
        }
        return max / 1000.0;
    }
}
