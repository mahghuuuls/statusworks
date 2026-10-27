package com.mahghuuuls.statusworks.compat.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;

/**
 * The callback types a script's {@code function(context) { ... }} becomes, one per kind. A callback runs only on the
 * server; Statusworks catches what it throws (a script error never reaches chat).
 */
public final class ZsFunctions {

    private ZsFunctions() {
    }

    @ZenRegister
    @ZenClass("mods.statusworks.StatusFunction")
    public interface Status {
        void handle(ZsContexts.Status context);
    }

    @ZenRegister
    @ZenClass("mods.statusworks.PoolFunction")
    public interface Pool {
        void handle(ZsContexts.Pool context);
    }

    @ZenRegister
    @ZenClass("mods.statusworks.MarkFunction")
    public interface Mark {
        void handle(ZsContexts.Mark context);
    }

    @ZenRegister
    @ZenClass("mods.statusworks.CooldownFunction")
    public interface Cooldown {
        void handle(ZsContexts.Cooldown context);
    }
}
