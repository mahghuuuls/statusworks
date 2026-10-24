package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.definition.Contribution;
import net.minecraft.entity.EntityLivingBase;

import java.util.function.BiConsumer;

/**
 * The contributions an entity receives from its active marks, as the mark store answers them. Defined here so
 * the dependency points from marks to contributions, never back.
 */
public interface MarkContributions {

    /** Hands every contribution of the entity's active marks, with its contribution key, to {@code out}. */
    void collect(EntityLivingBase entity, BiConsumer<String, Contribution> out);

    /** Answers nothing: no marks. */
    MarkContributions NONE = (entity, out) -> {
    };
}
