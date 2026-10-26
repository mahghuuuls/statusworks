package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.ClientReceiver;
import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What this client knows: the server's presentation table, the player's own entries, and the entries of other
 * entities it may see because they have particles, each with the client tick it arrived on, for the countdown. It
 * knows definitions only from the server's table, never from its own scripts. Filled from the network thread
 * through the main thread; read only on the main thread.
 */
public final class ClientStore implements ClientReceiver {

    /** One entry as received, with the client tick it arrived on. */
    static final class Received {
        final HudEntry entry;
        final long tick;

        Received(HudEntry entry, long tick) {
            this.entry = entry;
            this.tick = tick;
        }
    }

    /** One entry ready to draw: its table row, its data, and its remaining time now. */
    static final class Shown {
        final HudDefinition row;
        final HudEntry entry;
        final int remaining;
        final List<Integer> groupRemaining;
        /** For one of the player's own potion effects: the effect, for its icon and level; otherwise null. */
        @Nullable final PotionEffect potion;

        Shown(HudDefinition row, HudEntry entry, int remaining, List<Integer> groupRemaining) {
            this(row, entry, remaining, groupRemaining, null);
        }

        Shown(HudDefinition row, HudEntry entry, int remaining, List<Integer> groupRemaining,
              @Nullable PotionEffect potion) {
            this.row = row;
            this.entry = entry;
            this.remaining = remaining;
            this.groupRemaining = groupRemaining;
            this.potion = potion;
        }
    }

    private final ClientSettings settings;
    private final Logger logger;
    private final Map<Integer, HudDefinition> table = new HashMap<>();
    private final Map<String, Received> entries = new LinkedHashMap<>();
    /** Entries of other entities by network entity id. */
    private final Map<Integer, Map<String, Received>> audience = new HashMap<>();
    private long clientTicks;

    ClientStore(ClientSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public void onTable(List<HudDefinition> rows) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            table.clear();
            entries.clear();
            audience.clear();
            for (HudDefinition row : rows) {
                table.put(row.index, row);
            }
            // A join is when a player expects edited settings to apply.
            settings.load();
            logger.debug("Statusworks: received the presentation table, {} rows", rows.size());
        });
    }

    @Override
    public void onEntries(boolean snapshot, List<HudEntry> changed, List<String> removed) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (snapshot) {
                entries.clear();
                // An own snapshot comes at login, respawn, and dimension change: the server starts the other
                // entities over too.
                audience.clear();
            }
            for (String key : removed) {
                entries.remove(key);
            }
            for (HudEntry e : changed) {
                entries.put(e.key, new Received(e, clientTicks));
            }
            logger.debug("Statusworks: received entries snapshot={} changed={} removed={} now={}", snapshot,
                    changed.size(), removed.size(), entries.size());
        });
    }

    @Override
    public void onAudience(int entity, boolean snapshot, List<HudEntry> changed, List<String> removed) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Map<String, Received> forEntity = snapshot ? new LinkedHashMap<>()
                    : audience.computeIfAbsent(entity, k -> new LinkedHashMap<>());
            for (String key : removed) {
                forEntity.remove(key);
            }
            for (HudEntry e : changed) {
                forEntity.put(e.key, new Received(e, clientTicks));
            }
            if (forEntity.isEmpty()) {
                audience.remove(entity);
            } else {
                audience.put(entity, forEntity);
            }
        });
    }

    @Override
    public void onAudienceRemove(int entity) {
        Minecraft.getMinecraft().addScheduledTask(() -> audience.remove(entity));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.END && mc.world != null && !mc.isGamePaused()) {
            clientTicks++;
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            table.clear();
            entries.clear();
            audience.clear();
        });
    }

    /** The entries to draw, without the ones the player hid, by author priority and then registration order. */
    List<Shown> shown() {
        return shown(entries.values(), true);
    }

    /** Every own entry, also the ones the player hid on its HUD, in the same order. */
    List<Shown> allOwn() {
        return shown(entries.values(), false);
    }

    /** The entries of other entities this client may see, by network entity id, in the same order. */
    Map<Integer, List<Shown>> audience() {
        Map<Integer, List<Shown>> out = new HashMap<>();
        for (Map.Entry<Integer, Map<String, Received>> e : audience.entrySet()) {
            out.put(e.getKey(), shown(e.getValue().values(), false));
        }
        return out;
    }

    private List<Shown> shown(Iterable<Received> received, boolean withoutHidden) {
        List<Shown> out = new ArrayList<>();
        for (Received r : received) {
            HudDefinition row = table.get(r.entry.definition);
            if (row == null || (withoutHidden && settings.hidden.contains(row.id))) {
                continue;
            }
            long elapsed = clientTicks - r.tick;
            List<Integer> groups = new ArrayList<>(r.entry.groups.size());
            for (HudEntry.Group g : r.entry.groups) {
                groups.add(HudText.remainingNow(r.entry, g.remaining, elapsed));
            }
            out.add(new Shown(row, r.entry, HudText.remainingNow(r.entry, r.entry.remaining, elapsed), groups));
        }
        out.sort(Comparator.<Shown>comparingInt(s -> s.row.priority).thenComparingInt(s -> s.row.index)
                .thenComparing(s -> s.entry.key));
        return out;
    }

    @Nullable
    HudDefinition row(int index) {
        return table.get(index);
    }
}
