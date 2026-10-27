package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.minecraft.CraftTweakerMC;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.Map;

/**
 * The current values an entity has for one addon contribution type, as the Java {@code ContributionService} answers
 * them: one value per contribution key. Addons register the types in Java; scripts use them by id.
 */
@ZenRegister
@ZenClass("mods.statusworks.Contributions")
public final class ZsContributions {

    private ZsContributions() {
    }

    /** The keys of the entity's current contributions to the type. */
    @ZenMethod
    public static String[] keys(IEntity entity, String typeId) {
        return values(entity, typeId).keySet().toArray(new String[0]);
    }

    /** The current value of one contribution key, or 0 when there is none. */
    @ZenMethod
    public static double value(IEntity entity, String typeId, String key) {
        Double v = values(entity, typeId).get(key);
        return v == null ? 0 : v;
    }

    /** The sum of the entity's current contributions to the type (a convenience the Java service leaves to callers). */
    @ZenMethod
    public static double total(IEntity entity, String typeId) {
        double sum = 0;
        for (double v : values(entity, typeId).values()) {
            sum += v;
        }
        return sum;
    }

    private static Map<String, Double> values(IEntity entity, String typeId) {
        return StatusworksApi.contributions().currentValues(CraftTweakerMC.getEntity(entity), typeId);
    }
}
