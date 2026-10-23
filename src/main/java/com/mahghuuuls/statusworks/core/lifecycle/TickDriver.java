package com.mahghuuuls.statusworks.core.lifecycle;

import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Timings;
import com.mahghuuuls.statusworks.core.state.EntityState;
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
    private final WorldRemovalListener removalListener = new WorldRemovalListener(this::onRemoved);
    /** Server ticks since start, for the one-tick memory of the classifier. */
    private long serverTicks;
    private final StatusOperations statuses;
    private final Timings timings;
    private final DiagnosticLog diagnosticLog;

    public TickDriver(StatusOperations statuses, Timings timings, DiagnosticLog diagnosticLog) {
        this.statuses = statuses;
        this.timings = timings;
        this.diagnosticLog = diagnosticLog;
    }

    /** Starts processing an entity that just received state; its current tick counter is the baseline. */
    public void track(EntityLivingBase entity) {
        if (entity.world != null && !entity.world.isRemote) {
            active.add(entity, entity.ticksExisted);
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
            statuses.tick(entity, state);
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

    /** A tracked entity left its world: it is no longer processed, and the decision goes to the diagnostic log. */
    private void onRemoved(Entity entity) {
        if (!active.remove(entity)) {
            return;
        }
        boolean died = entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHealth() <= 0;
        RemovalClassifier.Removal removal = classifier.classify(entity.getUniqueID(), died, entity.isDead,
                serverTicks);
        if (diagnosticLog.isEnabled()) {
            diagnosticLog.note("removal entity=" + StatusOperations.entityLabel(entity) + " kind=" + removal.text()
                    + " ends=" + removal.ends());
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
        if (!state.isEmpty()) {
            track(entity);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote) {
            event.getWorld().addEventListener(removalListener);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            active.forgetWorld(event.getWorld());
        }
    }
}
