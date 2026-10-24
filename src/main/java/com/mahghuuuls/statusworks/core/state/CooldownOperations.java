package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.api.CooldownChange;
import com.mahghuuuls.statusworks.api.CooldownService;
import com.mahghuuuls.statusworks.api.callback.CooldownContext;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.rule.CooldownRules;
import com.mahghuuuls.statusworks.core.rule.CooldownState;
import com.mahghuuuls.statusworks.core.rule.DeathRules;
import com.mahghuuuls.statusworks.core.rule.RemovalReason;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Cooldown operations and queries on entities. Checks the call, asks {@link CooldownRules} for every timer
 * decision, keeps the running cooldowns in {@link EntityState} up to date, writes the diagnostic log, and runs the
 * "ended" callback through the {@link CallbackRunner}. A cooldown that is not running has no instance.
 */
public final class CooldownOperations implements CooldownService {

    /** Callback keys of cooldowns carry this prefix, so kinds with the same id are counted apart. */
    static final String KEY_PREFIX = "cooldown:";

    private final DefinitionRegistry registry;
    private final DiagnosticLog log;
    private final Warnings warnings;
    private final CallbackRunner callbacks;
    private final Consumer<EntityLivingBase> onStateAdded;

    public CooldownOperations(DefinitionRegistry registry, DiagnosticLog log, Warnings warnings,
                              CallbackRunner callbacks, Consumer<EntityLivingBase> onStateAdded) {
        this.registry = registry;
        this.log = log;
        this.warnings = warnings;
        this.callbacks = callbacks;
        this.onStateAdded = onStateAdded;
    }

    private static final class Target {
        final EntityLivingBase entity;
        final EntityState state;
        final DefinitionRegistry.CooldownEntry entry;

        Target(EntityLivingBase entity, EntityState state, DefinitionRegistry.CooldownEntry entry) {
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
        DefinitionRegistry.CooldownEntry entry = registry.cooldown(id);
        if (entry == null) {
            String why = registry.isInvalidCooldown(id)
                    ? "cooldown " + id + " is disabled because its definition is invalid"
                    : "no cooldown is registered with id " + id;
            warnings.once("cooldown:" + id, "Statusworks: " + why);
            return why;
        }
        out[0] = new Target(target.entity, target.state, entry);
        return null;
    }

    @Override
    public CooldownChange start(Entity entity, String id) {
        return start(entity, id, 0, StatusOperations.CAUSE_OPERATION);
    }

    @Override
    public CooldownChange start(Entity entity, String id, int ticks) {
        return start(entity, id, ticks, StatusOperations.CAUSE_OPERATION);
    }

