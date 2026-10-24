package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * Whether a progression rule fills or drains a pool. A {@code to_target} rule takes its direction from the target.
 */
public enum Direction {
    /** The amount rises. */
    FILL("fill"),
    /** The amount falls. */
    DRAIN("drain");

    private final String text;

    Direction(String text) {
        this.text = text;
    }

    /** The text value scripts use. */
    public String text() {
        return text;
    }

    /** The value for a text, or null when the text names none. */
    @Nullable
    public static Direction fromText(@Nullable String text) {
        for (Direction value : values()) {
            if (value.text.equals(text)) {
                return value;
            }
        }
        return null;
    }
}
