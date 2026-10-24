package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.core.contribution.Applied;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.rule.CooldownRules;
import com.mahghuuuls.statusworks.core.rule.CooldownState;
import com.mahghuuuls.statusworks.core.rule.PoolMath;
import com.mahghuuuls.statusworks.core.rule.PoolState;
import com.mahghuuuls.statusworks.core.rule.RemovalReason;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The save format of {@link EntityState}, owned in one place. Version 4 (versions 1 to 3 lacked pools, cooldowns,
 * or the contribution record, and are read the same way):
 *
 * <pre>
 * { v: 3, statuses: [ { id, age, groups: [ { srcMost, srcLeast, count, deadline, last,
 *                                            stacks: [ { at, n } ] } ] } ],
 *         pools: [ { id, amount, cap, capset, age, life, paused, page, deadline, done } ],
 *         cooldowns: [ { id, age, end } ],
 *         contrib: [ { key, attr, target, op, value } ] }
 * </pre>
 *
 * <p>Reading restores instances as they were: no callback runs and nothing counts as a new application. An
 * instance whose definition is gone or disabled ends with reason {@code invalid}; one whose definition changed
 * is fitted by {@link StatusRules#fit} or {@link PoolMath#fit}. Both leave a {@link EntityState.LoadNote} for the
 * diagnostic log. A restored pool fires no boundary callback.</p>
 */
public final class EntityStateCodec {

    public static final int VERSION = 4;

    private final DefinitionRegistry registry;
    /** Told about saved state that loading ended or fitted: (definition id, message for the server log). */
    private final BiConsumer<String, String> warn;

    public EntityStateCodec(DefinitionRegistry registry, BiConsumer<String, String> warn) {
        this.registry = registry;
        this.warn = warn;
    }

    public NBTTagCompound write(EntityState state) {
        if (state.hasNothingToSave() && state.newerFormat() != null) {
            return state.newerFormat();
        }
        NBTTagCompound root = new NBTTagCompound();
        if (state.hasNothingToSave()) {
            return root;
        }
        root.setInteger("v", VERSION);
        NBTTagList statuses = new NBTTagList();
        for (EntityState.StatusSlot slot : state.statuses()) {
            NBTTagCompound s = new NBTTagCompound();
            s.setString("id", slot.id);
            s.setLong("age", slot.state.age());
            NBTTagList groups = new NBTTagList();
            for (Map.Entry<UUID, StatusState.Group> e : slot.state.groups().entrySet()) {
                StatusState.Group g = e.getValue();
                NBTTagCompound gt = new NBTTagCompound();
                gt.setUniqueId("src", e.getKey());
                gt.setInteger("count", g.count());
                gt.setLong("deadline", g.deadline());
                gt.setInteger("last", g.lastDuration());
                NBTTagList stacks = new NBTTagList();
                for (Map.Entry<Long, Integer> st : g.stacks().entrySet()) {
                    NBTTagCompound one = new NBTTagCompound();
                    one.setLong("at", st.getKey());
                    one.setInteger("n", st.getValue());
                    stacks.appendTag(one);
                }
                gt.setTag("stacks", stacks);
                groups.appendTag(gt);
            }
            s.setTag("groups", groups);
            statuses.appendTag(s);
        }
        root.setTag("statuses", statuses);
        NBTTagList pools = new NBTTagList();
        for (EntityState.PoolSlot slot : state.pools()) {
            PoolState p = slot.state;
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", slot.id);
            t.setDouble("amount", p.amount());
            t.setDouble("cap", p.capacity());
            t.setBoolean("capset", p.capacitySet());
            t.setLong("age", p.age());
            t.setLong("life", p.lifetimeEnd());
            t.setBoolean("paused", p.paused());
            t.setLong("page", p.progressAge());
            t.setLong("deadline", p.deadline());
            t.setBoolean("done", p.targetDone());
            pools.appendTag(t);
        }
        root.setTag("pools", pools);
        NBTTagList cooldowns = new NBTTagList();
        for (EntityState.CooldownSlot slot : state.cooldowns()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", slot.id);
            t.setLong("age", slot.state.age());
            t.setLong("end", slot.state.end());
            cooldowns.appendTag(t);
        }
        root.setTag("cooldowns", cooldowns);
        NBTTagList contrib = new NBTTagList();
        for (Map.Entry<String, Applied> e : state.contributions().entries().entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("key", e.getKey());
            t.setBoolean("attr", e.getValue().attribute);
            t.setString("target", e.getValue().target);
            t.setInteger("op", e.getValue().operation);
            t.setDouble("value", e.getValue().value);
            contrib.appendTag(t);
        }
        root.setTag("contrib", contrib);
        return root;
    }

    public void read(EntityState state, NBTTagCompound root) {
        if (!root.hasKey("v")) {
            return;
        }
        int version = root.getInteger("v");
        if (version > VERSION) {
            state.keepNewerFormat(root.copy());
            warn.accept("<all>", "saved state with format version " + version + " is newer than this Statusworks ("
                    + VERSION + "); it is not loaded and is saved back unchanged unless new state is applied");
            return;
        }
        NBTTagList statuses = root.getTagList("statuses", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < statuses.tagCount(); i++) {
            NBTTagCompound s = statuses.getCompoundTagAt(i);
            String id = s.getString("id");
            DefinitionRegistry.Entry entry = registry.status(id);
            if (entry == null) {
                int saved = savedCount(s);
                warn.accept(id, "saved status " + id + " ended with reason " + RemovalReason.INVALID.text() + ": its"
                        + (registry.isInvalid(id) ? " definition is invalid" : " definition is no longer registered"));
                state.addLoadNote(new EntityState.LoadNote("status", id, saved, 0, RemovalReason.INVALID.text()));
                continue;
            }
            StatusState status = state.statusOrCreate(id, entry.order());
            status.restoreAge(s.getLong("age"));
            NBTTagList groups = s.getTagList("groups", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < groups.tagCount(); j++) {
                NBTTagCompound gt = groups.getCompoundTagAt(j);
                StatusState.Group g = status.restoreGroup(gt.getUniqueId("src"));
                g.restore(gt.getInteger("count"), gt.getLong("deadline"), gt.getInteger("last"));
                NBTTagList stacks = gt.getTagList("stacks", Constants.NBT.TAG_COMPOUND);
                for (int k = 0; k < stacks.tagCount(); k++) {
                    NBTTagCompound one = stacks.getCompoundTagAt(k);
                    g.restoreStack(one.getLong("at"), one.getInteger("n"));
                }
            }
            int saved = status.total();
            if (StatusRules.fit(entry.definition(), status)) {
                warn.accept("fit:" + id, "saved status " + id + " was fitted to its changed definition");
                state.addLoadNote(new EntityState.LoadNote("status", id, saved, status.total(), null));
            }
            if (status.isEmpty()) {
                state.removeStatus(id);
            }
        }
        NBTTagList pools = root.getTagList("pools", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < pools.tagCount(); i++) {
            NBTTagCompound t = pools.getCompoundTagAt(i);
            String id = t.getString("id");
            DefinitionRegistry.PoolEntry entry = registry.pool(id);
            double saved = t.getDouble("amount");
            if (entry == null) {
                warn.accept("pool:" + id, "saved pool " + id + " ended with reason " + RemovalReason.INVALID.text()
                        + ": its" + (registry.isInvalidPool(id) ? " definition is invalid"
                        : " definition is no longer registered"));
                state.addLoadNote(new EntityState.LoadNote("pool", id, saved, 0, RemovalReason.INVALID.text()));
                continue;
            }
            PoolState pool = new PoolState();
            pool.restore(saved, t.getDouble("cap"), t.getBoolean("capset"), t.getLong("age"), t.getLong("life"),
                    t.getBoolean("paused"), t.getLong("page"), t.getLong("deadline"), t.getBoolean("done"));
            if (PoolMath.fit(entry.definition(), pool)) {
                warn.accept("poolfit:" + id, "saved pool " + id + " was fitted to its changed definition");
                state.addLoadNote(new EntityState.LoadNote("pool", id, saved, pool.amount(), null));
            }
            state.putPool(id, entry.order(), pool);
        }
        NBTTagList cooldowns = root.getTagList("cooldowns", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cooldowns.tagCount(); i++) {
            NBTTagCompound t = cooldowns.getCompoundTagAt(i);
            String id = t.getString("id");
            CooldownState cooldown = new CooldownState();
            cooldown.restore(t.getLong("age"), t.getLong("end"));
            if (!CooldownRules.running(cooldown)) {
                continue;
            }
            DefinitionRegistry.CooldownEntry entry = registry.cooldown(id);
            if (entry == null) {
                warn.accept("cooldown:" + id, "saved cooldown " + id + " ended with reason "
                        + RemovalReason.INVALID.text() + ": its" + (registry.isInvalidCooldown(id)
                        ? " definition is invalid" : " definition is no longer registered"));
                state.addLoadNote(new EntityState.LoadNote("cooldown", id, CooldownRules.remaining(cooldown), 0,
                        RemovalReason.INVALID.text()));
                continue;
            }
            state.putCooldown(id, entry.order(), cooldown);
        }
        NBTTagList contrib = root.getTagList("contrib", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < contrib.tagCount(); i++) {
            NBTTagCompound t = contrib.getCompoundTagAt(i);
            state.contributions().restore(t.getString("key"), new Applied(t.getBoolean("attr"), t.getString("target"),
                    t.getInteger("op"), t.getDouble("value")));
        }
    }

    private static int savedCount(NBTTagCompound status) {
        int total = 0;
        NBTTagList groups = status.getTagList("groups", Constants.NBT.TAG_COMPOUND);
        for (int j = 0; j < groups.tagCount(); j++) {
            total += groups.getCompoundTagAt(j).getInteger("count");
        }
        return total;
    }
}
