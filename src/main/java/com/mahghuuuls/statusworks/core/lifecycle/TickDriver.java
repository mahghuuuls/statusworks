package com.mahghuuuls.statusworks.core.lifecycle;

import com.mahghuuuls.statusworks.core.contribution.ContributionManager;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Timings;
import com.mahghuuuls.statusworks.core.state.CooldownOperations;
import com.mahghuuuls.statusworks.core.state.EntityState;
import com.mahghuuuls.statusworks.core.state.PoolOperations;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The only place time advances, and the only owner of which entities are processed.
 *
 * <p>At the end of each server world tick, after the world has updated its entities, every tracked entity whose
 * {@code ticksExisted} changed since the last look gets one active tick. The world loop raises that counter only
 * when it actually updates an entity, so an entity that is loaded but frozen (far from players, or in a dimension
 * with no players) is skipped and its timers stay paused. A dead entity (for example a player on the death
 * screen) is skipped too, so kept statuses keep their time until the respawn. A different pause signal would
 * change only this class.</p>
 *
 * <p>Entities enter the set when state is first applied or when an entity with saved state joins a world, and
 * leave it when they leave their world (heard through a world listener this class installs and classified by
 * {@link RemovalClassifier}), when their state becomes empty, or when their world unloads.</p>
 */
public final class TickDriver {

    private final ActiveEntities active = new ActiveEntities();
    private final RemovalClassifier classifier = new RemovalClassifier();
    /** Server ticks since start, for the one-tick memory of the classifier. */
    private long serverTicks;
    private final StatusOperations statuses;
    private final PoolOperations pools;
    private final CooldownOperations cooldowns;
    private final ContributionManager contributions;
    private final Timings timings;
    private final DiagnosticLog diagnosticLog;

    public TickDriver(StatusOperations statuses, PoolOperations pools, CooldownOperations cooldowns,
                      ContributionManager contributions, Timings timings, DiagnosticLog diagnosticLog) {
        this.statuses = statuses;
        this.pools = pools;
        this.cooldowns = cooldowns;
        this.contributions = contributions;
        this.timings = timings;
        this.diagnosticLog = diagnosticLog;
    }

    /** Starts processing an entity that just received state; its current tick counter is the baseline. */
    public void track(EntityLivingBase entity) {
        if (entity.world != null && !entity.world.isRemote) {
            active.add(entity.world, entity, entity.ticksExisted);
        }
    }

    /** Every processed entity with its state, in every world; for one-off sweeps such as the server-start check. */
    public void forEachTracked(java.util.function.BiConsumer<EntityLivingBase, EntityState> action) {
        for (ActiveEntities.Tracked tracked : active.all()) {
            EntityState state = tracked.entity.getCapability(EntityState.CAPABILITY, null);
            if (state != null) {
                action.accept(tracked.entity, state);
            }
        }
    }

    /** Number of entities currently processed. */
    public int trackedCount() {
        return active.size();
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER) {
            return;
        }
        long start = System.nanoTime();
        for (ActiveEntities.Tracked tracked : active.snapshot(event.world)) {
            EntityLivingBase entity = tracked.entity;
            if (entity.world != event.world) {
                // Filed under a world the entity has left (state applied mid-transfer); follow the entity.
                boolean moved = active.moveToCurrentWorld(event.world, tracked);
                if (diagnosticLog.isEnabled()) {
                    diagnosticLog.note("track-move entity=" + StatusOperations.entityLabel(entity) + " from="
                            + dim(event.world) + " to=" + dim(entity.world) + " moved=" + moved);
                }
                continue;
            }
            if (!entity.isEntityAlive()) {
                continue;
            }
            int now = entity.ticksExisted;
            if (now == tracked.lastTicksExisted) {
                continue;
            }
            tracked.lastTicksExisted = now;
            EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
            if (state == null) {
                active.remove(entity);
                continue;
            }
            // Statuses, then pools, then cooldowns; each kind in its own registration order.
            statuses.tick(entity, state);
            pools.tick(entity, state);
            cooldowns.tick(entity, state);
            if (state.isEmpty()) {
                active.remove(entity);
            }
        }
        timings.add(System.nanoTime() - start);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            timings.endTick();
            diagnosticLog.tick();
            serverTicks++;
            classifier.forget(serverTicks);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTravel(EntityTravelToDimensionEvent event) {
        if (!event.getEntity().world.isRemote) {
            classifier.noteTravel(event.getEntity().getUniqueID(), serverTicks);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // The server saves and removes the player's whole vehicle stack with the player.
        Entity root = event.player.getLowestRidingEntity();
        classifier.noteLogout(root.getUniqueID(), serverTicks);
        for (Entity passenger : root.getRecursivePassengers()) {
            classifier.noteLogout(passenger.getUniqueID(), serverTicks);
        }
    }

    /**
     * A tracked entity left a world: it is no longer processed there, and the decision goes to the diagnostic log.
     * Only the reporting world's set changes, never the set of the world the entity is in now.
     */
    private void onRemoved(net.minecraft.world.World world, Entity entity) {
        if (!active.remove(world, entity)) {
            if (diagnosticLog.isEnabled() && entity instanceof EntityLivingBase) {
                EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
                if (state != null && !state.isEmpty()) {
                    diagnosticLog.note("removal-untracked entity=" + StatusOperations.entityLabel(entity) + " dim="
                            + dim(world) + " entityWorldDim=" + dim(entity.world));
                }
            }
            return;
        }
        boolean died = entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHealth() <= 0;
        RemovalClassifier.Removal removal = classifier.classify(entity.getUniqueID(), died, entity.isDead,
                serverTicks);
        if (diagnosticLog.isEnabled()) {
            diagnosticLog.note("removal entity=" + StatusOperations.entityLabel(entity) + " dim=" + dim(world)
                    + " kind=" + removal.text() + " ends=" + removal.ends());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase entity = (EntityLivingBase) event.getEntity();
        EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
        if (state == null) {
            return;
        }
        statuses.reportLoad(entity, state);
        contributions.onJoin(entity, state);
        if (!state.isEmpty()) {
            // The event's world, not entity.world: a player changing dimension joins the new world before
            // PlayerList.transferEntityToWorld sets its world field.
            active.add(event.getWorld(), entity, entity.ticksExisted);
            if (diagnosticLog.isEnabled()) {
                diagnosticLog.note("join entity=" + StatusOperations.entityLabel(entity) + " dim="
                        + dim(event.getWorld()) + " entityWorldDim=" + dim(entity.world));
            }
        }
    }

    private static int dim(net.minecraft.world.World world) {
        return world == null ? Integer.MIN_VALUE : world.provider.getDimension();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote) {
            event.getWorld().addEventListener(new WorldRemovalListener(event.getWorld(), this::onRemoved));
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            for (EntityLivingBase dropped : active.forgetWorld(event.getWorld())) {
                if (diagnosticLog.isEnabled()) {
                    diagnosticLog.note("world-unload dim=" + dim(event.getWorld()) + " dropped entity="
                            + StatusOperations.entityLabel(dropped) + " entityWorldDim=" + dim(dropped.world));
                }
            }
        }
    }
}
