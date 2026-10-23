package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;

/**
 * The result of removing stacks, setting a count, or clearing a status: the total count before and after,
 * or why the call failed.
 */
public final class CountChange {

    private final int before;
    private final int after;
    @Nullable private final String failure;

    private CountChange(int before, int after, @Nullable String failure) {
        this.before = before;
        this.after = after;
        this.failure = failure;
    }

    public static CountChange of(int before, int after) {
        return new CountChange(before, after, null);
    }

    public static CountChange failed(String failure) {
        return new CountChange(0, 0, failure);
    }

    public boolean succeeded() {
        return failure == null;
    }

    public int before() {
        return before;
    }

    public int after() {
        return after;
    }

    /** Why the call failed, or null when it succeeded. */
    @Nullable
    public String failure() {
        return failure;
    }

    @Override
    public String toString() {
        return failure == null ? before + " -> " + after : "failed: " + failure;
    }
}
