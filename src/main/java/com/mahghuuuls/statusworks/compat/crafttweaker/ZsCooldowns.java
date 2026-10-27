package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * Cooldowns in scripts: {@code Cooldowns.define(id)} and the operations and queries of the Java
 * {@code CooldownService}. Durations below 1 mean the definition's default. A cooldown carries no contributions: a
 * contribution given to its builder makes the definition invalid.
 */
@ZenRegister
@ZenClass("mods.statusworks.Cooldowns")
public final class ZsCooldowns {

    private ZsCooldowns() {
    }

    @ZenMethod
    public static ZsBuilders.Cooldown define(String id) {
        return new ZsBuilders.Cooldown(id);
    }

    @ZenMethod
    public static ZsResult start(IEntity entity, String id) {
        return ZsResult.of(StatusworksApi.cooldowns().start(CraftTweakerMC.getEntity(entity), id));
    }

    @ZenMethod
    public static ZsResult startFor(IEntity entity, String id, int ticks) {
        return ZsResult.of(StatusworksApi.cooldowns().start(CraftTweakerMC.getEntity(entity), id, ticks));
    }

    @ZenMethod
    public static ZsResult restart(IEntity entity, String id, int ticks) {
        return ZsResult.of(StatusworksApi.cooldowns().restart(CraftTweakerMC.getEntity(entity), id, ticks));
    }

    @ZenMethod
    public static ZsResult extend(IEntity entity, String id, int ticks) {
        return ZsResult.of(StatusworksApi.cooldowns().extend(CraftTweakerMC.getEntity(entity), id, ticks));
    }

    @ZenMethod
    public static ZsResult reduce(IEntity entity, String id, int ticks) {
        return ZsResult.of(StatusworksApi.cooldowns().reduce(CraftTweakerMC.getEntity(entity), id, ticks));
    }

    @ZenMethod
    public static ZsResult clear(IEntity entity, String id) {
        return ZsResult.of(StatusworksApi.cooldowns().clear(CraftTweakerMC.getEntity(entity), id));
    }

    /** Starts the cooldown with its default duration if it is not running; returns whether it started. */
    @ZenMethod
    public static boolean startIfReady(IEntity entity, String id) {
        return StatusworksApi.cooldowns().startIfReady(CraftTweakerMC.getEntity(entity), id);
    }

    @ZenMethod
    public static boolean startIfReadyFor(IEntity entity, String id, int ticks) {
        return StatusworksApi.cooldowns().startIfReady(CraftTweakerMC.getEntity(entity), id, ticks);
    }

    @ZenMethod
    public static boolean isRunning(IEntity entity, String id) {
        return StatusworksApi.cooldowns().isRunning(CraftTweakerMC.getEntity(entity), id);
    }

    /** Remaining ticks, or 0 when it is not running. */
    @ZenMethod
    public static int remaining(IEntity entity, String id) {
        return StatusworksApi.cooldowns().remaining(CraftTweakerMC.getEntity(entity), id);
    }
}
