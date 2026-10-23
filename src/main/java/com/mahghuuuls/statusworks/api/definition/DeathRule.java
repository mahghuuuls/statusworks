package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * What a player's death does to an instance. A mob's death always ends all its state.
 */
public enum DeathRule {
    /** The instance ends with reason {@code death}. */
    CLEAR("clear"),
    /** The instance stays, with its count and time, and is copied to the respawned player. */
    KEEP("keep");

    private final String text;

    DeathRule(String text) {
        this.text = text;
    }

    /** The text value scripts use for this rule. */
    public String text() {
        return text;
    }

    /** The rule for a text value, or null when the text names none. */
    @Nullable
    public static DeathRule fromText(@Nullable String text) {
        for (DeathRule rule : values()) {
            if (rule.text.equals(text)) {
                return rule;
            }
        }
        return null;
    }
}
