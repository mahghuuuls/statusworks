package com.mahghuuuls.statusworks.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.util.List;

/**
 * Operations and queries on marks. Every method works on the logical server only; on the client, and for a dead
 * entity (operations), a non-living entity, a fake player, or an unknown or disabled definition, operations report
 * {@code rejected} or 0 and queries report nothing. Call it from the server thread only.
 *
 * <p>Queries never load a chunk or a dimension: a participant that is not loaded is counted but returned without
 * its entity. Queries never change a mark and never run a callback.</p>
 */
public interface MarkService {

    /**
     * Marks {@code target} from {@code source} with the definition's default duration: creates the mark, refreshes
     * an existing one by the refresh rule, or at the target limit replaces the source's oldest mark or rejects.
     * Marking oneself is rejected.
     */
    MarkResult mark(Entity source, Entity target, String id);

    /** {@link #mark(Entity, Entity, String)} with a duration in active ticks; below 1 means the default. */
    MarkResult mark(Entity source, Entity target, String id, int ticks);

    /** Ends the source's mark on the target with reason {@code removed}; returns whether one existed. */
    boolean unmark(Entity source, Entity target, String id);

    /** Ends every mark of the definition the source holds, with reason {@code removed}; returns how many. */
    int clearSource(Entity source, String id);

    /** Ends every mark of the definition on the target, from every source, with reason {@code removed}. */
    int clearTarget(Entity target, String id);

    boolean isMarked(Entity source, Entity target, String id);

    /** The source's marks of the definition, oldest first, active and inactive. */
    List<MarkSnapshot> targets(Entity source, String id);

    /** The marks of the definition on the target, oldest first, active and inactive. */
    List<MarkSnapshot> sources(Entity target, String id);

    /** The source's marked targets that are loaded; {@link #targetCount} also counts the others. */
    List<EntityLivingBase> targetEntities(Entity source, String id);

    /** The sources marking the target that are loaded; {@link #sourceCount} also counts the others. */
    List<EntityLivingBase> sourceEntities(Entity target, String id);

    int targetCount(Entity source, String id);

    int sourceCount(Entity target, String id);
}
