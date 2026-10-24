package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * How a contribution's value follows its instance.
 */
public enum ValueForm {
    /** The value, while the instance exists. */
    FIXED("fixed"),
    /** The value times the status's total stack count (statuses only). */
    PER_STACK("per_stack"),
    /** The value times the pool amount (pools only). */
    PER_AMOUNT("per_amount"),
    /** The value times the pool fill fraction, from 0 to 1 (pools only). */
    PER_FILL("per_fill");

    private final String text;

    ValueForm(String text) {
        this.text = text;
    }

    /** The text value scripts use. */
    public String text() {
        return text;
    }

    /** The value for a text, or null when the text names none. */
    @Nullable
    public static ValueForm fromText(@Nullable String text) {
        for (ValueForm value : values()) {
            if (value.text.equals(text)) {
                return value;
            }
        }
        return null;
    }
}
