package com.mahghuuuls.statusworks.core.lifecycle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The server-side set of loaded entities that carry tracked state, per world. Only these entities are ever
 * processed, so an entity without state costs nothing. {@link TickDriver} decides who is in the set and when
 * an entity counts as updated; this class only holds the entries.
 */
final class ActiveEntities {

    /** One tracked entity and the tick counter value {@link TickDriver} last saw. */
    static final class Tracked {
        final EntityLivingBase entity;
        int lastTicksExisted;

        Tracked(EntityLivingBase entity, int lastTicksExisted) {
            this.entity = entity;
            this.lastTicksExisted = lastTicksExisted;
        }
    }

    private final Map<World, LinkedHashMap<Integer, Tracked>> byWorld = new IdentityHashMap<>();

    /**
     * Adds the entity to a world's set unless this exact entity object is already in it. The world is passed in
     * because a player changing dimension joins the new world before its {@code world} field is updated.
     */
    void add(World in, EntityLivingBase entity, int ticksExisted) {
        LinkedHashMap<Integer, Tracked> world = byWorld.computeIfAbsent(in, w -> new LinkedHashMap<>());
        Tracked existing = world.get(entity.getEntityId());
        if (existing == null || existing.entity != entity) {
            world.put(entity.getEntityId(), new Tracked(entity, ticksExisted));
        }
    }

    /** Removes this exact entity object from the set of the world it is in; returns whether it was tracked. */
    boolean remove(Entity entity) {
        return remove(entity.world, entity);
    }

    /** Removes this exact entity object from one world's set; returns whether it was tracked there. */
    boolean remove(World in, Entity entity) {
        LinkedHashMap<Integer, Tracked> world = byWorld.get(in);
        if (world != null) {
            Tracked existing = world.get(entity.getEntityId());
            if (existing != null && existing.entity == entity) {
                world.remove(entity.getEntityId());
                return true;
            }
        }
        return false;
    }

    /**
     * Moves an entry filed under {@code from} to the world its entity is in now, keeping its tick baseline. Used
     * for an entry filed under a world the entity has since left.
     */
    boolean moveToCurrentWorld(World from, Tracked tracked) {
        boolean moved = false;
        if (remove(from, tracked.entity) && !tracked.entity.isDead) {
            moved = true;
            LinkedHashMap<Integer, Tracked> world = byWorld.computeIfAbsent(tracked.entity.world,
                    w -> new LinkedHashMap<>());
            Tracked existing = world.get(tracked.entity.getEntityId());
            if (existing == null || existing.entity != tracked.entity) {
                world.put(tracked.entity.getEntityId(), tracked);
            }
        }
        return moved;
    }

    /** Drops a world's set; returns the entities that were in it. */
    List<EntityLivingBase> forgetWorld(World world) {
        LinkedHashMap<Integer, Tracked> entries = byWorld.remove(world);
        List<EntityLivingBase> dropped = new ArrayList<>();
        if (entries != null) {
            for (Tracked t : entries.values()) {
                dropped.add(t.entity);
            }
        }
        return dropped;
    }

    /** A copy of the tracked entities of one world, safe to iterate while the set changes. */
    List<Tracked> snapshot(World world) {
        LinkedHashMap<Integer, Tracked> entries = byWorld.get(world);
        return entries == null ? new ArrayList<>() : new ArrayList<>(entries.values());
    }

    /** A copy of every tracked entity in every world. */
    List<Tracked> all() {
        List<Tracked> out = new ArrayList<>();
        for (LinkedHashMap<Integer, Tracked> world : byWorld.values()) {
            out.addAll(world.values());
        }
        return out;
    }

    int size() {
        int total = 0;
        for (LinkedHashMap<Integer, Tracked> world : byWorld.values()) {
            total += world.size();
        }
        return total;
    }
}
