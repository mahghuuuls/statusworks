package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
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
 * The save format of {@link EntityState}, owned in one place. Version 1:
 *
 * <pre>
 * { v: 1, statuses: [ { id, age, groups: [ { srcMost, srcLeast, count, deadline, last,
 *                                            stacks: [ { at, n } ] } ] } ] }
 * </pre>
 *
 * <p>Reading restores instances as they were: no callback runs and nothing counts as a new application. An
 * instance whose definition is gone or disabled ends with reason {@code invalid}; one whose definition changed
 * is fitted by {@link StatusRules#fit}. Both leave a {@link EntityState.LoadNote} for the diagnostic log.</p>
 */
public final class EntityStateCodec {

    public static final int VERSION = 1;

    private final DefinitionRegistry registry;
    /** Told about saved state that loading ended or fitted: (definition id, message for the server log). */
    private final BiConsumer<String, String> warn;

    public EntityStateCodec(DefinitionRegistry registry, BiConsumer<String, String> warn) {
        this.registry = registry;
        this.warn = warn;
    }

    public NBTTagCompound write(EntityState state) {
        if (state.isEmpty() && state.newerFormat() != null) {
            return state.newerFormat();
        }
        NBTTagCompound root = new NBTTagCompound();
        if (state.isEmpty()) {
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
                state.addLoadNote(new EntityState.LoadNote(id, saved, 0, RemovalReason.INVALID.text()));
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
                state.addLoadNote(new EntityState.LoadNote(id, saved, status.total(), null));
            }
            if (status.isEmpty()) {
                state.removeStatus(id);
            }
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
