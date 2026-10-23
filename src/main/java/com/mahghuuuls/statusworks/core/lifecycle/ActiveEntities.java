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

    /** Adds the entity unless this exact entity object is already tracked. */
    void add(EntityLivingBase entity, int ticksExisted) {
        LinkedHashMap<Integer, Tracked> world = byWorld.computeIfAbsent(entity.world, w -> new LinkedHashMap<>());
        Tracked existing = world.get(entity.getEntityId());
        if (existing == null || existing.entity != entity) {
            world.put(entity.getEntityId(), new Tracked(entity, ticksExisted));
        }
    }

    /** Removes this exact entity object; returns whether it was tracked. */
    boolean remove(Entity entity) {
        LinkedHashMap<Integer, Tracked> world = byWorld.get(entity.world);
        if (world != null) {
            Tracked existing = world.get(entity.getEntityId());
            if (existing != null && existing.entity == entity) {
                world.remove(entity.getEntityId());
                return true;
            }
        }
        return false;
    }

    void forgetWorld(World world) {
        byWorld.remove(world);
    }

    /** A copy of the tracked entities of one world, safe to iterate while the set changes. */
    List<Tracked> snapshot(World world) {
        LinkedHashMap<Integer, Tracked> entries = byWorld.get(world);
        return entries == null ? new ArrayList<>() : new ArrayList<>(entries.values());
    }

    int size() {
        int total = 0;
        for (LinkedHashMap<Integer, Tracked> world : byWorld.values()) {
            total += world.size();
        }
        return total;
    }
}
