package com.mahghuuuls.statusworks.core.diagnostic;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The diagnostic log: one line per change of tracked state, off by default, switched at runtime, optionally
 * limited to one definition or one entity, and capped at a number of lines per second. Lines over the cap are
 * counted and reported in one summary line once the second is over: {@link #tick()}, called every server tick,
 * closes a finished second even when no further line arrives.
 */
public final class DiagnosticLog {

    public static final String PREFIX = "[statusworks-diag] ";

    private final Consumer<String> sink;
    private final LongSupplier millis;
    private volatile boolean enabled;
    private volatile int linesPerSecond;
    @Nullable private volatile String definitionFilter;
    @Nullable private volatile String entityFilter;
    private long windowStart;
    private int writtenInWindow;
    private int droppedInWindow;

    public DiagnosticLog(Consumer<String> sink, LongSupplier millis, boolean enabled, int linesPerSecond) {
        this.sink = sink;
        this.millis = millis;
        this.enabled = enabled;
        this.linesPerSecond = Math.max(1, linesPerSecond);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Turns the log on or off; filters are replaced, and null clears one. */
    public synchronized void configure(boolean on, @Nullable String definition, @Nullable String entity) {
        this.enabled = on;
        this.definitionFilter = definition;
        this.entityFilter = entity;
    }

    public void setLinesPerSecond(int lines) {
        this.linesPerSecond = Math.max(1, lines);
    }

    @Nullable
    public String definitionFilter() {
        return definitionFilter;
    }

    @Nullable
    public String entityFilter() {
        return entityFilter;
    }

    /**
     * Writes one change line when the log is on and the line passes the filters and the cap.
     *
     * @param entityKeys the entity's identities a filter may name (for example its name, numeric id, and UUID)
     */
    public synchronized void change(String definition, String[] entityKeys, String entityLabel, String kind,
                                    String oldValue, String newValue, String cause, @Nullable String reason) {
        if (!enabled) {
            return;
        }
        if (definitionFilter != null && !definitionFilter.equals(definition)) {
            return;
        }
        if (entityFilter != null && !matches(entityFilter, entityKeys)) {
            return;
        }
        StringBuilder line = new StringBuilder(PREFIX).append("change entity=").append(entityLabel)
                .append(" kind=").append(kind).append(" def=").append(definition)
                .append(" old=").append(oldValue).append(" new=").append(newValue)
                .append(" cause=").append(cause);
        if (reason != null) {
            line.append(" reason=").append(reason);
        }
        emit(line.toString());
    }

    /** Writes a free-form diagnostic line, subject to the switch and the cap but not to the filters. */
    public synchronized void note(String text) {
        if (enabled) {
            emit(PREFIX + text);
        }
    }

    private static boolean matches(String filter, String[] keys) {
        for (String key : keys) {
            if (filter.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /** Closes the current second when it is over, writing the summary of lines dropped in it. */
    public synchronized void tick() {
        closeWindowIfOver(millis.getAsLong());
    }

    private void closeWindowIfOver(long now) {
        if (now - windowStart >= 1000L) {
            if (droppedInWindow > 0) {
                sink.accept(PREFIX + "dropped " + droppedInWindow + " lines over the limit of "
                        + linesPerSecond + " per second");
            }
            windowStart = now;
            writtenInWindow = 0;
            droppedInWindow = 0;
        }
    }

    private void emit(String line) {
        closeWindowIfOver(millis.getAsLong());
        if (writtenInWindow < linesPerSecond) {
            writtenInWindow++;
            sink.accept(line);
        } else {
            droppedInWindow++;
        }
    }

    /** Reports dropped lines of the current second at once; used when the log is switched off. */
    public synchronized void flush() {
        if (droppedInWindow > 0) {
            sink.accept(PREFIX + "dropped " + droppedInWindow + " lines over the limit of "
                    + linesPerSecond + " per second");
            droppedInWindow = 0;
        }
    }
}
