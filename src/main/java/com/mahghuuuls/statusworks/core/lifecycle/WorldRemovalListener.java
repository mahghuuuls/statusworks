package com.mahghuuuls.statusworks.core.lifecycle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Hears every removal of an entity from a server world (death, despawn, chunk unload, logout, dimension change)
 * and hands it to {@link TickDriver}, which classifies it with {@link RemovalClassifier}. 1.12 has no
 * entity-leave event; a world listener is the one hook that sees every removal path. Every other method of the
 * interface is deliberately empty.
 */
final class WorldRemovalListener implements IWorldEventListener {

    private final Consumer<Entity> removed;

    WorldRemovalListener(Consumer<Entity> removed) {
        this.removed = removed;
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        removed.accept(entity);
    }

    @Override
    public void onEntityAdded(Entity entity) {
    }

    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    @Override
    public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent sound, SoundCategory category,
                                         double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, boolean minimiseParticleLevel, double x, double y,
                              double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }
}
