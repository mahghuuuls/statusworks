package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.callback.CooldownContext;
import com.mahghuuuls.statusworks.api.callback.MarkContext;
import com.mahghuuuls.statusworks.api.callback.PoolContext;
import com.mahghuuuls.statusworks.api.callback.StatusContext;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.entity.IEntityLivingBase;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;

/** What a script callback receives, one class per kind, with entities as CraftTweaker entities. */
public final class ZsContexts {

    private ZsContexts() {
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }

    @ZenRegister
    @ZenClass("mods.statusworks.StatusContext")
    public static final class Status {
        private final StatusContext c;

        Status(StatusContext c) {
            this.c = c;
        }

        @ZenGetter("entity")
        public IEntityLivingBase entity() {
            return CraftTweakerMC.getIEntityLivingBase(c.entity());
        }

        @ZenGetter("definitionId")
        public String definitionId() {
            return c.definitionId();
        }

        @ZenGetter("countBefore")
        public int countBefore() {
            return c.countBefore();
        }

        @ZenGetter("countAfter")
        public int countAfter() {
            return c.countAfter();
        }

        /** The source's UUID, or the empty text. */
        @ZenGetter("source")
        public String source() {
            return ScriptValues.text(c.source());
        }

        /** The source entity when it is loaded, else null. */
        @ZenGetter("sourceEntity")
        public IEntity sourceEntity() {
            return CraftTweakerMC.getIEntity(c.sourceEntity());
        }

        /** For the removed callback, why the status ended ({@code expired}, {@code removed}, ...); otherwise empty. */
        @ZenGetter("reason")
        public String reason() {
            return text(c.reason());
        }
    }

    @ZenRegister
    @ZenClass("mods.statusworks.PoolContext")
    public static final class Pool {
        private final PoolContext c;

        Pool(PoolContext c) {
            this.c = c;
        }

        @ZenGetter("entity")
        public IEntityLivingBase entity() {
            return CraftTweakerMC.getIEntityLivingBase(c.entity());
        }

        @ZenGetter("definitionId")
        public String definitionId() {
            return c.definitionId();
        }

        @ZenGetter("amountBefore")
        public double amountBefore() {
            return c.amountBefore();
        }

        @ZenGetter("amountAfter")
        public double amountAfter() {
            return c.amountAfter();
        }

        @ZenGetter("capacity")
        public double capacity() {
            return c.capacity();
        }

        @ZenGetter("reason")
        public String reason() {
            return text(c.reason());
        }
    }

    @ZenRegister
    @ZenClass("mods.statusworks.MarkContext")
    public static final class Mark {
        private final MarkContext c;

        Mark(MarkContext c) {
            this.c = c;
        }

        @ZenGetter("definitionId")
        public String definitionId() {
            return c.definitionId();
        }

        @ZenGetter("sourceId")
        public String sourceId() {
            return ScriptValues.text(c.sourceId());
        }

        @ZenGetter("targetId")
        public String targetId() {
            return ScriptValues.text(c.targetId());
        }

        /** The source when it is loaded, else null. */
        @ZenGetter("source")
        public IEntityLivingBase source() {
            return CraftTweakerMC.getIEntityLivingBase(c.source());
        }

        /** The target when it is loaded, else null. */
        @ZenGetter("target")
        public IEntityLivingBase target() {
            return CraftTweakerMC.getIEntityLivingBase(c.target());
        }

        @ZenGetter("reason")
        public String reason() {
            return text(c.reason());
        }
    }

    @ZenRegister
    @ZenClass("mods.statusworks.CooldownContext")
    public static final class Cooldown {
        private final CooldownContext c;

        Cooldown(CooldownContext c) {
            this.c = c;
        }

        @ZenGetter("entity")
        public IEntityLivingBase entity() {
            return CraftTweakerMC.getIEntityLivingBase(c.entity());
        }

        @ZenGetter("definitionId")
        public String definitionId() {
            return c.definitionId();
        }

        @ZenGetter("reason")
        public String reason() {
            return text(c.reason());
        }
    }
}
