package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * What a new application does to a running timer.
 */
public enum RefreshRule {
    /** The timer is set to the new duration. */
    RESET("reset"),
    /** The new duration is added to the remaining time, up to the definition's maximum. */
    EXTEND("extend"),
    /** The timer does not change. */
    PRESERVE("preserve");

    private final String text;

    RefreshRule(String text) {
        this.text = text;
    }

    /** The text value scripts use for this rule. */
    public String text() {
        return text;
    }

    /** The rule for a text value, or null when the text names no rule. */
    @Nullable
    public static RefreshRule fromText(@Nullable String text) {
        for (RefreshRule rule : values()) {
            if (rule.text.equals(text)) {
                return rule;
            }
        }
        return null;
    }
}
