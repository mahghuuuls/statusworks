package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The result of a mark operation: {@code created}, {@code refreshed}, {@code replaced} (with the target that lost
 * its mark), or {@code rejected} (with why). A call that could not run (client side, an invalid entity, an unknown
 * or disabled definition) is also {@code rejected}, with its failure text.
 */
public final class MarkResult {

    /** What a mark operation did. */
    public enum Outcome {
        CREATED("created"),
        REFRESHED("refreshed"),
        REPLACED("replaced"),
        REJECTED("rejected");

        private final String text;

        Outcome(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    private final Outcome outcome;
    @Nullable private final UUID replacedTarget;
    @Nullable private final String reason;

    private MarkResult(Outcome outcome, @Nullable UUID replacedTarget, @Nullable String reason) {
        this.outcome = outcome;
        this.replacedTarget = replacedTarget;
        this.reason = reason;
    }

    public static MarkResult created() {
        return new MarkResult(Outcome.CREATED, null, null);
    }

    public static MarkResult refreshed() {
        return new MarkResult(Outcome.REFRESHED, null, null);
    }

    public static MarkResult replaced(UUID replacedTarget) {
        return new MarkResult(Outcome.REPLACED, replacedTarget, null);
    }

    public static MarkResult rejected(String reason) {
        return new MarkResult(Outcome.REJECTED, null, reason);
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Whether a mark exists on the target after the call. */
    public boolean succeeded() {
        return outcome != Outcome.REJECTED;
    }

    /** For {@code replaced}: the target whose mark ended to make room; otherwise null. */
    @Nullable
    public UUID replacedTarget() {
        return replacedTarget;
    }

    /** For {@code rejected}: why; otherwise null. */
    @Nullable
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return outcome.text() + (replacedTarget != null ? " replacedTarget=" + replacedTarget : "")
                + (reason != null ? " (" + reason + ")" : "");
    }
}