    /** Starts the cooldown unless it is running; a running one keeps its time. */
    public CooldownChange start(Entity entity, String id, int ticks, String cause) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return CooldownChange.failed(failure);
        }
        Target t = out[0];
        CooldownState running = t.state.cooldown(id);
        CooldownState started = CooldownRules.startIfReady(running,
                CooldownRules.duration(t.entry.definition(), ticks));
        if (started == null) {
            int left = CooldownRules.remaining(running);
            return CooldownChange.of(left, left);
        }
        return begin(t, id, started, cause);
    }

    @Override
    public boolean startIfReady(Entity entity, String id) {
        return startIfReady(entity, id, 0, StatusOperations.CAUSE_OPERATION);
    }

    @Override
    public boolean startIfReady(Entity entity, String id, int ticks) {
        return startIfReady(entity, id, ticks, StatusOperations.CAUSE_OPERATION);
    }

    public boolean startIfReady(Entity entity, String id, int ticks, String cause) {
        Target[] out = new Target[1];
        if (failureOf(entity, id, true, out) != null) {
            return false;
        }
        Target t = out[0];
        CooldownState started = CooldownRules.startIfReady(t.state.cooldown(id),
                CooldownRules.duration(t.entry.definition(), ticks));
        if (started == null) {
            return false;
        }
        begin(t, id, started, cause);
        return true;
    }

    private CooldownChange begin(Target t, String id, CooldownState started, String cause) {
        boolean wasEmpty = t.state.isEmpty();
        t.state.putCooldown(id, t.entry.order(), started);
        int ticks = CooldownRules.remaining(started);
        logChange(t.entity, id, 0, ticks, resolve(cause), null);
        if (wasEmpty) {
            onStateAdded.accept(t.entity);
        }
        return CooldownChange.of(0, ticks);
    }

    @Override
    public CooldownChange restart(Entity entity, String id, int ticks) {
        return restart(entity, id, ticks, StatusOperations.CAUSE_OPERATION);
    }

    /** Sets a new remaining time, starting the cooldown when it is not running. */
    public CooldownChange restart(Entity entity, String id, int ticks, String cause) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return CooldownChange.failed(failure);
        }
        Target t = out[0];
        CooldownState running = t.state.cooldown(id);
        int before = CooldownRules.remaining(running);
        CooldownState result = CooldownRules.restartOrStart(running,
                CooldownRules.duration(t.entry.definition(), ticks));
        if (result != running) {
            return begin(t, id, result, cause);
        }
        logChange(t.entity, id, before, CooldownRules.remaining(result), resolve(cause), null);
        return CooldownChange.of(before, CooldownRules.remaining(result));
    }

    @Override
    public CooldownChange extend(Entity entity, String id, int ticks) {
        return adjust(entity, id, ticks, false, StatusOperations.CAUSE_OPERATION);
    }

    public CooldownChange extend(Entity entity, String id, int ticks, String cause) {
        return adjust(entity, id, ticks, false, cause);
    }

    @Override
    public CooldownChange reduce(Entity entity, String id, int ticks) {
        return adjust(entity, id, ticks, true, StatusOperations.CAUSE_OPERATION);
    }

    public CooldownChange reduce(Entity entity, String id, int ticks, String cause) {
        return adjust(entity, id, ticks, true, cause);
    }

    private CooldownChange adjust(Entity entity, String id, int ticks, boolean reduce, String cause) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return CooldownChange.failed(failure);
        }
        if (ticks < 1) {
            return CooldownChange.failed("ticks must be 1 or more, got " + ticks);
        }
        Target t = out[0];
        CooldownState running = t.state.cooldown(id);
        if (!CooldownRules.running(running)) {
            return CooldownChange.failed("cooldown " + id + " is not running on " + t.entity.getName());
        }
        int before = CooldownRules.remaining(running);
        String resolved = resolve(cause);
        if (reduce) {
            if (CooldownRules.reduce(running, ticks)) {
                end(t.entity, t.state, t.entry.definition(), id, before, resolved, RemovalReason.REMOVED);
                return CooldownChange.of(before, 0);
            }
        } else {
            CooldownRules.extend(running, ticks);
        }
        logChange(t.entity, id, before, CooldownRules.remaining(running), resolved, null);
        return CooldownChange.of(before, CooldownRules.remaining(running));
    }

    @Override
    public CooldownChange clear(Entity entity, String id) {
        return clear(entity, id, StatusOperations.CAUSE_OPERATION);
    }

    public CooldownChange clear(Entity entity, String id, String cause) {
        Target[] out = new Target[1];
        String failure = failureOf(entity, id, true, out);
        if (failure != null) {
            return CooldownChange.failed(failure);
        }
        Target t = out[0];
        CooldownState running = t.state.cooldown(id);
        if (!CooldownRules.running(running)) {
            return CooldownChange.of(0, 0);
        }
        int before = CooldownRules.remaining(running);
        end(t.entity, t.state, t.entry.definition(), id, before, resolve(cause), RemovalReason.REMOVED);
        return CooldownChange.of(before, 0);
    }

    @Override
    public boolean isRunning(Entity entity, String id) {
        return CooldownRules.running(read(entity, id));
    }

    @Override
    public int remaining(Entity entity, String id) {
        return CooldownRules.remaining(read(entity, id));
    }

    @Nullable
    private CooldownState read(Entity entity, String id) {
        Target[] out = new Target[1];
        return failureOf(entity, id, false, out) != null ? null : out[0].state.cooldown(id);
    }

    /** Advances every running cooldown of one updated entity by one active tick; an expired one ends. */
    public void tick(EntityLivingBase entity, EntityState state) {
        for (EntityState.CooldownSlot slot : state.cooldowns()) {
            DefinitionRegistry.CooldownEntry entry = registry.cooldown(slot.id);
            if (entry == null || state.cooldown(slot.id) != slot.state) {
                continue;
            }
            if (CooldownRules.tick(slot.state)) {
                end(entity, state, entry.definition(), slot.id, 1, StatusOperations.CAUSE_TIMER,
                        RemovalReason.EXPIRED);
            }
        }
    }

    /**
     * Ends, with reason {@code death}, the cooldowns of an entity that just died, except on a player the ones
     * whose death rule is {@code keep} (the default for cooldowns).
     */
    public void endOnDeath(EntityLivingBase entity, EntityState state, boolean player) {
        for (EntityState.CooldownSlot slot : state.cooldowns()) {
            DefinitionRegistry.CooldownEntry entry = registry.cooldown(slot.id);
            if (entry == null || state.cooldown(slot.id) != slot.state) {
                continue;
            }
            if (!DeathRules.survivesDeath(entry.definition().deathRule(), player)) {
                end(entity, state, entry.definition(), slot.id, CooldownRules.remaining(slot.state),
                        StatusOperations.CAUSE_DEATH, RemovalReason.DEATH);
            }
        }
    }

    private void end(EntityLivingBase entity, EntityState state, CooldownDefinition def, String id, int before,
                     String cause, RemovalReason reason) {
        state.removeCooldown(id);
        logChange(entity, id, before, 0, cause, reason.text());
        if (def.onEnded() != null) {
            CooldownContext context = new CooldownContext(entity, id, reason.text());
            callbacks.batch(() -> callbacks.run(KEY_PREFIX + id, "ended", () -> def.onEnded().onEnded(context)));
        }
    }

    private String resolve(String cause) {
        return callbacks.inCallback() && StatusOperations.CAUSE_OPERATION.equals(cause)
                ? StatusOperations.CAUSE_CALLBACK : cause;
    }

    private void logChange(EntityLivingBase entity, String id, int before, int after, String cause,
                           @Nullable String reason) {
        if (log.isEnabled()) {
            log.change(id, StatusOperations.entityKeys(entity), StatusOperations.entityLabel(entity), "cooldown",
                    Integer.toString(before), Integer.toString(after), cause, reason);
        }
    }
}
