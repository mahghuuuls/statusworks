package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.core.contribution.ContributionManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nullable;

/**
 * Attaches one {@link EntityState} to a living entity and saves it with the entity. Loading reapplies the
 * contribution record right away, because vanilla reads the capability before attributes and health.
 */
public final class EntityStateProvider implements ICapabilitySerializable<NBTTagCompound> {

    public static final ResourceLocation KEY = new ResourceLocation("statusworks", "entity_state");

    private final EntityState state = new EntityState();
    private final EntityStateCodec codec;
    private final ContributionManager contributions;
    private final EntityLivingBase entity;

    public EntityStateProvider(EntityStateCodec codec, ContributionManager contributions, EntityLivingBase entity) {
        this.codec = codec;
        this.contributions = contributions;
        this.entity = entity;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == EntityState.CAPABILITY;
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == EntityState.CAPABILITY ? EntityState.CAPABILITY.cast(state) : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return codec.write(state);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        codec.read(state, nbt);
        contributions.restore(entity, state);
    }
}
