package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.ParticleAudience;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.mark.MarkStore;
import com.mahghuuuls.statusworks.core.rule.PoolMath;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.state.EntityState;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.event.entity.player.PlayerEvent.StopTracking;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sends each player the entries of entities whose particles it may see, so its client draws them: a full snapshot
 * when the entity first has entries for the player, then only what changed, and a removal when it has none any more
 * (out of range, no longer tracked, ended). The one place that decides a particle audience; the client draws
 * whatever it was sent.
 *
 * <p>Who may see an entity (the players the server tracks it for, with their distances) is checked at most every
 * {@link AudienceRules#RECHECK_TICKS} ticks per entity that shows particles, spread over the ticks, and again at once
 * when a player starts or stops tracking it or when a player's entries start over (login, respawn, dimension change,
 * logout). The entries are built each tick only for entities that show particles; an author-hidden definition has
 * no row, so it is never built.</p>
 */
public final class AudienceSender {

    /** A player that may see an entity, as last checked. */
    private static final class Candidate {
        final EntityPlayerMP player;
        final AudienceRules.Viewer viewer;

        Candidate(EntityPlayerMP player, AudienceRules.Viewer viewer) {
            this.player = player;
            this.viewer = viewer;
        }
    }

    private static final class Checked {
        final long from;
        final List<Candidate> candidates;

        Checked(long from, List<Candidate> candidates) {
            this.from = from;
            this.candidates = candidates;
        }
    }

    private final DefinitionRegistry registry;
    private final MarkStore marks;
    private final DiagnosticLog log;
    private final OwnEntriesSender own;
    private final Consumer<BiConsumer<EntityLivingBase, EntityState>> tracked;
    /** What each player was sent, per entity id. */
    private final Map<UUID, Map<Integer, EntryDiff>> sent = new HashMap<>();
    /** Who may see each entity that shows particles, by entity id. */
    private final Map<Integer, Checked> checked = new HashMap<>();
    /** Each emitting entity's tick counter at the last tick, to send its timers inactive while it is frozen. */
    private final Map<Integer, Integer> lastTicks = new HashMap<>();
    private long tick;

    /**
     * @param tracked visits every entity with tracked state; the entities without any are never looked at
     */
    public AudienceSender(DefinitionRegistry registry, MarkStore marks, DiagnosticLog log, OwnEntriesSender own,
                          Consumer<BiConsumer<EntityLivingBase, EntityState>> tracked) {
        this.registry = registry;
        this.marks = marks;
        this.log = log;
        this.own = own;
        this.tracked = tracked;
        own.onStartOver(this::startOver);
    }

    @SubscribeEvent
    public void onStartTracking(StartTracking event) {
        checked.remove(event.getTarget().getEntityId());
    }

    @SubscribeEvent
    public void onStopTracking(StopTracking event) {
        checked.remove(event.getTarget().getEntityId());
    }

    /**
     * A player's entries start over (its client dropped everything): forget what it was sent, and check every
     * entity's viewers again, since vanilla stops tracking for a leaving player without a stop event.
     */
    private void startOver(UUID player) {
        sent.remove(player);
        checked.clear();
    }

    /** At the end of each server tick, after the own entries: one message per player and entity that changed. */
    public void tick(long serverTick) {
        this.tick = serverTick;
        PresentationTable table = own.table();
        if (StatusworksNetwork.channel == null || !table.hasParticles()) {
            return;
        }
        Set<Integer> emitting = new HashSet<>();
        // Per entity, per player: the entries that player sees on that entity now.
        Map<EntityLivingBase, Map<EntityPlayerMP, Map<String, HudEntry>>> now = new IdentityHashMap<>();
        tracked.accept((entity, state) -> collectOwn(table, entity, state, emitting, now));
        for (MarkStore.ParticleLink link : marks.particleLinks(id -> table.markParticles(id) != null)) {
            collectMark(table, link, emitting, now);
        }
        Map<UUID, Set<Integer>> produced = new HashMap<>();
        for (Map.Entry<EntityLivingBase, Map<EntityPlayerMP, Map<String, HudEntry>>> e : now.entrySet()) {
            for (Map.Entry<EntityPlayerMP, Map<String, HudEntry>> p : e.getValue().entrySet()) {
                send(table, p.getKey(), e.getKey(), p.getValue());
                produced.computeIfAbsent(p.getKey().getUniqueID(), k -> new HashSet<>()).add(e.getKey().getEntityId());
            }
        }
        removeMissing(produced);
        // Keep the viewers and tick counters of every entity that shows particles, seen by anyone or not, so an
        // entity nobody is near is still checked only on its slot.
        checked.keySet().retainAll(emitting);
        lastTicks.keySet().retainAll(emitting);
    }

