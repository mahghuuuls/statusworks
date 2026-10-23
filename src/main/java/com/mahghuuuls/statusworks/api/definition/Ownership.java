package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * Whether a status keeps one count for all sources or one per source.
 */
public enum Ownership {
    /** One count, limit, and timer for every source together. */
    SHARED("shared"),
    /** A separate count, limit, and timers for each source; applications without a source form their own group. */
    PER_SOURCE("per_source");

    private final String text;

    Ownership(String text) {
        this.text = text;
    }

    /** The text value scripts use for this ownership. */
    public String text() {
        return text;
    }

    /** The ownership for a text value, or null when the text names none. */
    @Nullable
    public static Ownership fromText(@Nullable String text) {
        for (Ownership ownership : values()) {
            if (ownership.text.equals(text)) {
                return ownership;
            }
        }
        return null;
    }
}
