package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.core.contribution.ContributionRecord;
import com.mahghuuuls.statusworks.core.rule.CooldownState;
import com.mahghuuuls.statusworks.core.rule.PoolState;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tracked state one living entity carries: its status, pool, and running cooldown instances. Attached
 * to every living entity that can hold state and saved with the entity, so it follows the entity through
 * chunk unloading, restarts, and dimension changes. Empty and cheap until something is applied.
 *
 * <p>Instances of each kind are kept in that kind's registration order, the order every tick processes them in.
 * An empty pool (amount 0) is still an instance.</p>
 */
public final class EntityState {

    @CapabilityInject(EntityState.class)
    public static Capability<EntityState> CAPABILITY = null;

    /** One status instance with its definition id and registration order. */
    public static final class StatusSlot {
        public final String id;
        public final int order;
        public final StatusState state;

        StatusSlot(String id, int order, StatusState state) {
            this.id = id;
            this.order = order;
            this.state = state;
        }
    }

    /** One pool instance with its definition id and registration order. */
    public static final class PoolSlot {
        public final String id;
        public final int order;
        public final PoolState state;

        PoolSlot(String id, int order, PoolState state) {
            this.id = id;
            this.order = order;
            this.state = state;
        }
    }

    /** One running cooldown with its definition id and registration order. */
    public static final class CooldownSlot {
        public final String id;
        public final int order;
        public final CooldownState state;

        CooldownSlot(String id, int order, CooldownState state) {
            this.id = id;
            this.order = order;
            this.state = state;
        }
    }

    /** What loading changed in one saved instance, reported once the entity joins a world. */
    public static final class LoadNote {
        /** {@code status}, {@code pool}, or {@code cooldown}. */
        public final String kind;
        public final String id;
        /** Stack count of a status or amount of a pool, as saved. */
        public final double countBefore;
        /** Stack count or amount after loading; 0 when the instance ended. */
        public final double countAfter;
        /** {@code invalid} when the instance ended because its definition is gone; null when it was fitted. */
        @Nullable public final String reason;

        public LoadNote(String kind, String id, double countBefore, double countAfter, @Nullable String reason) {
            this.kind = kind;
            this.id = id;
            this.countBefore = countBefore;
            this.countAfter = countAfter;
            this.reason = reason;
        }
    }

    // Kept in memory (never saved) so inspect can show what the last load did; reported to the log once.
    @Nullable
    private List<LoadNote> loadNotes;
    private int loadNotesReported;

    public void addLoadNote(LoadNote note) {
        if (loadNotes == null) {
            loadNotes = new ArrayList<>();
        }
        loadNotes.add(note);
    }

    /** What loading this entity ended or fitted; read-only, empty when nothing. */
    public List<LoadNote> loadNotes() {
        return loadNotes == null ? java.util.Collections.<LoadNote>emptyList()
                : java.util.Collections.unmodifiableList(loadNotes);
    }

    /** The load notes not yet reported to the diagnostic log; each is returned once. */
    public List<LoadNote> takeLoadNotes() {
        if (loadNotes == null || loadNotesReported == loadNotes.size()) {
            return java.util.Collections.emptyList();
        }
        List<LoadNote> fresh = new ArrayList<>(loadNotes.subList(loadNotesReported, loadNotes.size()));
        loadNotesReported = loadNotes.size();
        return fresh;
    }

    /** State saved by a newer Statusworks, kept unchanged so a downgrade does not erase it. */
    @Nullable
    private net.minecraft.nbt.NBTTagCompound newerFormat;

    @Nullable
    public net.minecraft.nbt.NBTTagCompound newerFormat() {
        return newerFormat;
    }

    public void keepNewerFormat(net.minecraft.nbt.NBTTagCompound saved) {
        this.newerFormat = saved;
    }

    // Created on first use: every living entity on the server carries this object, most of them empty.
    private Map<String, StatusSlot> statusesById;
    private TreeMap<Integer, StatusSlot> statusesByOrder;

    @Nullable
    public StatusState status(String id) {
        StatusSlot slot = statusesById == null ? null : statusesById.get(id);
        return slot == null ? null : slot.state;
    }

