package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;

/**
 * The result of a cooldown operation: the remaining active ticks before and after (0 when not running), or why
 * the call failed.
 */
public final class CooldownChange {

    private final int before;
    private final int after;
    @Nullable private final String failure;

    private CooldownChange(int before, int after, @Nullable String failure) {
        this.before = before;
        this.after = after;
        this.failure = failure;
    }

    public static CooldownChange of(int before, int after) {
        return new CooldownChange(before, after, null);
    }

    public static CooldownChange failed(String failure) {
        return new CooldownChange(0, 0, failure);
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

    @Nullable
    public String failure() {
        return failure;
    }

    @Override
    public String toString() {
        return failure == null ? "before=" + before + " after=" + after : "failed: " + failure;
    }
}
