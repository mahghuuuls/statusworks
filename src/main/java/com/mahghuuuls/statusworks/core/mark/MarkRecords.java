package com.mahghuuuls.statusworks.core.mark;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every mark of the server with its two indexes, by source UUID and by target UUID, and the creation order
 * counter. Each index list keeps creation order, so the first mark of a definition in a source's list is its
 * oldest. Plain data; {@link MarkStore} owns every decision.
 */
final class MarkRecords {

    private final LinkedHashSet<MarkRecord> all = new LinkedHashSet<>();
    private final Map<UUID, List<MarkRecord>> bySource = new HashMap<>();
    private final Map<UUID, List<MarkRecord>> byTarget = new HashMap<>();
    private long nextOrder;

    /** A new creation order number, higher than every earlier one. */
    long takeOrder() {
        return nextOrder++;
    }

    long nextOrder() {
        return nextOrder;
    }

    void setNextOrder(long next) {
        this.nextOrder = next;
    }

    /** Adds a record; records are added in creation order, also when loading. */
    void add(MarkRecord record) {
        all.add(record);
        bySource.computeIfAbsent(record.source, k -> new ArrayList<>(2)).add(record);
        byTarget.computeIfAbsent(record.target, k -> new ArrayList<>(2)).add(record);
        if (record.order >= nextOrder) {
            nextOrder = record.order + 1;
        }
    }

    void remove(MarkRecord record) {
        all.remove(record);
        unindex(bySource, record.source, record);
        unindex(byTarget, record.target, record);
    }

    private static void unindex(Map<UUID, List<MarkRecord>> index, UUID key, MarkRecord record) {
        List<MarkRecord> list = index.get(key);
        if (list != null) {
            list.remove(record);
            if (list.isEmpty()) {
                index.remove(key);
            }
        }
    }

    @Nullable
    MarkRecord find(String id, UUID source, UUID target) {
        List<MarkRecord> list = bySource.get(source);
        if (list != null) {
            for (MarkRecord r : list) {
                if (r.id.equals(id) && r.target.equals(target)) {
                    return r;
                }
            }
        }
        return null;
    }

    /** The source's marks of a definition, oldest first. */
    List<MarkRecord> ofSource(UUID source, String id) {
        return filter(bySource.get(source), id);
    }

    /** The marks of a definition on a target, oldest first. */
    List<MarkRecord> ofTarget(UUID target, String id) {
        return filter(byTarget.get(target), id);
    }

    /** Every mark of the source, any definition, oldest first. */
    List<MarkRecord> asSource(UUID source) {
        List<MarkRecord> list = bySource.get(source);
        return list == null ? Collections.emptyList() : list;
    }

    /** Every mark on the target, any definition, oldest first. */
    List<MarkRecord> asTarget(UUID target) {
        List<MarkRecord> list = byTarget.get(target);
        return list == null ? Collections.emptyList() : list;
    }

    /** Every mark the entity takes part in, either side, as a copy. */
    List<MarkRecord> involving(UUID entity) {
        List<MarkRecord> out = new ArrayList<>(asSource(entity));
        out.addAll(asTarget(entity));
        return out;
    }

    boolean participates(UUID entity) {
        return bySource.containsKey(entity) || byTarget.containsKey(entity);
    }

    private static List<MarkRecord> filter(@Nullable List<MarkRecord> list, String id) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<MarkRecord> out = new ArrayList<>(list.size());
        for (MarkRecord r : list) {
            if (r.id.equals(id)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Every mark in creation order, as a copy that is safe to iterate while marks change. */
    List<MarkRecord> all() {
        return new ArrayList<>(all);
    }

    int size() {
        return all.size();
    }

    boolean isEmpty() {
        return all.isEmpty();
    }
}
