package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * How the stacks of a status expire.
 */
public enum ExpirationMode {
    /** One timer; every stack ends together when it runs out. */
    SHARED("shared"),
    /** One timer; each expiry removes one stack and restarts the timer while stacks remain. */
    GRADUAL("gradual"),
    /** Every application keeps its own timer. */
    INDEPENDENT("independent"),
    /** No timer; stacks stay until removed. */
    NONE("none");

    private final String text;

    ExpirationMode(String text) {
        this.text = text;
    }

    /** The text value scripts use for this mode. */
    public String text() {
        return text;
    }

    /** The mode for a text value, or null when the text names no mode. */
    @Nullable
    public static ExpirationMode fromText(@Nullable String text) {
        for (ExpirationMode mode : values()) {
            if (mode.text.equals(text)) {
                return mode;
            }
        }
        return null;
    }
}
