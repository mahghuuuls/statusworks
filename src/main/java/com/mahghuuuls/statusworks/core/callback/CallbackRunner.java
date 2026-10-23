package com.mahghuuuls.statusworks.core.callback;

import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;

import java.util.ArrayDeque;
import java.util.function.BiConsumer;

/**
 * Runs every author callback, and owns what happens when one misbehaves.
 *
 * <ul>
 *   <li>A callback that throws is stopped there; the caller and every other instance continue. Its first error is
 *   logged with the stack trace, later ones are only counted, and nothing ever goes to player chat.</li>
 *   <li>After {@value #DISABLE_AFTER} failures in a row the callback is disabled until restart, with one warning.
 *   A success ends a run of failures.</li>
 *   <li>Changes a callback makes take effect at once, but callbacks those changes trigger are queued and run after
 *   the current callback returns, so a callback never sees its instance change under it.</li>
 *   <li>The callbacks of one change run as one unit ({@link #batch(Runnable)}): callbacks that they trigger run
 *   after all of them, so every callback sees the points of one change in order.</li>
 *   <li>A chain of callbacks triggered by callbacks stops past depth {@value #MAX_DEPTH}, with one warning per
 *   callback, so a self-feeding callback cannot run away. The change that the stopped callback would have
 *   reported has already taken effect.</li>
 * </ul>
 *
 * <p>Used on the server thread only.</p>
 */
public final class CallbackRunner {

    public static final int DISABLE_AFTER = 10;
    public static final int MAX_DEPTH = 8;

    private static final class Pending {
        final String key;
        final Runnable call;
        final int depth;

        Pending(String key, Runnable call, int depth) {
            this.key = key;
            this.call = call;
            this.depth = depth;
        }
    }

    private final ErrorCounts counts;
    private final Warnings warnings;
    private final BiConsumer<String, Throwable> errorLog;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    /** Depth of the callback now running, or -1 when none runs. */
    private int depth = -1;
    /** Open {@link #batch(Runnable)} scopes outside any callback. */
    private int batches;

    /**
     * @param errorLog writes the first error of a callback with its stack trace
     */
    public CallbackRunner(ErrorCounts counts, Warnings warnings, BiConsumer<String, Throwable> errorLog) {
        this.counts = counts;
        this.warnings = warnings;
        this.errorLog = errorLog;
    }

    /** Whether a callback is running now; changes made inside one are logged with the cause "callback". */
    public boolean inCallback() {
        return depth >= 0;
    }

    /**
     * Collects the callbacks that {@code body} asks for and runs them, in order, after {@code body} returns.
     * Inside a callback this changes nothing, because callbacks are queued there anyway.
     */
    public void batch(Runnable body) {
        batches++;
        try {
            body.run();
        } finally {
            batches--;
        }
        if (batches == 0 && depth < 0) {
            drain();
        }
    }

    /**
     * Runs a callback now, or queues it when another callback is running or a batch is open.
     *
     * @param definitionId the definition the callback belongs to
     * @param point the callback point, for example {@code applied}
     */
    public void run(String definitionId, String point, Runnable call) {
        String key = definitionId + " " + point;
        if (depth >= 0) {
            int next = depth + 1;
            if (next > MAX_DEPTH) {
                warnings.once("depth:" + key, "Statusworks: callback " + key + " was stopped: a chain of callbacks"
                        + " triggered by callbacks went deeper than " + MAX_DEPTH);
                return;
            }
            queue.add(new Pending(key, call, next));
            return;
        }
        if (batches > 0) {
            queue.add(new Pending(key, call, 0));
            return;
        }
        execute(key, call, 0);
        drain();
    }

    private void drain() {
        while (!queue.isEmpty()) {
            Pending p = queue.poll();
            execute(p.key, p.call, p.depth);
        }
    }

    private void execute(String key, Runnable call, int at) {
        if (counts.isDisabled(key)) {
            return;
        }
        depth = at;
        try {
            call.run();
            counts.success(key);
        } catch (Throwable t) {
            ErrorCounts.Entry e = counts.failure(key);
            if (e.total() == 1) {
                errorLog.accept("Statusworks: callback " + key + " failed; later failures are counted, see"
                        + " /statusworks errors", t);
            }
            if (e.consecutive() >= DISABLE_AFTER && !e.disabled()) {
                counts.disable(key);
                warnings.once("disabled:" + key, "Statusworks: callback " + key + " failed " + DISABLE_AFTER
                        + " times in a row and is disabled until restart");
            }
        } finally {
            depth = -1;
        }
    }
}
