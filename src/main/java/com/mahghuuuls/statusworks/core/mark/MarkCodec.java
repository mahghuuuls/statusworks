package com.mahghuuuls.statusworks.core.mark;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The save format of the mark store, version {@value #VERSION}: the creation order counter and one compound per
 * mark (definition id, participant UUIDs and names, order, age, end age, inactive ticks, active flag). A mark
 * entry that cannot be read is dropped with a warning; the others load. Data written by a newer version is not
 * read: the caller keeps it unchanged.
 */
final class MarkCodec {

    static final int VERSION = 1;

    private MarkCodec() {
    }

    /** Whether the data was written by a newer save format that this version cannot read. */
    static boolean isNewer(NBTTagCompound nbt) {
        return nbt.getInteger("v") > VERSION;
    }

    static NBTTagCompound write(MarkRecords records) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("v", VERSION);
        nbt.setLong("next", records.nextOrder());
        NBTTagList list = new NBTTagList();
        for (MarkRecord r : records.all()) {
            NBTTagCompound m = new NBTTagCompound();
            m.setString("id", r.id);
            m.setUniqueId("s", r.source);
            m.setUniqueId("t", r.target);
            m.setString("sn", r.sourceName);
            m.setString("tn", r.targetName);
            m.setLong("o", r.order);
            m.setLong("a", r.age);
            m.setLong("e", r.endAge);
            m.setInteger("i", r.inactiveTicks);
            m.setBoolean("act", r.active);
            list.appendTag(m);
        }
        nbt.setTag("marks", list);
        return nbt;
    }

    /** Reads marks into {@code into}, in creation order; {@code warn} hears about each dropped entry. */
    static void read(NBTTagCompound nbt, MarkRecords into, Consumer<String> warn) {
        List<MarkRecord> loaded = new ArrayList<>();
        NBTTagList list = nbt.getTagList("marks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound m = list.getCompoundTagAt(i);
            if (!m.hasKey("id", 8) || !m.hasUniqueId("s") || !m.hasUniqueId("t") || !m.hasKey("o", 4)) {
                warn.accept("mark entry " + i + " is incomplete and was dropped");
                continue;
            }
            UUID source = m.getUniqueId("s");
            UUID target = m.getUniqueId("t");
            MarkRecord r = new MarkRecord(m.getString("id"), source, target, m.getLong("o"),
                    nameOr(m, "sn", source), nameOr(m, "tn", target));
            r.age = Math.max(0, m.getLong("a"));
            r.endAge = m.hasKey("e", 4) ? m.getLong("e") : -1;
            r.inactiveTicks = Math.max(0, m.getInteger("i"));
            r.active = m.getBoolean("act");
            loaded.add(r);
        }
        loaded.sort(Comparator.comparingLong(r -> r.order));
        for (MarkRecord r : loaded) {
            if (into.find(r.id, r.source, r.target) != null) {
                warn.accept("duplicate mark " + r + " was dropped");
                continue;
            }
            into.add(r);
        }
        into.setNextOrder(Math.max(into.nextOrder(), nbt.getLong("next")));
    }

    private static String nameOr(NBTTagCompound m, String key, UUID fallback) {
        NBTBase tag = m.getTag(key);
        return tag == null ? fallback.toString() : m.getString(key);
    }
}
