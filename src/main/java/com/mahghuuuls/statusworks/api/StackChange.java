package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;

/**
 * The result of applying stacks: how many were added and how many were rejected at the limit, or why the call
 * failed (wrong side, invalid entity, unknown or disabled definition, bad argument).
 */
public final class StackChange {

    private final int added;
    private final int rejected;
    @Nullable private final String failure;

    private StackChange(int added, int rejected, @Nullable String failure) {
        this.added = added;
        this.rejected = rejected;
        this.failure = failure;
    }

    public static StackChange of(int added, int rejected) {
        return new StackChange(added, rejected, null);
    }

    public static StackChange failed(String failure) {
        return new StackChange(0, 0, failure);
    }

    public boolean succeeded() {
        return failure == null;
    }

    public int added() {
        return added;
    }

    public int rejected() {
        return rejected;
    }

    /** Why the call failed, or null when it succeeded. */
    @Nullable
    public String failure() {
        return failure;
    }

    @Override
    public String toString() {
        return failure == null ? "added=" + added + " rejected=" + rejected : "failed: " + failure;
    }
}
