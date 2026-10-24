package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/** Which participant of a mark a mark contribution applies to. */
public enum MarkSide {
    /** The entity that made the mark. */
    SOURCE("source"),
    /** The entity that carries the mark. */
    TARGET("target");

    private final String text;

    MarkSide(String text) {
        this.text = text;
    }

    /** The text value scripts use for this side. */
    public String text() {
        return text;
    }

    /** The side for a text value, or null when the text names none. */
    @Nullable
    public static MarkSide fromText(@Nullable String text) {
        for (MarkSide side : values()) {
            if (side.text.equals(text)) {
                return side;
            }
        }
        return null;
    }
}