    /** The instance for the id, created empty when absent. */
    public StatusState statusOrCreate(String id, int order) {
        if (statusesById == null) {
            statusesById = new HashMap<>();
            statusesByOrder = new TreeMap<>();
        }
        StatusSlot slot = statusesById.get(id);
        if (slot == null) {
            slot = new StatusSlot(id, order, new StatusState());
            statusesById.put(id, slot);
            statusesByOrder.put(order, slot);
        }
        return slot.state;
    }

    public void removeStatus(String id) {
        StatusSlot slot = statusesById == null ? null : statusesById.remove(id);
        if (slot != null) {
            statusesByOrder.remove(slot.order);
        }
    }

    /** Status instances in registration order; a copy, safe to iterate while instances change. */
    public List<StatusSlot> statuses() {
        return statusesByOrder == null ? new ArrayList<>() : new ArrayList<>(statusesByOrder.values());
    }

    // Created on first use, like the statuses.
    private Map<String, PoolSlot> poolsById;
    private TreeMap<Integer, PoolSlot> poolsByOrder;

    @Nullable
    public PoolState pool(String id) {
        PoolSlot slot = poolsById == null ? null : poolsById.get(id);
        return slot == null ? null : slot.state;
    }

    /** Adds a pool instance, replacing any with the same id. */
    public void putPool(String id, int order, PoolState state) {
        if (poolsById == null) {
            poolsById = new HashMap<>();
            poolsByOrder = new TreeMap<>();
        }
        removePool(id);
        PoolSlot slot = new PoolSlot(id, order, state);
        poolsById.put(id, slot);
        poolsByOrder.put(order, slot);
    }

    public void removePool(String id) {
        PoolSlot slot = poolsById == null ? null : poolsById.remove(id);
        if (slot != null) {
            poolsByOrder.remove(slot.order);
        }
    }

    /** Pool instances in registration order; a copy, safe to iterate while instances change. */
    public List<PoolSlot> pools() {
        return poolsByOrder == null ? new ArrayList<>() : new ArrayList<>(poolsByOrder.values());
    }

    // Created on first use: most entities never get a contribution.
    @Nullable
    private ContributionRecord contributions;

    /** The record of the contributions applied to this entity. */
    public ContributionRecord contributions() {
        if (contributions == null) {
            contributions = new ContributionRecord();
        }
        return contributions;
    }

    /** Whether there is nothing to save: no instance and no applied contribution. */
    public boolean hasNothingToSave() {
        return isEmpty() && (contributions == null || contributions.isEmpty());
    }

    // Created on first use, like the statuses.
    private Map<String, CooldownSlot> cooldownsById;
    private TreeMap<Integer, CooldownSlot> cooldownsByOrder;

    @Nullable
    public CooldownState cooldown(String id) {
        CooldownSlot slot = cooldownsById == null ? null : cooldownsById.get(id);
        return slot == null ? null : slot.state;
    }

    /** Adds a running cooldown, replacing any with the same id. */
    public void putCooldown(String id, int order, CooldownState state) {
        if (cooldownsById == null) {
            cooldownsById = new HashMap<>();
            cooldownsByOrder = new TreeMap<>();
        }
        removeCooldown(id);
        CooldownSlot slot = new CooldownSlot(id, order, state);
        cooldownsById.put(id, slot);
        cooldownsByOrder.put(order, slot);
    }

    public void removeCooldown(String id) {
        CooldownSlot slot = cooldownsById == null ? null : cooldownsById.remove(id);
        if (slot != null) {
            cooldownsByOrder.remove(slot.order);
        }
    }

    /** Running cooldowns in registration order; a copy, safe to iterate while instances change. */
    public List<CooldownSlot> cooldowns() {
        return cooldownsByOrder == null ? new ArrayList<>() : new ArrayList<>(cooldownsByOrder.values());
    }

    public boolean isEmpty() {
        return (statusesById == null || statusesById.isEmpty()) && (poolsById == null || poolsById.isEmpty())
                && (cooldownsById == null || cooldownsById.isEmpty());
    }
}
