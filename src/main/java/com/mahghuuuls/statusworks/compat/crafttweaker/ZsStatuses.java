package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.UUID;

/**
 * Statuses in scripts: {@code Statuses.define(id)} for a definition, and the operations and queries of the Java
 * {@code StatusService} with the entity first. A source is an entity when applying, and its UUID as text elsewhere
 * (the empty text for no source).
 */
@ZenRegister
@ZenClass("mods.statusworks.Statuses")
public final class ZsStatuses {

    private ZsStatuses() {
    }

    @ZenMethod
    public static ZsBuilders.Status define(String id) {
        return new ZsBuilders.Status(id);
    }

    /** Applies stacks with the default duration and no source. */
    @ZenMethod
    public static ZsResult add(IEntity entity, String id, int stacks) {
        return ZsResult.of(StatusworksApi.statuses().apply(CraftTweakerMC.getEntity(entity), id, stacks));
    }

    /** Applies stacks from a source entity (or null) for a duration in ticks (below 1: the default duration). */
    @ZenMethod
    public static ZsResult addFrom(IEntity entity, String id, int stacks, IEntity source, int ticks) {
        return ZsResult.of(StatusworksApi.statuses().apply(CraftTweakerMC.getEntity(entity), id, stacks,
                CraftTweakerMC.getEntity(source), ticks));
    }

    @ZenMethod
    public static ZsResult remove(IEntity entity, String id, int stacks) {
        return ZsResult.of(StatusworksApi.statuses().remove(CraftTweakerMC.getEntity(entity), id, stacks));
    }

    @ZenMethod
    public static ZsResult removeFrom(IEntity entity, String id, int stacks, String source) {
        UUID uuid = ScriptValues.uuid(source);
        if (uuid == null) {
            return ZsResult.failed(ScriptValues.notAUuid(source));
        }
        return ZsResult.of(StatusworksApi.statuses().remove(CraftTweakerMC.getEntity(entity), id, stacks, uuid));
    }

    @ZenMethod
    public static ZsResult setCount(IEntity entity, String id, int count, String source) {
        UUID uuid = ScriptValues.uuid(source);
        if (uuid == null) {
            return ZsResult.failed(ScriptValues.notAUuid(source));
        }
        return ZsResult.of(StatusworksApi.statuses().setCount(CraftTweakerMC.getEntity(entity), id, count, uuid));
    }

    @ZenMethod
    public static ZsResult clear(IEntity entity, String id) {
        return ZsResult.of(StatusworksApi.statuses().clear(CraftTweakerMC.getEntity(entity), id));
    }

    /** Whether the entity has the status ({@code has} is a ZenScript keyword, so the name says what it asks). */
    @ZenMethod
    public static boolean hasStatus(IEntity entity, String id) {
        return StatusworksApi.statuses().has(CraftTweakerMC.getEntity(entity), id);
    }

    @ZenMethod
    public static int count(IEntity entity, String id) {
        return StatusworksApi.statuses().count(CraftTweakerMC.getEntity(entity), id);
    }

    /** The stacks from one source (its UUID as text), or 0 for text that is not a UUID. */
    @ZenMethod
    public static int countFrom(IEntity entity, String id, String source) {
        UUID uuid = ScriptValues.uuid(source);
        return uuid == null ? 0 : StatusworksApi.statuses().count(CraftTweakerMC.getEntity(entity), id, uuid);
    }

    /** Remaining ticks of the soonest timer, or -1 for none. */
    @ZenMethod
    public static int remaining(IEntity entity, String id) {
        return StatusworksApi.statuses().remainingTicks(CraftTweakerMC.getEntity(entity), id);
    }

    @ZenMethod
    public static int remainingFrom(IEntity entity, String id, String source) {
        UUID uuid = ScriptValues.uuid(source);
        return uuid == null ? -1
                : StatusworksApi.statuses().remainingTicks(CraftTweakerMC.getEntity(entity), id, uuid);
    }

    /** An entity's UUID as text, the form the source-taking methods use; the empty text for null. */
    @ZenMethod
    public static String idOf(IEntity entity) {
        net.minecraft.entity.Entity e = CraftTweakerMC.getEntity(entity);
        return e == null ? "" : e.getUniqueID().toString();
    }

    /** The UUIDs of the sources as text; the empty text is the group without a source. */
    @ZenMethod
    public static String[] sources(IEntity entity, String id) {
        return ScriptValues.texts(StatusworksApi.statuses().sources(CraftTweakerMC.getEntity(entity), id));
    }
}
