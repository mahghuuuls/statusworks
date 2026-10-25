package com.mahghuuuls.statusworks.core.mark;

import java.util.UUID;

/** One mark as one of its participants sees it, for display: the side it takes, the other one, time, activity. */
public final class MarkView {

    public final String id;
    /** Whether the viewer is the mark's source; otherwise its target. */
    public final boolean asSource;
    public final UUID other;
    public final String otherName;
    /** Remaining active ticks, or -1 for a mark without a duration. */
    public final int remaining;
    public final boolean active;

    public MarkView(String id, boolean asSource, UUID other, String otherName, int remaining, boolean active) {
        this.id = id;
        this.asSource = asSource;
        this.other = other;
        this.otherName = otherName;
        this.remaining = remaining;
        this.active = active;
    }
}
