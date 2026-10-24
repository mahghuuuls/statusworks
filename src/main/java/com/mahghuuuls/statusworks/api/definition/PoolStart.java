package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * The amount a new pool instance starts with. It applies only when the instance is created.
 */
public enum PoolStart {
    /** Starts at 0. */
    EMPTY("empty"),
    /** Starts at its capacity. */
    FULL("full"),
    /** Starts at the definition's start amount, clamped to the range. */
    AMOUNT("amount");

    private final String text;

    PoolStart(String text) {
        this.text = text;
    }

    /** The text value scripts use. */
    public String text() {
        return text;
    }

    /** The value for a text, or null when the text names none. */
    @Nullable
    public static PoolStart fromText(@Nullable String text) {
        for (PoolStart value : values()) {
            if (value.text.equals(text)) {
                return value;
            }
        }
        return null;
    }
}
