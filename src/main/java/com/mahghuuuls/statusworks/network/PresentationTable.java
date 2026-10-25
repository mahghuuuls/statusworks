package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The presentation table of the server's valid definitions: one row per definition a player may see, in
 * registration order (statuses, pools, marks, cooldowns). The one place that decides what a client may know about a
 * definition: an author-hidden definition, and a mark shown on neither side, get no row, so none of their entries
 * can be built or sent.
 */
public final class PresentationTable {

    private final List<HudDefinition> rows = new ArrayList<>();
    private final Map<String, HudDefinition> statuses = new HashMap<>();
    private final Map<String, HudDefinition> pools = new HashMap<>();
    private final Map<String, HudDefinition> marks = new HashMap<>();
    private final Map<String, HudDefinition> cooldowns = new HashMap<>();
    private final Map<String, Presentation> markPresentations = new HashMap<>();

    public static PresentationTable build(DefinitionRegistry registry) {
        PresentationTable t = new PresentationTable();
        for (DefinitionRegistry.Entry e : registry.statuses()) {
            StatusDefinition d = e.definition();
            t.add(t.statuses, d.id(), HudDefinition.STATUS, d.presentation(), d.limit(), d.expiration().text());
        }
        for (DefinitionRegistry.PoolEntry e : registry.pools()) {
            PoolDefinition d = e.definition();
            t.add(t.pools, d.id(), HudDefinition.POOL, d.presentation(), 1, "lifetime");
        }
        for (DefinitionRegistry.MarkEntry e : registry.marks()) {
            MarkDefinition d = e.definition();
            Presentation p = d.presentation();
            if (p.markOnSource() || p.markOnTarget()) {
                t.add(t.marks, d.id(), HudDefinition.MARK, p, 1, "mark");
                t.markPresentations.put(d.id(), p);
            }
        }
        for (DefinitionRegistry.CooldownEntry e : registry.cooldowns()) {
            CooldownDefinition d = e.definition();
            t.add(t.cooldowns, d.id(), HudDefinition.COOLDOWN, d.presentation(), 1, "cooldown");
        }
        return t;
    }

    private void add(Map<String, HudDefinition> byId, String id, byte kind, Presentation p, int limit,
                     String timerMode) {
        if (p.hidden()) {
            return;
        }
        // Without a name, the id's path: "hp10" rather than "mymod:hp10".
        String name = p.name() != null ? p.name() : id.substring(id.indexOf(':') + 1);
        HudDefinition row = new HudDefinition(rows.size(), id, kind, name, p.name() != null && p.nameIsKey(),
                p.description(), p.descriptionIsKey(), p.iconItem(), p.iconMeta(), p.iconTexture(), p.showCount(),
                p.showAmount(), p.showTime(), p.decimals(), p.priority(), limit, timerMode);
        rows.add(row);
        byId.put(id, row);
    }

    public List<HudDefinition> rows() {
        return Collections.unmodifiableList(rows);
    }

    @Nullable
    HudDefinition status(String id) {
        return statuses.get(id);
    }

    @Nullable
    HudDefinition pool(String id) {
        return pools.get(id);
    }

    @Nullable
    HudDefinition cooldown(String id) {
        return cooldowns.get(id);
    }

    /** The row of a mark the viewer sees from this side, or null when the definition does not show there. */
    @Nullable
    HudDefinition mark(String id, boolean asSource) {
        Presentation p = markPresentations.get(id);
        if (p == null || !(asSource ? p.markOnSource() : p.markOnTarget())) {
            return null;
        }
        return marks.get(id);
    }

    @Nullable
    HudDefinition row(int index) {
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }
}
