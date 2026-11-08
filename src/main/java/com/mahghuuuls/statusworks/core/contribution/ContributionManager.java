package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroupType;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import com.mahghuuuls.statusworks.core.diagnostic.Numbers;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.state.EntityState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one owner of managed contributions. It computes the contributions an entity should have from its status
 * and pool instances and its active marks (cooldowns never contribute), compares them with the entity's {@link ContributionRecord},
 * and applies only the difference: attribute modifiers with a UUID derived from the contribution key and never
 * saved by the attribute map, and value and end notices to addon contribution types.
 *
 * <p>Each pass first commits the record, the attribute modifiers and health, then sends its notices, so a type
 * that reads {@link #currentValues} or changes tracked state from a notice sees the committed record. One pass per
 * entity runs at a time: a reconcile requested from inside a pass (a notice changing state) runs after it, and at
 * most {@value PassSerializer#MAX_PASSES_IN_A_ROW} passes run in a row before the next waits for the next server
 * tick, with one warning.</p>
 *
 * <p>Each pass runs as an update of {@link GroupBoundary}, which sends the group signals of the types that asked
 * for them: the invalidations before the pass changes anything, the settled groups after its notices, and failed
 * groups when Statusworks cannot finish the update.</p>
 *
 * <p>It also owns the health policy (raising maximum health never refills; lowering it below current health
 * brings health down), the pool update step, and the restore order: the record is reapplied while the entity's
 * saved capability loads, before vanilla reads attributes and health, so health above the base maximum survives
 * loading, chunk reloads, and dimension changes; on leaving the End it reapplies, then restores the old player's
 * health.</p>
 */
public final class ContributionManager implements ContributionService {

    private static final String TYPE_PREFIX = "type:";

    private final DefinitionRegistry registry;
    private final ContributionTypeRegistry types;
    private final CallbackRunner callbacks;
    private final DiagnosticLog log;
    private final Warnings warnings;
    private final PassSerializer<EntityLivingBase> passes = new PassSerializer<>(this::passLimitReached);
    private MarkContributions marks = MarkContributions.NONE;
    /** The contribution type whose notice or group signal ran last, named by the pass-limit warning. */
    private String lastNoticeType = "none";

    private final GroupBoundary<EntityLivingBase> groups;

    public ContributionManager(DefinitionRegistry registry, ContributionTypeRegistry types, CallbackRunner callbacks,
                               DiagnosticLog log, Warnings warnings, ErrorCounts errorCounts,
                               BiConsumer<String, Throwable> errorLog) {
        this.registry = registry;
        this.types = types;
        this.callbacks = callbacks;
        this.log = log;
        this.warnings = warnings;
        this.groups = new GroupBoundary<>(new GroupSide(), errorCounts, errorLog, warnings::warn, log::isEnabled,
                log::note);
    }

    /** Connects the mark store, which is built after this manager. */
    public void setMarks(MarkContributions marks) {
        this.marks = marks;
    }

    /** The contributions the entity should have now, by key. */
    Map<String, Applied> desired(EntityLivingBase entity, EntityState state) {
        Map<String, Applied> out = new LinkedHashMap<>(4);
        for (EntityState.StatusSlot slot : state.statuses()) {
            DefinitionRegistry.Entry entry = registry.status(slot.id);
            int total = slot.state.total();
            if (entry == null || total <= 0) {
                continue;
            }
            List<Contribution> list = entry.definition().contributions();
            for (int i = 0; i < list.size(); i++) {
                want(out, entity, ContributionValues.key("status", slot.id, i), list.get(i),
                        ContributionValues.forStatus(list.get(i), total));
            }
        }
        for (EntityState.PoolSlot slot : state.pools()) {
            DefinitionRegistry.PoolEntry entry = registry.pool(slot.id);
            if (entry == null) {
                continue;
            }
            PoolDefinition def = entry.definition();
            List<Contribution> list = def.contributions();
            for (int i = 0; i < list.size(); i++) {
                want(out, entity, ContributionValues.key("pool", slot.id, i), list.get(i), ContributionValues.forPool(
                        list.get(i), slot.state.amount(), slot.state.capacity(), def.updateStep()));
            }
        }
        marks.collect(entity, (key, c) -> want(out, entity, key, c, c.value()));
        return out;
    }

    private void want(Map<String, Applied> out, EntityLivingBase entity, String key, Contribution c, double value) {
        if (c.isAttribute()) {
            out.put(key, new Applied(true, c.target(), ContributionValues.operation(c.operation()), value));
        } else if (appliesTo(c.target(), entity)) {
            out.put(key, new Applied(false, c.target(), -1, value));
        }
    }

    private boolean appliesTo(String typeId, EntityLivingBase entity) {
        ContributionType type = types.type(typeId);
        return type != null && callbacks.ask(TYPE_PREFIX + typeId, "applies_to", () -> type.appliesTo(entity), false);
    }

    /**
     * Brings the entity's contributions in line with its instances. Called after every instance change; a no-op
     * when nothing differs.
     */
    public void reconcile(EntityLivingBase entity, EntityState state) {
        reconcile(entity, state, false);
    }

    /**
     * @param verify also check that every recorded attribute modifier is really on the entity (after a load, an
     *               attribute another mod registers late may have been missing); done when the entity joins
     */
    private void reconcile(EntityLivingBase entity, EntityState state, boolean verify) {
        passes.request(entity, () -> pass(entity, state, verify), () -> pass(entity, state, false));
    }

    /** Called when a server tick starts, before any world ticks: passes deferred until now may run at its end. */
    public void beginServerTick() {
        passes.nextTick();
    }

    /** Runs the passes that waited since an earlier tick because an entity reached the pass limit. */
    public void runDeferredPasses() {
        passes.runDeferred(ContributionManager::stillHere);
    }

    /**
     * One pass, as an update of the group boundary: the group invalidations, then commit the difference (modifiers,
     * record, health), then the notices of that difference, then the settled groups. When Statusworks fails inside,
     * every group of the entity fails and the error goes on to the caller as before.
     */
    private void pass(EntityLivingBase entity, EntityState state, boolean verify) {
        groups.update(entity, () -> commitAndNotify(entity, state, verify));
    }

    private void commitAndNotify(EntityLivingBase entity, EntityState state, boolean verify) {
        ContributionRecord record = state.contributions();
        Map<String, Applied> desired = desired(entity, state);
        if (desired.isEmpty() && record.isEmpty()) {
            return;
        }
        if (!verify && desired.equals(record.entries())) {
            return;
        }
        Map<String, Applied> applied = new LinkedHashMap<>(record.entries());
        ContributionPlan plan = ContributionPlan.diff(desired, applied,
                key -> !verify || present(entity, key, applied.get(key)));
        if (plan.isEmpty()) {
            return;
        }
        groups.commit(entity, touchedTypes(plan, desired, applied), () -> {
            List<Runnable> notices = new ArrayList<>();
            double oldMax = entity.getMaxHealth();
            for (String key : plan.remove) {
                Applied old = applied.get(key);
                if (old.attribute) {
                    removeModifier(entity, key, old);
                } else {
                    notices.add(() -> notifyEnd(entity, key, old));
                }
                record.remove(key);
                logChange(entity, key, Numbers.format(old.value), "none");
            }
            for (String key : plan.apply) {
                Applied want = desired.get(key);
                Applied old = record.entries().get(key);
                if (want.attribute && !applyModifier(entity, key, want)) {
                    record.remove(key);
                    continue;
                }
                record.put(key, want);
                if (!want.attribute) {
                    notices.add(() -> notifyValue(entity, key, want));
                }
                logChange(entity, key, old == null ? "none" : Numbers.format(old.value), Numbers.format(want.value));
            }
            float health = HealthPolicy.afterMaxChange(entity.getHealth(), oldMax, entity.getMaxHealth());
            if (health != entity.getHealth()) {
                entity.setHealth(health);
            }
            for (Runnable notice : notices) {
                notice.run();
            }
        });
    }

    /** The contribution types whose entries the plan removes, adds or changes. */
    private static Set<String> touchedTypes(ContributionPlan plan, Map<String, Applied> desired,
                                            Map<String, Applied> applied) {
        Set<String> touched = new LinkedHashSet<>();
        for (String key : plan.remove) {
            addType(touched, applied.get(key));
        }
        for (String key : plan.apply) {
            addType(touched, desired.get(key));
            addType(touched, applied.get(key));
        }
        return touched;
    }

    private static void addType(Set<String> touched, @Nullable Applied a) {
        if (a != null && !a.attribute) {
            touched.add(a.target);
        }
    }

    private void passLimitReached(EntityLivingBase entity) {
        warnings.once("passes:" + lastNoticeType, "Statusworks: the contributions of " + entity.getName() + "#"
                + entity.getEntityId() + " kept changing from contribution-type notices or group signals (the last"
                + " from "
                + lastNoticeType + "); after " + PassSerializer.MAX_PASSES_IN_A_ROW
                + " passes in a row the next one waits for the next tick");
    }

    /**
     * Reapplies the record's attribute modifiers while the entity's capability loads, before vanilla reads
     * attributes and health. Contribution types are told when the entity joins a world.
     */
    public void restore(EntityLivingBase entity, EntityState state) {
        for (Map.Entry<String, Applied> e : state.contributions().entries().entrySet()) {
            if (e.getValue().attribute) {
                applyModifier(entity, e.getKey(), e.getValue());
            }
        }
    }

    /**
     * When the entity joins a world: tells contribution types the restored values, then reconciles (definitions
     * may have changed since the save, and an attribute another mod registers late may exist now), then sends the
     * join signals of the group types. All of it is one serialized unit: a change made from a replayed notice runs
     * as the next pass, and a join that arrives while the entity's pass runs still runs as a whole afterwards.
     */
    public void onJoin(EntityLivingBase entity, EntityState state) {
        passes.request(entity, () -> groups.join(entity, () -> {
            Map<String, Applied> recorded = new LinkedHashMap<>(state.contributions().entries());
            for (Map.Entry<String, Applied> e : recorded.entrySet()) {
                Applied a = e.getValue();
                if (!a.attribute && appliesTo(a.target, entity)) {
                    notifyValue(entity, e.getKey(), a);
                }
            }
            pass(entity, state, true);
        }), () -> pass(entity, state, false), true);
    }

    /** A player object replaced at a respawn or End exit: its groups end for good. */
    public void retire(EntityLivingBase old) {
        groups.retire(old);
    }

    /** The entity object left its world: its groups are unavailable until it joins again. */
    public void leave(EntityLivingBase entity) {
        groups.leave(entity);
    }

    /** Retries the failed groups of loaded entities once; meant to be called every 20 ticks. */
    public void retryFailedGroups() {
        for (EntityLivingBase entity : groups.withFailures()) {
            EntityState state = EntityState.CAPABILITY == null ? null
                    : entity.getCapability(EntityState.CAPABILITY, null);
            if (state == null || !stillHere(entity)) {
                groups.leave(entity);
                continue;
            }
            passes.request(entity, () -> groups.retry(entity, () -> pass(entity, state, false)),
                    () -> pass(entity, state, false), true);
        }
    }

    private static boolean stillHere(EntityLivingBase entity) {
        return entity.isEntityAlive() && entity.world != null && !entity.world.isRemote
                && net.minecraftforge.common.DimensionManager.getWorld(entity.world.provider.getDimension())
                == entity.world
                && entity.world.getEntityByID(entity.getEntityId()) == entity;
    }

    /**
     * After a player clone copied the kept instances and the record: reapplies the record on the new player,
     * reconciles it with what was kept, and, when leaving the End, restores the old player's health, which
     * vanilla clamped to the base maximum before the contributions were back.
     */
    public void afterClone(EntityLivingBase original, EntityLivingBase clone, EntityState state, boolean wasDeath) {
        restore(clone, state);
        reconcile(clone, state);
        if (!wasDeath) {
            clone.setHealth(original.getHealth());
        }
    }

    private boolean present(EntityLivingBase entity, String key, @Nullable Applied applied) {
        if (applied == null) {
            return false;
        }
        if (!applied.attribute) {
            return true;
        }
        IAttributeInstance instance = entity.getAttributeMap().getAttributeInstanceByName(applied.target);
        return instance != null && instance.getModifier(ContributionValues.modifierId(key)) != null;
    }

    /** Applies one attribute modifier; returns false when the entity does not have the attribute (yet). */
    private boolean applyModifier(EntityLivingBase entity, String key, Applied applied) {
        IAttributeInstance instance = entity.getAttributeMap().getAttributeInstanceByName(applied.target);
        if (instance == null) {
            return false;
        }
        UUID id = ContributionValues.modifierId(key);
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
        instance.applyModifier(new AttributeModifier(id, "statusworks " + key, applied.value, applied.operation)
                .setSaved(false));
        return true;
    }

    private void removeModifier(EntityLivingBase entity, String key, Applied applied) {
        IAttributeInstance instance = entity.getAttributeMap().getAttributeInstanceByName(applied.target);
        if (instance != null) {
            instance.removeModifier(ContributionValues.modifierId(key));
        }
    }

    private void notifyEnd(EntityLivingBase entity, String key, Applied applied) {
        ContributionType type = types.type(applied.target);
        if (type != null) {
            lastNoticeType = applied.target;
            callbacks.deliver(TYPE_PREFIX + applied.target, "end", () -> type.onEnd(entity, key));
        }
    }

    private void notifyValue(EntityLivingBase entity, String key, Applied applied) {
        ContributionType type = types.type(applied.target);
        if (type != null) {
            double value = applied.value;
            lastNoticeType = applied.target;
            callbacks.deliver(TYPE_PREFIX + applied.target, "value", () -> type.onValue(entity, key, value));
        }
    }

    @Override
    public Map<String, Double> currentValues(Entity entity, String typeId) {
        if (!(entity instanceof EntityLivingBase) || entity.world == null || entity.world.isRemote
                || EntityState.CAPABILITY == null) {
            return Collections.emptyMap();
        }
        EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
        if (state == null) {
            return Collections.emptyMap();
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Applied> e : state.contributions().entries().entrySet()) {
            if (!e.getValue().attribute && e.getValue().target.equals(typeId)) {
                out.put(e.getKey(), e.getValue().value);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public ContributionGroup group(Entity entity, String typeId) {
        if (!(entity instanceof EntityLivingBase) || entity.world == null || entity.world.isRemote
                || EntityState.CAPABILITY == null) {
            return ContributionGroup.of(GroupAvailability.UNAVAILABLE);
        }
        return groups.read((EntityLivingBase) entity, typeId);
    }

    /** What {@link GroupBoundary} needs from the game. */
    private final class GroupSide implements GroupBoundary.Side<EntityLivingBase> {

        /** The group types, kept once registration is closed. */
        @Nullable
        private List<String> frozenGroupTypes;

        @Override
        public List<String> groupTypes() {
            if (frozenGroupTypes != null) {
                return frozenGroupTypes;
            }
            List<String> out = new ArrayList<>(2);
            for (String id : types.ids()) {
                if (types.type(id) instanceof ContributionGroupType) {
                    out.add(id);
                }
            }
            if (types.isFrozen()) {
                frozenGroupTypes = Collections.unmodifiableList(out);
            }
            return out;
        }

        @Override
        public boolean applies(EntityLivingBase entity, String typeId) {
            ContributionType type = types.type(typeId);
            return type != null && type.appliesTo(entity);
        }

        @Override
        public boolean invalidate(EntityLivingBase entity, String typeId) {
            ContributionType type = types.type(typeId);
            lastNoticeType = typeId;
            return type instanceof ContributionGroupType && ((ContributionGroupType) type).invalidate(entity);
        }

        @Override
        public void settled(EntityLivingBase entity, String typeId, Map<String, Double> group) {
            ContributionType type = types.type(typeId);
            lastNoticeType = typeId;
            if (type instanceof ContributionGroupType) {
                ((ContributionGroupType) type).settled(entity, group);
            }
        }

        @Override
        public Map<String, Double> committed(EntityLivingBase entity, String typeId) {
            return new LinkedHashMap<>(currentValues(entity, typeId));
        }

        @Override
        @Nullable
        public GroupStates states(EntityLivingBase entity) {
            if (entity.world == null || entity.world.isRemote || EntityState.CAPABILITY == null) {
                return null;
            }
            EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
            return state == null ? null : state.contributionGroups();
        }

        @Override
        public String label(EntityLivingBase entity) {
            return entity.getName() + "#" + entity.getEntityId();
        }
    }

    private void logChange(EntityLivingBase entity, String key, String before, String after) {
        if (log.isEnabled()) {
            String id = key.substring(key.indexOf(':') + 1, key.lastIndexOf('#'));
            log.change(id, new String[]{entity.getName(), Integer.toString(entity.getEntityId()),
                            entity.getUniqueID().toString()}, entity.getName() + "#" + entity.getEntityId(),
                    "contribution " + key, before, after, "reconcile", null);
        }
    }
}
