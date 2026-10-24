package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * What marking a new target does when the source already has its target limit of marks of that definition.
 */
public enum Overflow {
    /** The source's oldest mark (by creation; refreshing does not change it) ends with reason {@code replaced}. */
    REPLACE_OLDEST("replace_oldest"),
    /** The new mark is not created. */
    REJECT("reject");

    private final String text;

    Overflow(String text) {
        this.text = text;
    }

    /** The text value scripts use for this rule. */
    public String text() {
        return text;
    }

    /** The rule for a text value, or null when the text names none. */
    @Nullable
    public static Overflow fromText(@Nullable String text) {
        for (Overflow rule : values()) {
            if (rule.text.equals(text)) {
                return rule;
            }
        }
        return null;
    }
}