    private void collectOwn(PresentationTable table, EntityLivingBase entity, EntityState state, Set<Integer> emitting,
                            Map<EntityLivingBase, Map<EntityPlayerMP, Map<String, HudEntry>>> now) {
        if (!AudienceRules.emits(spectator(entity), entity.isEntityAlive())) {
            return;
        }
        List<HudEntry> entries = null;
        Boolean running = null;
        for (EntityState.StatusSlot slot : state.statuses()) {
            HudDefinition row = table.status(slot.id);
            if (row == null || row.particleType == null || slot.state.total() <= 0) {
                continue;
            }
            DefinitionRegistry.Entry def = registry.status(slot.id);
            if (def == null) {
                continue;
            }
            if (running == null) {
                running = running(entity);
            }
            entries = add(entries, new HudEntry(row.index + "|", row.index, HudEntry.OWN, slot.state.total(), 0, 0,
                    StatusRules.remaining(def.definition(), slot.state, null), running, null,
                    Collections.<HudEntry.Group>emptyList()));
        }
        for (EntityState.PoolSlot slot : state.pools()) {
            HudDefinition row = table.pool(slot.id);
            if (row == null || row.particleType == null) {
                continue;
            }
            if (running == null) {
                running = running(entity);
            }
            entries = add(entries, new HudEntry(row.index + "|", row.index, HudEntry.OWN, 0,
                    OwnEntries.round(slot.state.amount(), row.decimals), slot.state.capacity(),
                    PoolMath.remainingLifetime(slot.state), running, null, Collections.<HudEntry.Group>emptyList()));
        }
        if (entries == null) {
            return;
        }
        emitting.add(entity.getEntityId());
        List<Candidate> candidates = candidates(entity);
        for (HudEntry entry : entries) {
            HudDefinition row = table.row(entry.definition);
            for (Candidate c : candidates) {
                if (AudienceRules.sees(false, ParticleAudience.NEARBY, row != null && row.particlesFar, c.viewer)) {
                    put(now, entity, c.player, entry);
                }
            }
        }
    }

    private void collectMark(PresentationTable table, MarkStore.ParticleLink link, Set<Integer> emitting,
                             Map<EntityLivingBase, Map<EntityPlayerMP, Map<String, HudEntry>>> now) {
        EntityLivingBase target = link.target;
        HudDefinition row = table.markParticles(link.id);
        ParticleAudience audience = row == null ? null : table.audience(row.index);
        if (row == null || audience == null || !AudienceRules.emits(spectator(target), target.isEntityAlive())) {
            return;
        }
        emitting.add(target.getEntityId());
        // An active mark's time runs while its source is updated, which the target's own ticking does not show.
        HudEntry entry = new HudEntry(row.index + "|s:" + link.source, row.index, HudEntry.MARK_TARGET, 0, 0, 0,
                link.remaining, true, null, Collections.<HudEntry.Group>emptyList());
        for (Candidate c : candidates(target)) {
            boolean source = c.player.getUniqueID().equals(link.source);
            AudienceRules.Viewer v = new AudienceRules.Viewer(c.viewer.self, c.viewer.tracking, c.viewer.distanceSq,
                    source);
            if (AudienceRules.sees(true, audience, row.particlesFar, v)) {
                put(now, target, c.player, entry);
            }
        }
    }

    /**
     * Whether the entity was updated since the last tick, so its timers run; a frozen entity's entries go out
     * inactive, and the client does not count them down.
     */
    private boolean running(EntityLivingBase entity) {
        Integer last = lastTicks.put(entity.getEntityId(), entity.ticksExisted);
        return last == null || last != entity.ticksExisted;
    }

    private static List<HudEntry> add(@Nullable List<HudEntry> list, HudEntry entry) {
        List<HudEntry> out = list == null ? new ArrayList<>() : list;
        out.add(entry);
        return out;
    }

    private static void put(Map<EntityLivingBase, Map<EntityPlayerMP, Map<String, HudEntry>>> now,
                            EntityLivingBase entity, EntityPlayerMP player, HudEntry entry) {
        now.computeIfAbsent(entity, k -> new IdentityHashMap<>())
                .computeIfAbsent(player, k -> new LinkedHashMap<>()).put(entry.key, entry);
    }

