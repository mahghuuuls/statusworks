package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.api.StatusSnapshot;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds a {@link StatusSnapshot} from an entity's status slots: the one place that decides which statuses a snapshot
 * lists and how its rows and per-source groups are made. It only reads; the total is the instance's own total and
 * the times and group counts come from {@link StatusRules}, as for the per-id queries; the author's hidden flag
 * ({@link Presentation#hidden()}) leaves a status out. A definition without a name gets the id's path, as on the
 * HUD.
 */
public final class StatusSnapshots {

    private StatusSnapshots() {
    }

    /**
     * @param slots       the entity's status instances, in registration order
     * @param definitions the registered definition for an id, or null when it is no longer registered
     */
    public static StatusSnapshot build(List<EntityState.StatusSlot> slots,
                                       Function<String, StatusDefinition> definitions) {
        List<StatusSnapshot.Entry> entries = new ArrayList<>(slots.size());
        for (EntityState.StatusSlot slot : slots) {
            StatusDefinition def = definitions.apply(slot.id);
            StatusState state = slot.state;
            if (def == null || state == null || state.isEmpty()) {
                continue;
            }
            Presentation p = def.presentation();
            if (p.hidden()) {
                continue;
            }
            // As the presentation table names a row: without a name, the id's path, which is never a key.
            String name = p.name() != null ? p.name() : slot.id.substring(slot.id.indexOf(':') + 1);
            entries.add(new StatusSnapshot.Entry(slot.id, name, p.name() != null && p.nameIsKey(), p.iconItem(),
                    p.iconMeta(),
                    p.iconTexture(), state.total(), StatusRules.remaining(def, state, null), p.harmful(),
                    p.priority(), p.showCount(), p.showTime(), groups(def, state)));
        }
        return entries.isEmpty() ? StatusSnapshot.EMPTY : new StatusSnapshot(entries);
    }

    private static List<StatusSnapshot.Group> groups(StatusDefinition def, StatusState state) {
        if (def.ownership() != Ownership.PER_SOURCE) {
            return Collections.emptyList();
        }
        List<StatusSnapshot.Group> out = new ArrayList<>(state.groups().size());
        for (Map.Entry<UUID, StatusState.Group> e : state.groups().entrySet()) {
            UUID source = e.getKey();
            out.add(new StatusSnapshot.Group(source, StatusRules.count(state, source),
                    StatusRules.remaining(def, state, source)));
        }
        return out;
    }
}
