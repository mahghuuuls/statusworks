package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * How an attribute contribution combines with the attribute, as vanilla attribute modifiers do.
 */
public enum AttributeOperation {
    /** Adds the value to the base. */
    ADD("add"),
    /** Adds value times the base. */
    MULTIPLY_BASE("multiply_base"),
    /** Multiplies the total by one plus the value. */
    MULTIPLY_TOTAL("multiply_total");

    private final String text;

    AttributeOperation(String text) {
        this.text = text;
    }

    /** The text value scripts use. */
    public String text() {
        return text;
    }

    /** The value for a text, or null when the text names none. */
    @Nullable
    public static AttributeOperation fromText(@Nullable String text) {
        for (AttributeOperation value : values()) {
            if (value.text.equals(text)) {
                return value;
            }
        }
        return null;
    }
}
