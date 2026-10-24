package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * How a pool's amount changes by itself over active time. A pool has at most one rule.
 */
public enum Progression {
    /** The amount changes only through operations. */
    NONE("none"),
    /** A fixed amount per second. */
    FIXED("fixed"),
    /** A percentage of the capacity per second. */
    PERCENT_OF_CAPACITY("percent_of_capacity"),
    /** A percentage of the current amount per second, compounding. */
    PERCENT_OF_CURRENT("percent_of_current"),
    /** Reaches a target amount by a deadline, at whatever rate that needs. */
    TO_TARGET("to_target");

    private final String text;

    Progression(String text) {
        this.text = text;
    }

    /** The text value scripts use. */
    public String text() {
        return text;
    }

    /** The value for a text, or null when the text names none. */
    @Nullable
    public static Progression fromText(@Nullable String text) {
        for (Progression value : values()) {
            if (value.text.equals(text)) {
                return value;
            }
        }
        return null;
    }
}
