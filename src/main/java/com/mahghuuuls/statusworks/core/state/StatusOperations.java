package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.api.CountChange;
import com.mahghuuuls.statusworks.api.StackChange;
import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.callback.StatusContext;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Numbers;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.rule.RemovalReason;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Status operations and queries on entities. Checks the call (side, entity, definition, arguments), asks
 * {@link StatusRules} for every decision, keeps {@link EntityState} up to date, writes the diagnostic log, and
 * hands every author callback to the {@link CallbackRunner}. Callers never save, sync, or schedule anything.
 */
public final class StatusOperations implements StatusService {

    /** Why a change happened, as the diagnostic log names it. */
    public static final String CAUSE_OPERATION = "operation";
    public static final String CAUSE_COMMAND = "command";
    public static final String CAUSE_TIMER = "timer";
    public static final String CAUSE_CALLBACK = "callback";
    public static final String CAUSE_DEATH = "death";
    public static final String CAUSE_LOAD = "load";

    private final DefinitionRegistry registry;
    private final DiagnosticLog log;
    private final Warnings warnings;
    private final CallbackRunner callbacks;
    private final Consumer<EntityLivingBase> onStateAdded;
    /** Told after every change of an entity's statuses, so its contributions follow. */
    private final BiConsumer<EntityLivingBase, EntityState> changed;

    public StatusOperations(DefinitionRegistry registry, DiagnosticLog log, Warnings warnings,
                            CallbackRunner callbacks, Consumer<EntityLivingBase> onStateAdded,
                            BiConsumer<EntityLivingBase, EntityState> changed) {
        this.registry = registry;
        this.log = log;
        this.warnings = warnings;
        this.callbacks = callbacks;
        this.onStateAdded = onStateAdded;
        this.changed = changed;
    }

    /** A checked call target: the living entity, its state, and the definition. */
    private static final class Target {
        final EntityLivingBase entity;
        final EntityState state;
        final DefinitionRegistry.Entry entry;

        Target(EntityLivingBase entity, EntityState state, DefinitionRegistry.Entry entry) {
            this.entity = entity;
            this.state = state;
            this.entry = entry;
        }
    }

    /** Either a target or why there is none. */
    private static final class Checked {
        @Nullable final Target target;
        @Nullable final String failure;

        Checked(@Nullable Target target, @Nullable String failure) {
            this.target = target;
            this.failure = failure;
        }
    }

    private Checked check(@Nullable Entity entity, @Nullable String id, boolean forChange) {
        EntityTargets.Checked target = EntityTargets.check(entity, forChange);
        if (target.failure != null) {
            return new Checked(null, target.failure);
        }
        EntityLivingBase living = target.entity;
        EntityState state = target.state;
        if (id == null) {
            return new Checked(null, "definition id is null");
        }
        DefinitionRegistry.Entry entry = registry.status(id);
        if (entry == null) {
            String why = registry.isInvalid(id) ? "status " + id + " is disabled because its definition is invalid"
                    : "no status is registered with id " + id;
            warnings.once("status:" + id, "Statusworks: " + why);
            return new Checked(null, why);
        }
        return new Checked(new Target(living, state, entry), null);
    }

    @Override
    public StackChange apply(Entity entity, String id, int stacks) {
        return apply(entity, id, stacks, null, 0, CAUSE_OPERATION);
    }

    @Override
    public StackChange apply(Entity entity, String id, int stacks, @Nullable Entity source, int durationTicks) {
        return apply(entity, id, stacks, source, durationTicks, CAUSE_OPERATION);
    }

