package com.mahghuuuls.statusworks.core.mark;

import com.mahghuuuls.statusworks.api.definition.MarkSide;
import com.mahghuuuls.statusworks.core.contribution.ContributionValues;
import com.mahghuuuls.statusworks.core.rule.MarkLimits;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One mark: its definition, both participants by UUID, its creation order number, its active-tick age and end
 * age, how long it has been inactive, and whether it is active. The participant names are kept for display while
 * a participant is unloaded. The entity fields are a cache filled by the activity check; they are never saved.
 */
final class MarkRecord {

    final String id;
    final UUID source;
    final UUID target;
    /** Creation order: lower is older. Refreshing never changes it. */
    final long order;
    long age;
    long endAge = MarkLimits.NO_END;
    int inactiveTicks;
    boolean active;
    String sourceName;
    String targetName;

    @Nullable transient EntityLivingBase sourceEntity;
    @Nullable transient EntityLivingBase targetEntity;
    /** The source's tick counter when the mark last advanced, so a frozen source pauses the mark. */
    transient int lastSourceTicks;
    transient boolean ended;
    /** Inactive only because the participants are beyond the maximum distance; then no timeout counts. */
    transient boolean distanceOnly;
    /** Contribution keys of each side, built once: the same keys come back on every reconcile. */
    @Nullable private transient String[] sourceKeys;
    @Nullable private transient String[] targetKeys;

    MarkRecord(String id, UUID source, UUID target, long order, String sourceName, String targetName) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.order = order;
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    /** The contribution keys of one side's {@code count} contributions on this mark. */
    String[] keys(MarkSide side, int count) {
        String[] keys = side == MarkSide.SOURCE ? sourceKeys : targetKeys;
        if (keys == null || keys.length != count) {
            keys = new String[count];
            UUID other = side == MarkSide.SOURCE ? target : source;
            for (int i = 0; i < count; i++) {
                keys[i] = ContributionValues.markKey(id, i, side.text(), other);
            }
            if (side == MarkSide.SOURCE) {
                sourceKeys = keys;
            } else {
                targetKeys = keys;
            }
        }
        return keys;
    }

    boolean involves(UUID entity) {
        return source.equals(entity) || target.equals(entity);
    }

    @Override
    public String toString() {
        return id + " " + sourceName + "->" + targetName + " order=" + order;
    }
}
