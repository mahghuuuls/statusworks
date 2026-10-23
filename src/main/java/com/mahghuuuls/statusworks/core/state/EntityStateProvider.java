package com.mahghuuuls.statusworks.core.state;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nullable;

/** Attaches one {@link EntityState} to a living entity and saves it with the entity. */
public final class EntityStateProvider implements ICapabilitySerializable<NBTTagCompound> {

    public static final ResourceLocation KEY = new ResourceLocation("statusworks", "entity_state");

    private final EntityState state = new EntityState();
    private final EntityStateCodec codec;

    public EntityStateProvider(EntityStateCodec codec) {
        this.codec = codec;
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
    }
}
