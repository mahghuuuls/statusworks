package com.mahghuuuls.statusworks.api.definition;

/**
 * Who sees a definition's particles. Statuses and pools always use {@link #NEARBY}. A mark's particles appear on its
 * target; a mark defaults to {@link #SOURCE}. A participant audience reaches a participant only if it is a player.
 */
public enum ParticleAudience {
    /**
     * Players near the entity (within 32 blocks, or as far as the game shows the entity with long distance), and
     * the entity itself if it is a player.
     */
    NEARBY("nearby"),
    /** A mark's source, if it is a player. */
    SOURCE("source"),
    /** A mark's target, if it is a player. */
    TARGET("target"),
    /** A mark's source and target, each if it is a player. */
    BOTH("both");

    private final String text;

    ParticleAudience(String text) {
        this.text = text;
    }

    /** The text value scripts use for this audience. */
    public String text() {
        return text;
    }

    /** The audience for a text value, or null when the text names none. */
    @javax.annotation.Nullable
    public static ParticleAudience fromText(@javax.annotation.Nullable String text) {
        for (ParticleAudience a : values()) {
            if (a.text.equals(text)) {
                return a;
            }
        }
        return null;
    }
}
