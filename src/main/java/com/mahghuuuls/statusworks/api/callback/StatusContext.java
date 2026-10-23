package com.mahghuuuls.statusworks.api.callback;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a status callback is told: the entity, the status, the total count before and after, the source of the
 * application when there is one, and the removal reason when the status ended.
 *
 * <p>The source is recorded only; it never decides damage or kill credit. A callback that deals damage chooses
 * its own attribution. The source is set for the "applied" and "quantity changed" callbacks of an application
 * that named one; it is null for removals, count changes, clears, intervals, and expiration.</p>
 *
 * <p>Removal reasons, as text: {@code expired}, {@code removed}, {@code replaced}, {@code death}, {@code gone},
 * {@code inactive_timeout}, {@code invalid}.</p>
 */
public final class StatusContext {

    private final EntityLivingBase entity;
    private final String definitionId;
    private final int countBefore;
    private final int countAfter;
    @Nullable private final UUID source;
    @Nullable private final String reason;

    public StatusContext(EntityLivingBase entity, String definitionId, int countBefore, int countAfter,
                         @Nullable UUID source, @Nullable String reason) {
        this.entity = entity;
        this.definitionId = definitionId;
        this.countBefore = countBefore;
        this.countAfter = countAfter;
        this.source = source;
        this.reason = reason;
    }

    public EntityLivingBase entity() {
        return entity;
    }

    public String definitionId() {
        return definitionId;
    }

    /** Total count before the change. */
    public int countBefore() {
        return countBefore;
    }

    /** Total count after the change; for an interval callback, the current count. */
    public int countAfter() {
        return countAfter;
    }

    /** The source entity's UUID for an application, or null. */
    @Nullable
    public UUID source() {
        return source;
    }

    /**
     * The source entity, when it has a source and that entity is loaded in the same world; otherwise null.
     */
    @Nullable
    public Entity sourceEntity() {
        if (source == null || !(entity.world instanceof WorldServer)) {
            return null;
        }
        return ((WorldServer) entity.world).getEntityFromUuid(source);
    }

    /** Why the status ended, for a removed callback; otherwise null. */
    @Nullable
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return definitionId + " " + countBefore + "->" + countAfter
                + (source != null ? " source=" + source : "") + (reason != null ? " reason=" + reason : "");
    }
}
