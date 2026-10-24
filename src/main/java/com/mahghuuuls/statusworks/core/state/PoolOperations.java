package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.api.PoolChange;
import com.mahghuuuls.statusworks.api.PoolService;
import com.mahghuuuls.statusworks.api.PoolSnapshot;
import com.mahghuuuls.statusworks.api.callback.PoolContext;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.Progression;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Numbers;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.rule.DeathRules;
import com.mahghuuuls.statusworks.core.rule.PoolMath;
import com.mahghuuuls.statusworks.core.rule.PoolState;
import com.mahghuuuls.statusworks.core.rule.RemovalReason;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Pool operations and queries on entities. Checks the call, asks {@link PoolMath} for every amount and timer,
 * keeps the pool instances in {@link EntityState} up to date, writes the diagnostic log, and hands every author
 * callback to the {@link CallbackRunner}.
 *
 * <p>Callbacks: "amount changed" runs for an operation that changed the amount, never for a progression step;
 * "became empty" and "became full" run once per arrival from an operation, progression, or capacity change;
 * "target reached" when a {@code to_target} rule arrives; "removed" with the reason when an instance ends. None
 * runs when an instance is created or restored. The diagnostic log records operations, arrivals (cause
 * {@code progression}), and ends, not each progression step, which would be one line per pool per tick.</p>
 */
public final class PoolOperations implements PoolService {

    /** Callback keys of pools carry this prefix, so a status and a pool with the same id are counted apart. */
    static final String KEY_PREFIX = "pool:";
    public static final String CAUSE_PROGRESSION = "progression";

    private final DefinitionRegistry registry;
    private final DiagnosticLog log;
    private final Warnings warnings;
    private final CallbackRunner callbacks;
    private final Consumer<EntityLivingBase> onStateAdded;
    /** Told after every change of an entity's pools, so its contributions follow. */
    private final BiConsumer<EntityLivingBase, EntityState> changed;

    public PoolOperations(DefinitionRegistry registry, DiagnosticLog log, Warnings warnings,
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
        final DefinitionRegistry.PoolEntry entry;

        Target(EntityLivingBase entity, EntityState state, DefinitionRegistry.PoolEntry entry) {
            this.entity = entity;
            this.state = state;
            this.entry = entry;
        }
    }

    @Nullable
    private String failureOf(@Nullable Entity entity, @Nullable String id, boolean forChange, Target[] out) {
        EntityTargets.Checked target = EntityTargets.check(entity, forChange);
        if (target.failure != null) {
            return target.failure;
        }
        if (id == null) {
            return "definition id is null";
        }
        DefinitionRegistry.PoolEntry entry = registry.pool(id);
        if (entry == null) {
            String why = registry.isInvalidPool(id) ? "pool " + id + " is disabled because its definition is invalid"
                    : "no pool is registered with id " + id;
            warnings.once("pool:" + id, "Statusworks: " + why);
            return why;
        }
        out[0] = new Target(target.entity, target.state, entry);
        return null;
    }

    // ---- amount operations (create a missing instance) ----

