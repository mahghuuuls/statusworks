package com.mahghuuuls.statusworks.api.callback;

import net.minecraft.entity.EntityLivingBase;

/** What the "ended" callback of a cooldown is told: the entity, the cooldown, and why it ended. */
public final class CooldownContext {

    private final EntityLivingBase entity;
    private final String definitionId;
    private final String reason;

    public CooldownContext(EntityLivingBase entity, String definitionId, String reason) {
        this.entity = entity;
        this.definitionId = definitionId;
        this.reason = reason;
    }

    public EntityLivingBase entity() {
        return entity;
    }

    public String definitionId() {
        return definitionId;
    }

    /** {@code expired}, {@code removed}, {@code death}, or {@code invalid}. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return definitionId + " reason=" + reason;
    }
}
