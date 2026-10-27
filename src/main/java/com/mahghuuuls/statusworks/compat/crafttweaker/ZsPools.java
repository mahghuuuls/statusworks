package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.PoolSnapshot;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

/** Pools in scripts: {@code Pools.define(id)} and the operations and queries of the Java {@code PoolService}. */
@ZenRegister
@ZenClass("mods.statusworks.Pools")
public final class ZsPools {

    private ZsPools() {
    }

    @ZenMethod
    public static ZsBuilders.Pool define(String id) {
        return new ZsBuilders.Pool(id);
    }

    @ZenMethod
    public static ZsResult add(IEntity entity, String id, double amount) {
        return ZsResult.of(StatusworksApi.pools().add(CraftTweakerMC.getEntity(entity), id, amount));
    }

    @ZenMethod
    public static ZsResult subtract(IEntity entity, String id, double amount) {
        return ZsResult.of(StatusworksApi.pools().subtract(CraftTweakerMC.getEntity(entity), id, amount));
    }

    @ZenMethod
    public static ZsResult set(IEntity entity, String id, double amount) {
        return ZsResult.of(StatusworksApi.pools().set(CraftTweakerMC.getEntity(entity), id, amount));
    }

    @ZenMethod
    public static ZsResult setCapacity(IEntity entity, String id, double capacity) {
        return ZsResult.of(StatusworksApi.pools().setCapacity(CraftTweakerMC.getEntity(entity), id, capacity));
    }

    @ZenMethod
    public static ZsResult setLifetime(IEntity entity, String id, int ticks) {
        return ZsResult.of(StatusworksApi.pools().setLifetime(CraftTweakerMC.getEntity(entity), id, ticks));
    }

    @ZenMethod
    public static ZsResult extendLifetime(IEntity entity, String id, int ticks) {
        return ZsResult.of(StatusworksApi.pools().extendLifetime(CraftTweakerMC.getEntity(entity), id, ticks));
    }

    @ZenMethod
    public static ZsResult pause(IEntity entity, String id) {
        return ZsResult.of(StatusworksApi.pools().pause(CraftTweakerMC.getEntity(entity), id));
    }

    @ZenMethod
    public static ZsResult resume(IEntity entity, String id) {
        return ZsResult.of(StatusworksApi.pools().resume(CraftTweakerMC.getEntity(entity), id));
    }

    @ZenMethod
    public static ZsResult remove(IEntity entity, String id) {
        return ZsResult.of(StatusworksApi.pools().remove(CraftTweakerMC.getEntity(entity), id));
    }

    /** Whether the entity has the pool ({@code has} is a ZenScript keyword). */
    @ZenMethod
    public static boolean hasPool(IEntity entity, String id) {
        return StatusworksApi.pools().has(CraftTweakerMC.getEntity(entity), id);
    }

    /** The pool's state, or null when the entity has no such pool. */
    @ZenMethod
    public static Info get(IEntity entity, String id) {
        PoolSnapshot s = StatusworksApi.pools().get(CraftTweakerMC.getEntity(entity), id);
        return s == null ? null : new Info(s);
    }

    /** A pool's state at the moment it was read. */
    @ZenRegister
    @ZenClass("mods.statusworks.PoolInfo")
    public static final class Info {
        private final PoolSnapshot s;

        Info(PoolSnapshot s) {
            this.s = s;
        }

        @ZenGetter("amount")
        public double amount() {
            return s.amount();
        }

        @ZenGetter("capacity")
        public double capacity() {
            return s.capacity();
        }

        @ZenGetter("fill")
        public double fill() {
            return s.fill();
        }

        /** Remaining lifetime in ticks, or -1 for none. */
        @ZenGetter("remainingLifetime")
        public int remainingLifetime() {
            return s.remainingLifetime();
        }

        /** {@code none}, {@code active}, or {@code paused}. */
        @ZenGetter("progression")
        public String progression() {
            return s.progression().text();
        }

        @ZenGetter("target")
        public double target() {
            return s.target();
        }

        @ZenGetter("remainingToDeadline")
        public int remainingToDeadline() {
            return s.remainingToDeadline();
        }
    }
}
