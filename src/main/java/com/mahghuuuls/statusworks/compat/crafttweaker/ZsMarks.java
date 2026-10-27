package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.MarkSnapshot;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.entity.IEntityLivingBase;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.entity.EntityLivingBase;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.List;

/** Marks in scripts: {@code Marks.define(id)} and the operations and queries of the Java {@code MarkService}. */
@ZenRegister
@ZenClass("mods.statusworks.Marks")
public final class ZsMarks {

    private ZsMarks() {
    }

    @ZenMethod
    public static ZsBuilders.Mark define(String id) {
        return new ZsBuilders.Mark(id);
    }

    @ZenMethod
    public static ZsResult mark(IEntity source, IEntity target, String id) {
        return ZsResult.of(StatusworksApi.marks().mark(CraftTweakerMC.getEntity(source),
                CraftTweakerMC.getEntity(target), id));
    }

    /** {@link #mark} for a duration in ticks; below 1 means the default. */
    @ZenMethod
    public static ZsResult markFor(IEntity source, IEntity target, String id, int ticks) {
        return ZsResult.of(StatusworksApi.marks().mark(CraftTweakerMC.getEntity(source),
                CraftTweakerMC.getEntity(target), id, ticks));
    }

    @ZenMethod
    public static boolean unmark(IEntity source, IEntity target, String id) {
        return StatusworksApi.marks().unmark(CraftTweakerMC.getEntity(source), CraftTweakerMC.getEntity(target), id);
    }

    @ZenMethod
    public static int clearSource(IEntity source, String id) {
        return StatusworksApi.marks().clearSource(CraftTweakerMC.getEntity(source), id);
    }

    @ZenMethod
    public static int clearTarget(IEntity target, String id) {
        return StatusworksApi.marks().clearTarget(CraftTweakerMC.getEntity(target), id);
    }

    @ZenMethod
    public static boolean isMarked(IEntity source, IEntity target, String id) {
        return StatusworksApi.marks().isMarked(CraftTweakerMC.getEntity(source), CraftTweakerMC.getEntity(target),
                id);
    }

    @ZenMethod
    public static Info[] targets(IEntity source, String id) {
        return infos(StatusworksApi.marks().targets(CraftTweakerMC.getEntity(source), id));
    }

    @ZenMethod
    public static Info[] sources(IEntity target, String id) {
        return infos(StatusworksApi.marks().sources(CraftTweakerMC.getEntity(target), id));
    }

    /** The loaded targets only. */
    @ZenMethod
    public static IEntityLivingBase[] targetEntities(IEntity source, String id) {
        return entities(StatusworksApi.marks().targetEntities(CraftTweakerMC.getEntity(source), id));
    }

    /** The loaded sources only. */
    @ZenMethod
    public static IEntityLivingBase[] sourceEntities(IEntity target, String id) {
        return entities(StatusworksApi.marks().sourceEntities(CraftTweakerMC.getEntity(target), id));
    }

    @ZenMethod
    public static int targetCount(IEntity source, String id) {
        return StatusworksApi.marks().targetCount(CraftTweakerMC.getEntity(source), id);
    }

    @ZenMethod
    public static int sourceCount(IEntity target, String id) {
        return StatusworksApi.marks().sourceCount(CraftTweakerMC.getEntity(target), id);
    }

    private static Info[] infos(List<MarkSnapshot> snapshots) {
        Info[] out = new Info[snapshots.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Info(snapshots.get(i));
        }
        return out;
    }

    private static IEntityLivingBase[] entities(List<EntityLivingBase> list) {
        IEntityLivingBase[] out = new IEntityLivingBase[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = CraftTweakerMC.getIEntityLivingBase(list.get(i));
        }
        return out;
    }

    /** One mark as seen from one of its participants. */
    @ZenRegister
    @ZenClass("mods.statusworks.MarkInfo")
    public static final class Info {
        private final MarkSnapshot s;

        Info(MarkSnapshot s) {
            this.s = s;
        }

        @ZenGetter("definitionId")
        public String definitionId() {
            return s.definitionId();
        }

        @ZenGetter("sourceId")
        public String sourceId() {
            return ScriptValues.text(s.sourceId());
        }

        @ZenGetter("targetId")
        public String targetId() {
            return ScriptValues.text(s.targetId());
        }

        /** The other participant when it is loaded, else null. */
        @ZenGetter("other")
        public IEntityLivingBase other() {
            return CraftTweakerMC.getIEntityLivingBase(s.other());
        }

        @ZenGetter("active")
        public boolean active() {
            return s.isActive();
        }

        /** Remaining active ticks, or -1 for a mark without a duration. */
        @ZenGetter("remaining")
        public int remaining() {
            return s.remaining();
        }

        @ZenGetter("inactiveTicks")
        public int inactiveTicks() {
            return s.inactiveTicks();
        }
    }
}
