package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.CooldownChange;
import com.mahghuuuls.statusworks.api.CountChange;
import com.mahghuuuls.statusworks.api.MarkResult;
import com.mahghuuuls.statusworks.api.PoolChange;
import com.mahghuuuls.statusworks.api.StackChange;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * The result of an operation in a script: whether it succeeded and why not, and the values the Java result has.
 * A field the operation does not have is 0 or the empty text.
 */
@ZenRegister
@ZenClass("mods.statusworks.Result")
public final class ZsResult {

    private final boolean succeeded;
    private final String failure;
    private final int added;
    private final int rejected;
    private final double before;
    private final double after;
    private final String outcome;
    private final String replacedTarget;

    private ZsResult(boolean succeeded, String failure, int added, int rejected, double before, double after,
                     String outcome, String replacedTarget) {
        this.succeeded = succeeded;
        this.failure = failure == null ? "" : failure;
        this.added = added;
        this.rejected = rejected;
        this.before = before;
        this.after = after;
        this.outcome = outcome;
        this.replacedTarget = replacedTarget;
    }

    static ZsResult of(StackChange c) {
        return new ZsResult(c.succeeded(), c.failure(), c.added(), c.rejected(), 0, 0, "", "");
    }

    static ZsResult of(CountChange c) {
        return new ZsResult(c.succeeded(), c.failure(), 0, 0, c.before(), c.after(), "", "");
    }

    static ZsResult of(PoolChange c) {
        return new ZsResult(c.succeeded(), c.failure(), 0, 0, c.before(), c.after(), "", "");
    }

    static ZsResult of(CooldownChange c) {
        return new ZsResult(c.succeeded(), c.failure(), 0, 0, c.before(), c.after(), "", "");
    }

    static ZsResult of(MarkResult r) {
        return new ZsResult(r.succeeded(), r.succeeded() ? "" : r.reason(), 0, 0, 0, 0,
                r.outcome().text(), ScriptValues.text(r.replacedTarget()));
    }

    static ZsResult failed(String failure) {
        return new ZsResult(false, failure, 0, 0, 0, 0, "", "");
    }

    @ZenGetter("succeeded")
    public boolean succeeded() {
        return succeeded;
    }

    /** Why the operation failed, or the empty text. */
    @ZenGetter("failure")
    public String failure() {
        return failure;
    }

    /** Stacks added by a status operation. */
    @ZenGetter("added")
    public int added() {
        return added;
    }

    /** Stacks that did not fit. */
    @ZenGetter("rejected")
    public int rejected() {
        return rejected;
    }

    /** The count, amount, or remaining ticks before the operation. */
    @ZenGetter("before")
    public double before() {
        return before;
    }

    /** The count, amount, or remaining ticks after the operation. */
    @ZenGetter("after")
    public double after() {
        return after;
    }

    /** For a mark: {@code created}, {@code refreshed}, {@code replaced}, or {@code rejected}. */
    @ZenGetter("outcome")
    public String outcome() {
        return outcome;
    }

    /** For a mark that replaced another target: that target's UUID, else the empty text. */
    @ZenGetter("replacedTarget")
    public String replacedTarget() {
        return replacedTarget;
    }

    @ZenMethod
    public String asString() {
        return succeeded ? "succeeded" : "failed: " + failure;
    }
}
