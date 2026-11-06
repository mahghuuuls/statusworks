package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.api.client.ClientEntry;
import com.mahghuuuls.statusworks.api.client.StatusworksClientView;
import com.mahghuuuls.statusworks.network.HudDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The addon view over the client store: copies of what the server sent, nothing more. */
final class ClientStatusworksView implements StatusworksClientView {

    private final ClientStore store;

    ClientStatusworksView(ClientStore store) {
        this.store = store;
    }

    @Override
    public List<ClientEntry> ownEntries() {
        return copy(store.allOwn());
    }

    @Override
    public Map<Integer, List<ClientEntry>> nearbyEntries() {
        Map<Integer, List<ClientEntry>> out = new HashMap<>();
        for (Map.Entry<Integer, List<ClientStore.Shown>> e : store.audience().entrySet()) {
            out.put(e.getKey(), copy(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<ClientEntry> copy(List<ClientStore.Shown> shown) {
        List<ClientEntry> out = new ArrayList<>(shown.size());
        for (ClientStore.Shown s : shown) {
            HudDefinition row = s.row;
            out.add(new ClientEntry(row.id, kind(row.kind), row.name, row.nameIsKey, s.entry.count, s.entry.amount,
                    s.entry.capacity, s.remaining, s.entry.active, s.entry.other, row.particleType != null,
                    HudGroups.harmful(s)));
        }
        return Collections.unmodifiableList(out);
    }

    private static String kind(byte kind) {
        switch (kind) {
            case HudDefinition.STATUS:
                return "status";
            case HudDefinition.POOL:
                return "pool";
            case HudDefinition.MARK:
                return "mark";
            default:
                return "cooldown";
        }
    }
}
