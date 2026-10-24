package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;

/**
 * The result of a pool operation: the amount before and after, or why the call failed (wrong side, invalid
 * entity, unknown or disabled definition, bad argument, or no instance for an operation that needs one).
 */
public final class PoolChange {

    private final double before;
    private final double after;
    @Nullable private final String failure;

    private PoolChange(double before, double after, @Nullable String failure) {
        this.before = before;
        this.after = after;
        this.failure = failure;
    }

    public static PoolChange of(double before, double after) {
        return new PoolChange(before, after, null);
    }

    public static PoolChange failed(String failure) {
        return new PoolChange(0, 0, failure);
    }

    public boolean succeeded() {
        return failure == null;
    }

    public double before() {
        return before;
    }

    public double after() {
        return after;
    }

    /** How much the amount actually changed; 0 for a failed call. */
    public double delta() {
        return after - before;
    }

    /** Why the call failed, or null when it succeeded. */
    @Nullable
    public String failure() {
        return failure;
    }

    @Override
    public String toString() {
        return failure == null ? "before=" + before + " after=" + after + " delta=" + delta() : "failed: " + failure;
    }
}
