package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.StatusSnapshot;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * Takes status snapshots through the public API, as another mod would, and logs every row, so a campaign card can
 * compare them with {@code /statusworks inspect}. It can also keep one snapshot and log it again later (a kept
 * snapshot must not change), and hold one entity object to snapshot it after it died, unloaded or was replaced.
 */
final class SnapshotProbe {

    /** A snapshot kept by {@code /stwdev snapshot keep}, logged again by {@code kept}. */
    @Nullable static StatusSnapshot kept;
    @Nullable static String keptLabel;
    /** An entity object held by {@code /stwdev snapshot hold}, snapshotted again by {@code held}. */
    @Nullable static Entity held;

    private SnapshotProbe() {
    }

    /** The held object's state as Forge and Statusworks see it, for a card that reads it after a death or unload. */
    static String heldState(Entity entity) {
        String alive = entity instanceof EntityLivingBase ? " alive=" + ((EntityLivingBase) entity).isEntityAlive()
                + " health=" + ((EntityLivingBase) entity).getHealth() : "";
        return "addedToWorld=" + entity.isAddedToWorld() + alive;
    }

    /**
     * When a player starts tracking a test mob (a name starting with {@code stw}), takes its snapshot there: the
     * moment another mod would send it, while Forge is still adding the mob to its world.
     */
    static final class TrackingProbe {
        @SubscribeEvent
        public void onStartTracking(PlayerEvent.StartTracking event) {
            Entity target = event.getTarget();
            if (target.world.isRemote || !(target instanceof EntityLivingBase) || !target.hasCustomName()
                    || !target.getCustomNameTag().startsWith("stw")) {
                return;
            }
            DevTestMod.LOGGER.info("[stw-devtest] snapshot tracking player={} addedToWorld={}",
                    event.getEntityPlayer().getName(), target.isAddedToWorld());
            log("tracking", label(target), take(target));
        }
    }

    static String label(Entity entity) {
        return entity.getName() + "#" + entity.getEntityId() + " obj=" + GroupTestTypes.obj(entity);
    }

    static StatusSnapshot take(Entity entity) {
        return StatusworksApi.statuses().snapshot(entity);
    }

    /** Logs one line per row, or one line for an empty snapshot. */
    static void log(String what, String label, StatusSnapshot snapshot) {
        if (snapshot.isEmpty()) {
            DevTestMod.LOGGER.info("[stw-devtest] snapshot {} entity={} tick={} rows=0", what, label,
                    GroupTestTypes.tick());
            return;
        }
        int i = 0;
        for (StatusSnapshot.Entry e : snapshot.entries()) {
            StringBuilder groups = new StringBuilder("[");
            for (StatusSnapshot.Group g : e.groups()) {
                if (groups.length() > 1) {
                    groups.append(',');
                }
                groups.append(g.source()).append(':').append(g.count()).append('/').append(g.remainingTicks());
            }
            groups.append(']');
            DevTestMod.LOGGER.info("[stw-devtest] snapshot {} entity={} tick={} row={}/{} id={} name={} key={}"
                            + " item={}:{} texture={} count={} remaining={} harmful={} priority={} showCount={}"
                            + " showTime={} groups={}", what, label, GroupTestTypes.tick(), ++i,
                    snapshot.entries().size(), e.definitionId(), e.name(), e.nameIsKey(), e.iconItem(), e.iconMeta(), e.iconTexture(),
                    e.count(), e.remainingTicks(), e.harmful(), e.priority(), e.showCount(), e.showTime(), groups);
        }
    }
}
