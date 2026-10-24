package com.mahghuuuls.statusworks.core.state;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;

/**
 * The entity part of checking an operation or query, shared by every kind: logical server, living entity, not a
 * fake player, has tracked state, and alive when the call changes state.
 */
final class EntityTargets {

    /** Either the living entity with its state, or why there is none. */
    static final class Checked {
        @Nullable final EntityLivingBase entity;
        @Nullable final EntityState state;
        @Nullable final String failure;

        private Checked(@Nullable EntityLivingBase entity, @Nullable EntityState state, @Nullable String failure) {
            this.entity = entity;
            this.state = state;
            this.failure = failure;
        }

        static Checked of(EntityLivingBase entity, EntityState state) {
            return new Checked(entity, state, null);
        }

        static Checked failed(String failure) {
            return new Checked(null, null, failure);
        }
    }

    private EntityTargets() {
    }

    static Checked check(@Nullable Entity entity, boolean forChange) {
        if (entity == null) {
            return Checked.failed("entity is null");
        }
        if (entity.world == null || entity.world.isRemote) {
            return Checked.failed("tracked state is changed and read on the server only");
        }
        if (!(entity instanceof EntityLivingBase)) {
            return Checked.failed("entity " + entity.getName() + " is not a living entity");
        }
        if (entity instanceof FakePlayer) {
            return Checked.failed("entity " + entity.getName() + " is a fake player");
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        EntityState state = EntityState.CAPABILITY == null ? null : living.getCapability(EntityState.CAPABILITY, null);
        if (state == null) {
            return Checked.failed("entity " + entity.getName() + " cannot hold tracked state");
        }
        if (forChange && !living.isEntityAlive()) {
            return Checked.failed("entity " + entity.getName() + " is dead");
        }
        return Checked.of(living, state);
    }
}