    private static boolean spectator(EntityLivingBase entity) {
        return entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator();
    }

    /**
     * The players that may see the entity: itself if a player, and every player the server tracks it for that is
     * still in the entity's world.
     */
    private List<Candidate> candidates(EntityLivingBase entity) {
        Checked c = checked.get(entity.getEntityId());
        if (c == null || !AudienceRules.current(c.from, tick)) {
            List<Candidate> out = new ArrayList<>();
            if (entity instanceof EntityPlayerMP) {
                out.add(new Candidate((EntityPlayerMP) entity, new AudienceRules.Viewer(true, true, 0, false)));
            }
            if (entity.world instanceof WorldServer) {
                for (EntityPlayer p : ((WorldServer) entity.world).getEntityTracker().getTrackingPlayers(entity)) {
                    if (p instanceof EntityPlayerMP && p != entity) {
                        out.add(new Candidate((EntityPlayerMP) p,
                                new AudienceRules.Viewer(false, true, p.getDistanceSq(entity), false)));
                    }
                }
            }
            c = new Checked(AudienceRules.slotStart(tick, entity.getEntityId()), out);
            checked.put(entity.getEntityId(), c);
        }
        List<Candidate> out = new ArrayList<>(c.candidates.size());
        for (Candidate candidate : c.candidates) {
            EntityPlayerMP p = candidate.player;
            if (!p.hasDisconnected() && !p.isDead && p.world == entity.world) {
                out.add(candidate);
            }
        }
        return out;
    }

    private void send(PresentationTable table, EntityPlayerMP player, EntityLivingBase entity,
                      Map<String, HudEntry> entries) {
        Map<Integer, EntryDiff> forPlayer = sent.computeIfAbsent(player.getUniqueID(), k -> new HashMap<>());
        EntryDiff diff = forPlayer.get(entity.getEntityId());
        boolean snapshot = diff == null;
        if (snapshot) {
            diff = new EntryDiff();
            forPlayer.put(entity.getEntityId(), diff);
        }
        EntryDiff.Changes changes = diff.update(entries, tick);
        if (!snapshot && changes.isEmpty()) {
            return;
        }
        StatusworksNetwork.channel.sendTo(new AudienceEntriesMessage(entity.getEntityId(), snapshot,
                changes.changed, changes.removed), player);
        if (log.isEnabled()) {
            note(table, player.getName(), StatusOperations.entityLabel(entity), snapshot ? "snapshot" : "change",
                    changes);
        }
    }

    /** Sends a removal for every entity a player was sent entries of and sees nothing on now. */
    private void removeMissing(Map<UUID, Set<Integer>> produced) {
        for (Iterator<Map.Entry<UUID, Map<Integer, EntryDiff>>> it = sent.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<Integer, EntryDiff>> e = it.next();
            Set<Integer> still = produced.get(e.getKey());
            EntityPlayerMP player = null;
            for (Iterator<Integer> ids = e.getValue().keySet().iterator(); ids.hasNext(); ) {
                Integer id = ids.next();
                if (still != null && still.contains(id)) {
                    continue;
                }
                ids.remove();
                if (player == null) {
                    player = playerOf(e.getKey());
                }
                if (player != null && !player.hasDisconnected()) {
                    StatusworksNetwork.channel.sendTo(new AudienceRemoveMessage(id), player);
                    if (log.isEnabled()) {
                        log.note("audience player=" + player.getName() + " entity=#" + id + " op=remove");
                    }
                }
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    @Nullable
    private static EntityPlayerMP playerOf(UUID id) {
        net.minecraft.server.MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(id);
    }

    private void note(PresentationTable table, String player, String entity, String op, EntryDiff.Changes changes) {
        // One line per message, naming every entry in it.
        StringBuilder line = new StringBuilder("audience player=").append(player).append(" entity=").append(entity)
                .append(" op=").append(op).append(" changed=").append(changes.changed.size()).append(" removed=")
                .append(changes.removed.size()).append(" entries=");
        for (HudEntry e : changes.changed) {
            HudDefinition row = table.row(e.definition);
            line.append(row == null ? "?" : row.id).append(e.active ? "" : "/inactive").append(',');
        }
        for (String key : changes.removed) {
            line.append('-').append(key).append(',');
        }
        log.note(line.toString());
    }
}
