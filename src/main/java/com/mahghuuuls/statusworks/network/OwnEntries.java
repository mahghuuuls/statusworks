package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.mark.MarkView;
import com.mahghuuuls.statusworks.core.rule.CooldownRules;
import com.mahghuuuls.statusworks.core.rule.PoolMath;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import com.mahghuuuls.statusworks.core.state.EntityState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the entries one player sees from its tracked state and its marks: one entry per instance and per mark that
 * has a row in the presentation table. A {@code per_source} status is one entry with the total count and the
 * soonest timer, with each source's count and time for the detail view. Pool amounts are rounded to the row's
 * decimals here, so a change the player cannot see is not a change.
 */
public final class OwnEntries {

    private OwnEntries() {
    }

    /**
     * @param paused     whether the player is not being updated now (dead): its own timers stand still, so its own
     *                   entries are sent inactive and the client does not count them down
     * @param sourceName names a status source by UUID (a loaded entity's name, or a short form of the UUID)
     */
    public static Map<String, HudEntry> build(PresentationTable table, DefinitionRegistry registry, EntityState state,
                                              List<MarkView> marks, boolean paused,
                                              Function<UUID, String> sourceName) {
        boolean running = !paused;
        Map<String, HudEntry> out = new LinkedHashMap<>();
        for (EntityState.StatusSlot slot : state.statuses()) {
            HudDefinition row = table.status(slot.id);
            DefinitionRegistry.Entry entry = registry.status(slot.id);
            if (row == null || entry == null || slot.state.total() <= 0) {
                continue;
            }
            StatusDefinition def = entry.definition();
            List<HudEntry.Group> groups = Collections.emptyList();
            if (def.ownership() == Ownership.PER_SOURCE) {
                groups = new ArrayList<>();
                for (Map.Entry<UUID, StatusState.Group> g : slot.state.groups().entrySet()) {
                    String name = StatusRules.NO_SOURCE.equals(g.getKey()) ? "" : sourceName.apply(g.getKey());
                    groups.add(new HudEntry.Group(name, g.getValue().count(),
                            StatusRules.remaining(def, slot.state, g.getKey())));
                }
            }
            put(out, new HudEntry(key(row), row.index, HudEntry.OWN, slot.state.total(), 0, 0,
                    StatusRules.remaining(def, slot.state, null), running, null, groups));
        }
        for (EntityState.PoolSlot slot : state.pools()) {
            HudDefinition row = table.pool(slot.id);
            if (row == null) {
                continue;
            }
            put(out, new HudEntry(key(row), row.index, HudEntry.OWN, 0, round(slot.state.amount(), row.decimals),
                    slot.state.capacity(), PoolMath.remainingLifetime(slot.state), running, null,
                    Collections.<HudEntry.Group>emptyList()));
        }
        for (MarkView m : marks) {
            HudDefinition row = table.mark(m.id, m.asSource);
            if (row == null) {
                continue;
            }
            byte side = m.asSource ? HudEntry.MARK_SOURCE : HudEntry.MARK_TARGET;
            put(out, new HudEntry(key(row) + (m.asSource ? "s:" : "t:") + m.other, row.index, side, 0, 0, 0,
                    m.remaining, m.active, m.otherName, Collections.<HudEntry.Group>emptyList()));
        }
        for (EntityState.CooldownSlot slot : state.cooldowns()) {
            HudDefinition row = table.cooldown(slot.id);
            if (row == null || !CooldownRules.running(slot.state)) {
                continue;
            }
            put(out, new HudEntry(key(row), row.index, HudEntry.OWN, 0, 0, 0, CooldownRules.remaining(slot.state),
                    running, null, Collections.<HudEntry.Group>emptyList()));
        }
        return out;
    }

    private static String key(HudDefinition row) {
        return row.index + "|";
    }

    private static void put(Map<String, HudEntry> out, HudEntry e) {
        out.put(e.key, e);
    }

    /** An amount rounded to 0, 1, or 2 decimals. */
    static double round(double amount, int decimals) {
        double scale = decimals <= 0 ? 1 : decimals == 1 ? 10 : 100;
        return Math.round(amount * scale) / scale;
    }
}