    public StackChange apply(Entity entity, String id, int stacks, @Nullable Entity source, int durationTicks,
                             String cause) {
        Checked c = check(entity, id, true);
        if (c.target == null) {
            return StackChange.failed(c.failure);
        }
        if (stacks < 1) {
            return StackChange.failed("stacks must be 1 or more, got " + stacks);
        }
        Target t = c.target;
        StatusDefinition def = t.entry.definition();
        boolean wasEmpty = t.state.isEmpty();
        boolean created = t.state.status(id) == null;
        StatusState status = t.state.statusOrCreate(id, t.entry.order());
        UUID sourceId = source == null ? null : source.getUniqueID();
        UUID key = StatusRules.groupKey(def, sourceId);
        StatusRules.Applied r = StatusRules.apply(def, status, key, stacks, durationTicks);
        afterChange(t, id, status, r.totalBefore, created, cause, RemovalReason.REMOVED, sourceId);
        if (wasEmpty && !t.state.isEmpty()) {
            onStateAdded.accept(t.entity);
        }
        return StackChange.of(r.added, r.rejected);
    }

    @Override
    public CountChange remove(Entity entity, String id, int stacks) {
        return remove(entity, id, stacks, null, CAUSE_OPERATION);
    }

    @Override
    public CountChange remove(Entity entity, String id, int stacks, UUID source) {
        return remove(entity, id, stacks, source, CAUSE_OPERATION);
    }

    public CountChange remove(Entity entity, String id, int stacks, @Nullable UUID source, String cause) {
        Checked c = check(entity, id, true);
        if (c.target == null) {
            return CountChange.failed(c.failure);
        }
        if (stacks < 1) {
            return CountChange.failed("stacks must be 1 or more, got " + stacks);
        }
        Target t = c.target;
        StatusState status = t.state.status(id);
        if (status == null) {
            return CountChange.of(0, 0);
        }
        int before = status.total();
        StatusRules.remove(t.entry.definition(), status,
                source == null ? null : StatusRules.groupKey(t.entry.definition(), source), stacks);
        afterChange(t, id, status, before, false, cause, RemovalReason.REMOVED, null);
        return CountChange.of(before, status.total());
    }

    @Override
    public CountChange setCount(Entity entity, String id, int count, UUID source) {
        return setCount(entity, id, count, source, CAUSE_OPERATION);
    }

    public CountChange setCount(Entity entity, String id, int count, @Nullable UUID source, String cause) {
        Checked c = check(entity, id, true);
        if (c.target == null) {
            return CountChange.failed(c.failure);
        }
        Target t = c.target;
        StatusDefinition def = t.entry.definition();
        boolean wasEmpty = t.state.isEmpty();
        boolean created = t.state.status(id) == null;
        StatusState status = t.state.statusOrCreate(id, t.entry.order());
        int before = status.total();
        StatusRules.setCount(def, status, StatusRules.groupKey(def, source), count);
        afterChange(t, id, status, before, created, cause, RemovalReason.REMOVED, null);
        if (wasEmpty && !t.state.isEmpty()) {
            onStateAdded.accept(t.entity);
        }
        return CountChange.of(before, status.total());
    }

    @Override
    public CountChange clear(Entity entity, String id) {
        return clear(entity, id, CAUSE_OPERATION);
    }

    public CountChange clear(Entity entity, String id, String cause) {
        Checked c = check(entity, id, true);
        if (c.target == null) {
            return CountChange.failed(c.failure);
        }
        Target t = c.target;
        StatusState status = t.state.status(id);
        if (status == null) {
            return CountChange.of(0, 0);
        }
        int before = status.total();
        StatusRules.clear(status);
        afterChange(t, id, status, before, false, cause, RemovalReason.REMOVED, null);
        return CountChange.of(before, 0);
    }

    /**
     * After any change: drops the instance when it has no stacks left, logs the change, and runs the callbacks in
     * their order: applied (only for a new instance that got stacks), quantity changed, removed.
     */
    private void afterChange(Target t, String id, StatusState status, int before, boolean created, String cause,
                             RemovalReason reasonWhenEnded, @Nullable UUID source) {
        settle(t.entity, t.state, t.entry.definition(), id, status, before, created,
                callbacks.inCallback() && CAUSE_OPERATION.equals(cause) ? CAUSE_CALLBACK : cause, reasonWhenEnded,
                source);
    }

