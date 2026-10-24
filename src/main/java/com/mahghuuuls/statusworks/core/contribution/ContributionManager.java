package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Numbers;
import com.mahghuuuls.statusworks.core.state.EntityState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one owner of managed contributions. It computes the contributions an entity should have from its status
 * and pool instances and its active marks (cooldowns never contribute), compares them with the entity's {@link ContributionRecord},
 * and applies only the difference: attribute modifiers with a UUID derived from the contribution key and never
 * saved by the attribute map, and value and end notices to addon contribution types.
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
    private MarkContributions marks = MarkContributions.NONE;

    public ContributionManager(DefinitionRegistry registry, ContributionTypeRegistry types, CallbackRunner callbacks,
                               DiagnosticLog log) {
        this.registry = registry;
        this.types = types;
        this.callbacks = callbacks;
        this.log = log;
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
        double oldMax = entity.getMaxHealth();
        for (String key : plan.remove) {
            Applied old = applied.get(key);
            withdraw(entity, key, old);
            record.remove(key);
            logChange(entity, key, Numbers.format(old.value), "none");
        }
        for (String key : plan.apply) {
            Applied want = desired.get(key);
            Applied old = record.entries().get(key);
            if (put(entity, key, want, true)) {
                record.put(key, want);
                logChange(entity, key, old == null ? "none" : Numbers.format(old.value), Numbers.format(want.value));
            } else {
                record.remove(key);
            }
        }
        float health = HealthPolicy.afterMaxChange(entity.getHealth(), oldMax, entity.getMaxHealth());
        if (health != entity.getHealth()) {
            entity.setHealth(health);
        }
    }

    /**
     * Reapplies the record's attribute modifiers while the entity's capability loads, before vanilla reads
     * attributes and health. Contribution types are told when the entity joins a world.
     */
    public void restore(EntityLivingBase entity, EntityState state) {
        for (Map.Entry<String, Applied> e : state.contributions().entries().entrySet()) {
            if (e.getValue().attribute) {
                put(entity, e.getKey(), e.getValue(), false);
            }
        }
    }

    /**
     * When the entity joins a world: tells contribution types the restored values, then reconciles (definitions
     * may have changed since the save, and an attribute another mod registers late may exist now).
     */
    public void onJoin(EntityLivingBase entity, EntityState state) {
        for (Map.Entry<String, Applied> e : state.contributions().entries().entrySet()) {
            Applied a = e.getValue();
            if (!a.attribute && appliesTo(a.target, entity)) {
                notifyValue(entity, e.getKey(), a);
            }
        }
        reconcile(entity, state, true);
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

    /** Applies one contribution; returns false when the entity does not have the attribute (yet). */
    private boolean put(EntityLivingBase entity, String key, Applied applied, boolean notify) {
        if (!applied.attribute) {
            if (notify) {
                notifyValue(entity, key, applied);
            }
            return true;
        }
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

    private void withdraw(EntityLivingBase entity, String key, Applied applied) {
        if (applied.attribute) {
            IAttributeInstance instance = entity.getAttributeMap().getAttributeInstanceByName(applied.target);
            if (instance != null) {
                instance.removeModifier(ContributionValues.modifierId(key));
            }
            return;
        }
        ContributionType type = types.type(applied.target);
        if (type != null) {
            callbacks.run(TYPE_PREFIX + applied.target, "end", () -> type.onEnd(entity, key));
        }
    }

    private void notifyValue(EntityLivingBase entity, String key, Applied applied) {
        ContributionType type = types.type(applied.target);
        if (type != null) {
            double value = applied.value;
            callbacks.run(TYPE_PREFIX + applied.target, "value", () -> type.onValue(entity, key, value));
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

    private void logChange(EntityLivingBase entity, String key, String before, String after) {
        if (log.isEnabled()) {
            String id = key.substring(key.indexOf(':') + 1, key.lastIndexOf('#'));
            log.change(id, new String[]{entity.getName(), Integer.toString(entity.getEntityId()),
                            entity.getUniqueID().toString()}, entity.getName() + "#" + entity.getEntityId(),
                    "contribution " + key, before, after, "reconcile", null);
        }
    }
}
