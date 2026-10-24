package com.mahghuuuls.statusworks.api.callback;

import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a mark callback is told: the mark's definition, both participants by UUID, each participant entity when
 * it is loaded (null when not), and for "removed" the reason. A participant may be unloaded, for example when a
 * mark ends by timeout or when the other participant dies far away.
 */
public final class MarkContext {

    private final String definitionId;
    private final UUID sourceId;
    private final UUID targetId;
    @Nullable private final EntityLivingBase source;
    @Nullable private final EntityLivingBase target;
    @Nullable private final String reason;

    public MarkContext(String definitionId, UUID sourceId, UUID targetId, @Nullable EntityLivingBase source,
                       @Nullable EntityLivingBase target, @Nullable String reason) {
        this.definitionId = definitionId;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.source = source;
        this.target = target;
        this.reason = reason;
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

    /** The source entity when it is loaded, otherwise null. */
    @Nullable
    public EntityLivingBase source() {
        return source;
    }

    /** The target entity when it is loaded, otherwise null. */
    @Nullable
    public EntityLivingBase target() {
        return target;
    }

    /**
     * For "removed": {@code expired}, {@code removed}, {@code replaced}, {@code death}, {@code gone},
     * {@code inactive_timeout}, or {@code invalid}; otherwise null.
     */
    @Nullable
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return definitionId + " " + (source != null ? source.getName() : sourceId) + "->"
                + (target != null ? target.getName() : targetId) + (reason != null ? " reason=" + reason : "");
    }
}