    private void settle(EntityLivingBase entity, EntityState state, StatusDefinition def, String id,
                        StatusState status, int before, boolean created, String cause, RemovalReason reasonWhenEnded,
                        @Nullable UUID source) {
        int after = status.total();
        if (after == 0 && state.status(id) == status) {
            state.removeStatus(id);
        }
        if (after == before) {
            return;
        }
        String reason = after == 0 && before > 0 ? reasonWhenEnded.text() : null;
        logChange(entity, id, before, after, cause, reason);
        changed.accept(entity, state);
        StatusContext context = new StatusContext(entity, id, before, after, source, reason);
        callbacks.batch(() -> {
            if (created && after > 0 && def.onApplied() != null) {
                callbacks.run(id, "applied", () -> def.onApplied().onApplied(context));
            }
            if (def.onQuantityChanged() != null) {
                callbacks.run(id, "quantity_changed", () -> def.onQuantityChanged().onQuantityChanged(context));
            }
            if (reason != null && def.onRemoved() != null) {
                callbacks.run(id, "removed", () -> def.onRemoved().onRemoved(context));
            }
        });
    }

    private void logChange(EntityLivingBase entity, String id, int before, int after, String cause,
                           @Nullable String reason) {
        if (!log.isEnabled()) {
            return;
        }
        log.change(id, entityKeys(entity), entityLabel(entity), "status", Integer.toString(before),
                Integer.toString(after), cause, reason);
    }

    /**
     * Ends, with reason {@code death}, every instance of an entity that just died, except on a player the
     * instances whose death rule is {@code keep}. Runs the removal callbacks; the entity is already dead.
     */
    public void endOnDeath(EntityLivingBase entity, EntityState state, boolean player) {
        for (EntityState.StatusSlot slot : state.statuses()) {
            DefinitionRegistry.Entry entry = registry.status(slot.id);
            if (entry == null || state.status(slot.id) != slot.state) {
                continue;
            }
            StatusDefinition def = entry.definition();
            if (StatusRules.survivesDeath(def, player)) {
                continue;
            }
            int before = slot.state.total();
            StatusRules.clear(slot.state);
            settle(entity, state, def, slot.id, slot.state, before, false, CAUSE_DEATH, RemovalReason.DEATH, null);
        }
    }

    /**
     * Ends, with reason {@code invalid}, every instance of any kind on the entity whose definition is no longer
     * valid (the server-start attribute check can disable a definition after entities in always-loaded chunks
     * were restored). No callback runs: the definition is gone. Then its contributions follow.
     */
    public void endOrphans(EntityLivingBase entity, EntityState state, DefinitionRegistry registry) {
        boolean changedAny = false;
        for (EntityState.StatusSlot slot : state.statuses()) {
            if (registry.status(slot.id) == null) {
                int before = slot.state.total();
                state.removeStatus(slot.id);
                logChange(entity, slot.id, before, 0, CAUSE_LOAD, RemovalReason.INVALID.text());
                changedAny = true;
            }
        }
        for (EntityState.PoolSlot slot : state.pools()) {
            if (registry.pool(slot.id) == null) {
                state.removePool(slot.id);
                logEnd(entity, slot.id, "pool", Numbers.format(slot.state.amount()));
                changedAny = true;
            }
        }
        for (EntityState.CooldownSlot slot : state.cooldowns()) {
            if (registry.cooldown(slot.id) == null) {
                state.removeCooldown(slot.id);
                logEnd(entity, slot.id, "cooldown", Integer.toString(
                        com.mahghuuuls.statusworks.core.rule.CooldownRules.remaining(slot.state)));
                changedAny = true;
            }
        }
        if (changedAny) {
            changed.accept(entity, state);
        }
    }

