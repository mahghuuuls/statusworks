package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.mark.MarkStore;
import com.mahghuuuls.statusworks.core.state.EntityState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends each player its own visible entries: the presentation table and a full snapshot at login, a snapshot at
 * respawn and dimension change, and otherwise, once at the end of each server tick, only what changed. The one
 * place that decides what leaves the server for a player's HUD; it sends nothing of an author-hidden definition.
 */
public final class OwnEntriesSender {

    private final DefinitionRegistry registry;
    private final MarkStore marks;
    private final DiagnosticLog log;
    @Nullable private PresentationTable table;
    private final Map<UUID, EntryDiff> sent = new HashMap<>();
    /** Told when a player's entries start over: its client drops everything it had, also other entities'. */
    private java.util.function.Consumer<UUID> startOver = id -> { };
    private long tick;

    public OwnEntriesSender(DefinitionRegistry registry, MarkStore marks, DiagnosticLog log) {
        this.registry = registry;
        this.marks = marks;
        this.log = log;
    }

    /** Sets who is told when a player's entries start over (login, respawn, dimension change, logout). */
    public void onStartOver(java.util.function.Consumer<UUID> listener) {
        this.startOver = listener;
    }

    /** Builds the table from the definitions as they are after the server-start checks. */
    public void buildTable() {
        table = PresentationTable.build(registry);
    }

    /** The presentation table, shared with the audience sender. */
    PresentationTable table() {
        if (table == null) {
            buildTable();
        }
        return table;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP && channel() != null) {
            channel().sendTo(new TableMessage(table().rows()), (EntityPlayerMP) event.player);
            if (log.isEnabled()) {
                log.note("sync player=" + event.player.getName() + " op=table rows=" + table().rows().size());
            }
            snapshot((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            snapshot((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            snapshot((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sent.remove(event.player.getUniqueID());
        startOver.accept(event.player.getUniqueID());
    }

    /** At the end of each server tick: one message per player whose entries changed. */
    public void tick(long serverTick) {
        this.tick = serverTick;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || channel() == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            EntryDiff diff = sent.get(player.getUniqueID());
            if (diff == null) {
                // Not yet snapshotted (the login event comes first); nothing to compare with.
                continue;
            }
            send(player, diff, false);
        }
    }

    private void snapshot(EntityPlayerMP player) {
        EntryDiff diff = new EntryDiff();
        sent.put(player.getUniqueID(), diff);
        // The client drops other entities' entries with an own snapshot; they are sent again from the next tick.
        startOver.accept(player.getUniqueID());
        send(player, diff, true);
    }

    private void send(EntityPlayerMP player, EntryDiff diff, boolean snapshot) {
        EntityState state = player.getCapability(EntityState.CAPABILITY, null);
        Map<String, HudEntry> now = state == null ? Collections.<String, HudEntry>emptyMap()
                : OwnEntries.build(table(), registry, state, marks.views(player.getUniqueID()),
                !player.isEntityAlive(), OwnEntriesSender::name);
        EntryDiff.Changes changes = diff.update(now, tick);
        if (!snapshot && changes.isEmpty()) {
            return;
        }
        channel().sendTo(new EntriesMessage(snapshot, changes.changed, changes.removed), player);
        if (log.isEnabled()) {
            // One line per message, naming every entry in it, so the rate limit drops whole messages, never a
            // part of one.
            StringBuilder line = new StringBuilder("sync player=").append(player.getName())
                    .append(" op=").append(snapshot ? "snapshot" : "change")
                    .append(" changed=").append(changes.changed.size()).append(" removed=")
                    .append(changes.removed.size()).append(" entries=");
            for (HudEntry e : changes.changed) {
                HudDefinition row = table().row(e.definition);
                line.append(row == null ? "?" : row.id).append('/').append(kindText(row))
                        .append(e.active ? "" : "/inactive").append(',');
            }
            for (String key : changes.removed) {
                line.append("-").append(key).append(',');
            }
            log.note(line.toString());
        }
    }

    private static String kindText(@Nullable HudDefinition row) {
        if (row == null) {
            return "?";
        }
        switch (row.kind) {
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

    /** A status source's name: the loaded entity's name, or the first part of its UUID; never loads anything. */
    private static String name(UUID id) {
        for (WorldServer world : DimensionManager.getWorlds()) {
            Entity e = world.getEntityFromUuid(id);
            if (e != null) {
                return e.getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    @Nullable
    private static net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper channel() {
        return StatusworksNetwork.channel;
    }
}