    @Override
    public PoolChange add(Entity entity, String id, double amount) {
        return add(entity, id, amount, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange add(Entity entity, String id, double amount, String cause) {
        return amountOperation(entity, id, cause, true, finite(amount, "amount"), s -> PoolMath.add(s, amount));
    }

    @Override
    public PoolChange subtract(Entity entity, String id, double amount) {
        return subtract(entity, id, amount, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange subtract(Entity entity, String id, double amount, String cause) {
        return amountOperation(entity, id, cause, true, finite(amount, "amount"), s -> PoolMath.add(s, -amount));
    }

    @Override
    public PoolChange set(Entity entity, String id, double amount) {
        return set(entity, id, amount, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange set(Entity entity, String id, double amount, String cause) {
        return amountOperation(entity, id, cause, true, finite(amount, "amount"), s -> PoolMath.set(s, amount));
    }

    @Override
    public PoolChange setCapacity(Entity entity, String id, double capacity) {
        return setCapacity(entity, id, capacity, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange setCapacity(Entity entity, String id, double capacity, String cause) {
        String bad = capacity > 0 && !Double.isInfinite(capacity) ? null : "capacity must be above 0, got " + capacity;
        return amountOperation(entity, id, cause, false, bad, s -> PoolMath.setCapacity(s, capacity));
    }

    @Nullable
    private static String finite(double value, String name) {
        return Double.isNaN(value) || Double.isInfinite(value) ? name + " must be a number, got " + value : null;
    }

    private PoolChange amountOperation(Entity entity, String id, String cause, boolean create, @Nullable String bad,
                                       Consumer<PoolState> operation) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return PoolChange.failed(failure);
        }
        if (bad != null) {
            return PoolChange.failed(bad);
        }
        Target t = out[0];
        PoolDefinition def = t.entry.definition();
        boolean wasEmpty = t.state.isEmpty();
        PoolState pool = t.state.pool(id);
        boolean created = false;
        if (pool == null) {
            if (!create) {
                return PoolChange.failed("entity " + t.entity.getName() + " has no pool " + id);
            }
            pool = PoolMath.create(def);
            t.state.putPool(id, t.entry.order(), pool);
            created = true;
        }
        double before = pool.amount();
        double capacityBefore = pool.capacity();
        operation.accept(pool);
        double after = pool.amount();
        String resolved = resolve(cause);
        if (created || after != before || capacityBefore != pool.capacity()) {
            logChange(t.entity, id, created ? "none" : Numbers.format(before), Numbers.format(after), resolved, null);
            changed.accept(t.entity, t.state);
        }
        PoolContext context = new PoolContext(t.entity, id, before, after, pool.capacity(), null);
        boolean empty = PoolMath.becameEmpty(before, after);
        boolean full = PoolMath.becameFull(before, capacityBefore, after, pool.capacity());
        callbacks.batch(() -> {
            if (after != before && def.onAmountChanged() != null) {
                callbacks.run(KEY_PREFIX + id, "amount_changed", () -> def.onAmountChanged().onAmountChanged(context));
            }
            arrivals(def, id, context, empty, full, false);
        });
        if (wasEmpty && !t.state.isEmpty()) {
            onStateAdded.accept(t.entity);
        }
        return PoolChange.of(before, after);
    }

    // ---- operations on an existing instance ----

    @Override
    public PoolChange setLifetime(Entity entity, String id, int ticks) {
        return setLifetime(entity, id, ticks, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange setLifetime(Entity entity, String id, int ticks, String cause) {
        return timerOperation(entity, id, cause, ticks < 1 ? "lifetime must be 1 tick or more, got " + ticks : null,
                "lifetime", s -> PoolMath.setLifetime(s, ticks));
    }

    @Override
    public PoolChange extendLifetime(Entity entity, String id, int ticks) {
        return extendLifetime(entity, id, ticks, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange extendLifetime(Entity entity, String id, int ticks, String cause) {
        return timerOperation(entity, id, cause, ticks < 1 ? "extension must be 1 tick or more, got " + ticks : null,
                "lifetime", s -> PoolMath.extendLifetime(s, ticks));
    }

    @Override
    public PoolChange pause(Entity entity, String id) {
        return pause(entity, id, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange pause(Entity entity, String id, String cause) {
        return timerOperation(entity, id, cause, null, "paused", s -> PoolMath.setPaused(s, true));
    }

    @Override
    public PoolChange resume(Entity entity, String id) {
        return resume(entity, id, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange resume(Entity entity, String id, String cause) {
        return timerOperation(entity, id, cause, null, "paused", s -> PoolMath.setPaused(s, false));
    }

    /** An operation that changes a timer or the pause, never the amount; logged as {@code kind=pool_<what>}. */
    private PoolChange timerOperation(Entity entity, String id, String cause, @Nullable String bad, String what,
                                      Consumer<PoolState> operation) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return PoolChange.failed(failure);
        }
        if (bad != null) {
            return PoolChange.failed(bad);
        }
        Target t = out[0];
        PoolState pool = t.state.pool(id);
        if (pool == null) {
            return PoolChange.failed("entity " + t.entity.getName() + " has no pool " + id);
        }
        String before = timerValue(pool, what);
        operation.accept(pool);
        String after = timerValue(pool, what);
        if (log.isEnabled() && !before.equals(after)) {
            log.change(id, StatusOperations.entityKeys(t.entity), StatusOperations.entityLabel(t.entity),
                    "pool_" + what, before, after, resolve(cause), null);
        }
        return PoolChange.of(pool.amount(), pool.amount());
    }

    private static String timerValue(PoolState pool, String what) {
        return "paused".equals(what) ? Boolean.toString(pool.paused()) : Integer.toString(
                PoolMath.remainingLifetime(pool));
    }

    @Override
    public PoolChange remove(Entity entity, String id) {
        return remove(entity, id, StatusOperations.CAUSE_OPERATION);
    }

    public PoolChange remove(Entity entity, String id, String cause) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return PoolChange.failed(failure);
        }
        Target t = out[0];
        PoolState pool = t.state.pool(id);
        if (pool == null) {
            return PoolChange.failed("entity " + t.entity.getName() + " has no pool " + id);
        }
        double before = pool.amount();
        end(t.entity, t.state, t.entry.definition(), id, pool, resolve(cause), RemovalReason.REMOVED);
        return PoolChange.of(before, 0);
    }

    // ---- queries ----

    @Override
    public boolean has(Entity entity, String id) {
        return read(entity, id) != null;
    }

    @Override
    @Nullable
    public PoolSnapshot get(Entity entity, String id) {
        Target[] out = new Target[1];
        if (failureOf(entity, id, false, out) != null) {
            return null;
        }
        PoolState pool = out[0].state.pool(id);
        return pool == null ? null : snapshot(out[0].entry.definition(), pool);
    }

    public static PoolSnapshot snapshot(PoolDefinition def, PoolState pool) {
        double target = def.progression() == Progression.TO_TARGET
                ? PoolMath.clamp(def.target(), pool.capacity()) : Double.NaN;
        return new PoolSnapshot(pool.amount(), pool.capacity(), PoolMath.remainingLifetime(pool),
                PoolMath.progressionState(def, pool), target, PoolMath.remainingToDeadline(def, pool));
    }

    @Nullable
    private PoolState read(Entity entity, String id) {
        Target[] out = new Target[1];
        return failureOf(entity, id, false, out) != null ? null : out[0].state.pool(id);
    }

    // ---- ticking and lifecycle ----

    /**
     * Advances every pool of one updated entity by one active tick, in registration order: progression with its
     * arrivals, then the lifetime.
     */
    public void tick(EntityLivingBase entity, EntityState state) {
        for (EntityState.PoolSlot slot : state.pools()) {
            DefinitionRegistry.PoolEntry entry = registry.pool(slot.id);
            if (entry == null || state.pool(slot.id) != slot.state) {
                continue;
            }
            PoolDefinition def = entry.definition();
            PoolState pool = slot.state;
            double before = pool.amount();
            double capacity = pool.capacity();
            boolean reached = PoolMath.advance(def, pool, 1);
            double after = pool.amount();
            if (after != before) {
                changed.accept(entity, state);
            }
            boolean empty = PoolMath.becameEmpty(before, after);
            boolean full = PoolMath.becameFull(before, capacity, after, capacity);
            if (empty || full || reached) {
                logChange(entity, slot.id, Numbers.format(before), Numbers.format(after), CAUSE_PROGRESSION, null);
                PoolContext context = new PoolContext(entity, slot.id, before, after, capacity, null);
                callbacks.batch(() -> arrivals(def, slot.id, context, empty, full, reached));
            }
            if (state.pool(slot.id) == pool && PoolMath.expired(pool)) {
                end(entity, state, def, slot.id, pool, StatusOperations.CAUSE_TIMER, RemovalReason.EXPIRED);
            }
        }
    }

    /**
     * Ends, with reason {@code death}, every pool of an entity that just died, except on a player the pools whose
     * death rule is {@code keep}.
     */
    public void endOnDeath(EntityLivingBase entity, EntityState state, boolean player) {
        for (EntityState.PoolSlot slot : state.pools()) {
            DefinitionRegistry.PoolEntry entry = registry.pool(slot.id);
            if (entry == null || state.pool(slot.id) != slot.state) {
                continue;
            }
            if (!DeathRules.survivesDeath(entry.definition().deathRule(), player)) {
                end(entity, state, entry.definition(), slot.id, slot.state, StatusOperations.CAUSE_DEATH,
                        RemovalReason.DEATH);
            }
        }
    }

    /** Removes the instance, logs the end with its reason, and runs the removed callback. */
    private void end(EntityLivingBase entity, EntityState state, PoolDefinition def, String id, PoolState pool,
                     String cause, RemovalReason reason) {
        double before = pool.amount();
        state.removePool(id);
        logChange(entity, id, Numbers.format(before), "0", cause, reason.text());
        changed.accept(entity, state);
        if (def.onRemoved() != null) {
            PoolContext context = new PoolContext(entity, id, before, 0, pool.capacity(), reason.text());
            callbacks.batch(() -> callbacks.run(KEY_PREFIX + id, "removed", () -> def.onRemoved().onRemoved(context)));
        }
    }

    private void arrivals(PoolDefinition def, String id, PoolContext context, boolean empty, boolean full,
                          boolean reached) {
        if (empty && def.onEmpty() != null) {
            callbacks.run(KEY_PREFIX + id, "empty", () -> def.onEmpty().onEmpty(context));
        }
        if (full && def.onFull() != null) {
            callbacks.run(KEY_PREFIX + id, "full", () -> def.onFull().onFull(context));
        }
        if (reached && def.onTargetReached() != null) {
            callbacks.run(KEY_PREFIX + id, "target_reached", () -> def.onTargetReached().onTargetReached(context));
        }
    }

    private String resolve(String cause) {
        return callbacks.inCallback() && StatusOperations.CAUSE_OPERATION.equals(cause)
                ? StatusOperations.CAUSE_CALLBACK : cause;
    }

    private void logChange(EntityLivingBase entity, String id, String before, String after, String cause,
                           @Nullable String reason) {
        if (log.isEnabled()) {
            log.change(id, StatusOperations.entityKeys(entity), StatusOperations.entityLabel(entity), "pool", before,
                    after, cause, reason);
        }
    }
}