    private void logEnd(EntityLivingBase entity, String id, String kind, String before) {
        if (log.isEnabled()) {
            log.change(id, entityKeys(entity), entityLabel(entity), kind, before, "0", CAUSE_LOAD,
                    RemovalReason.INVALID.text());
        }
    }

    /** Writes what loading ended or fitted, for every kind, to the diagnostic log once the entity is in a world. */
    public void reportLoad(EntityLivingBase entity, EntityState state) {
        for (EntityState.LoadNote note : state.takeLoadNotes()) {
            if (!log.isEnabled()) {
                continue;
            }
            if (note.reason == null && note.countBefore == note.countAfter) {
                log.note("fitted entity=" + entityLabel(entity) + " kind=" + note.kind + " def=" + note.id
                        + " value=" + Numbers.format(note.countAfter) + " cause=" + CAUSE_LOAD);
            } else {
                log.change(note.id, entityKeys(entity), entityLabel(entity), note.kind,
                        Numbers.format(note.countBefore), Numbers.format(note.countAfter), CAUSE_LOAD, note.reason);
            }
        }
    }

    /**
     * Advances every status of one updated entity by one active tick, in registration order. For each instance:
     * its interval callback first (when this tick completes an interval), then timers, then expiration.
     */
    public void tick(EntityLivingBase entity, EntityState state) {
        for (EntityState.StatusSlot slot : state.statuses()) {
            DefinitionRegistry.Entry entry = registry.status(slot.id);
            if (entry == null) {
                continue;
            }
            StatusDefinition def = entry.definition();
            StatusState status = slot.state;
            if (state.status(slot.id) != status) {
                // A callback earlier in this pass ended (or ended and re-applied) this instance.
                continue;
            }
            if (def.onInterval() != null && StatusRules.intervalDue(def, status)) {
                StatusContext context = new StatusContext(entity, slot.id, status.total(), status.total(), null, null);
                callbacks.run(slot.id, "interval", () -> def.onInterval().onInterval(context));
                if (state.status(slot.id) != status) {
                    // The callback ended (or ended and re-applied) this instance; its new life starts next tick.
                    continue;
                }
            }
            int before = status.total();
            StatusRules.tick(def, status);
            settle(entity, state, def, slot.id, status, before, false, CAUSE_TIMER, RemovalReason.EXPIRED, null);
        }
    }

    @Override
    public boolean has(Entity entity, String id) {
        StatusState s = read(entity, id);
        return s != null && !s.isEmpty();
    }

    @Override
    public int count(Entity entity, String id) {
        StatusState s = read(entity, id);
        return s == null ? 0 : s.total();
    }

    @Override
    public int count(Entity entity, String id, UUID source) {
        StatusState s = read(entity, id);
        return s == null ? 0 : StatusRules.count(s, source);
    }

    @Override
    public int remainingTicks(Entity entity, String id) {
        return remaining(entity, id, null);
    }

    @Override
    public int remainingTicks(Entity entity, String id, UUID source) {
        return remaining(entity, id, source);
    }

    private int remaining(Entity entity, String id, @Nullable UUID source) {
        StatusState s = read(entity, id);
        DefinitionRegistry.Entry entry = id == null ? null : registry.status(id);
        if (s == null || entry == null) {
            return -1;
        }
        return StatusRules.remaining(entry.definition(), s, source);
    }

    @Override
    public List<UUID> sources(Entity entity, String id) {
        StatusState s = read(entity, id);
        if (s == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(s.groups().keySet()));
    }

    @Nullable
    private StatusState read(Entity entity, String id) {
        Checked c = check(entity, id, false);
        return c.target == null ? null : c.target.state.status(id);
    }

    /** The identities a diagnostic filter may name: display name, numeric id, UUID. */
    public static String[] entityKeys(Entity entity) {
        return new String[]{entity.getName(), Integer.toString(entity.getEntityId()), entity.getUniqueID().toString()};
    }

    public static String entityLabel(Entity entity) {
        return entity.getName() + "#" + entity.getEntityId();
    }
}
