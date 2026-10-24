package com.mahghuuuls.statusworks.api;

import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A read-only view of one mark, as a query sees it: both participants by UUID, the other participant's entity
 * when it is loaded (null otherwise), whether the mark is active, its remaining active ticks, and how long it has
 * been inactive.
 */
public final class MarkSnapshot {

    /** {@link #remaining()} of a mark without a duration. */
    public static final int NO_DURATION = -1;

    private final String definitionId;
    private final UUID sourceId;
    private final UUID targetId;
    @Nullable private final EntityLivingBase other;
    private final boolean active;
    private final int remaining;
    private final int inactiveTicks;

    public MarkSnapshot(String definitionId, UUID sourceId, UUID targetId, @Nullable EntityLivingBase other,
                        boolean active, int remaining, int inactiveTicks) {
        this.definitionId = definitionId;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.other = other;
        this.active = active;
        this.remaining = remaining;
        this.inactiveTicks = inactiveTicks;
    }

    public String definitionId() {
        return definitionId;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public UUID targetId() {
        return targetId;
    }

    /**
     * The participant on the other side of the query (the target for a source's query, the source for a
     * target's query) when it is loaded in any dimension; otherwise null. Nothing is loaded to find it.
     */
    @Nullable
    public EntityLivingBase other() {
        return other;
    }

    public boolean isActive() {
        return active;
    }

    /** Remaining active ticks, or {@link #NO_DURATION} for a mark that never expires. */
    public int remaining() {
        return remaining;
    }

    /** Server ticks since the mark became inactive, or 0 while it is active. */
    public int inactiveTicks() {
        return inactiveTicks;
    }

    @Override
    public String toString() {
        return definitionId + " " + sourceId + "->" + targetId + " active=" + active + " remaining="
                + (remaining == NO_DURATION ? "none" : Integer.toString(remaining)) + " inactive=" + inactiveTicks;
    }
}
