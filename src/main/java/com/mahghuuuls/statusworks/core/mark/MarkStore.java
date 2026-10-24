package com.mahghuuuls.statusworks.core.mark;

import com.mahghuuuls.statusworks.api.MarkResult;
import com.mahghuuuls.statusworks.api.MarkService;
import com.mahghuuuls.statusworks.api.MarkSnapshot;
import com.mahghuuuls.statusworks.api.callback.MarkContext;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkSide;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.contribution.ContributionManager;
import com.mahghuuuls.statusworks.core.contribution.ContributionValues;
import com.mahghuuuls.statusworks.core.contribution.MarkContributions;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.rule.MarkLimits;
import com.mahghuuuls.statusworks.core.rule.RemovalReason;
import com.mahghuuuls.statusworks.core.state.EntityState;
import com.mahghuuuls.statusworks.core.state.EntityTargets;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The one owner of marks: their records (saved once for the whole server in the overworld's
 * {@link MarkSavedData}), every mark operation and query, the activity rule, the timers, death and gone handling,
 * the inactive timeout, the mark callbacks, and which mark contributions an entity should have.
 *
 * <p>Activity is decided only from entities already loaded: participants are looked up by UUID in the loaded
 * worlds, so nothing is ever loaded for a mark. Activity is checked again for a participant's marks when it joins
 * or leaves a world (the next tick, once it is findable), and for every mark every {@value #RECHECK_TICKS} server
 * ticks for the distance rule; never every tick. Active marks advance one active tick when their source has
 * updated, so a mark pauses with a frozen source; inactive marks count server ticks toward the timeout.</p>
 *
 * <p>A removal that looks permanent (a despawn) ends marks with {@code gone} only when the entity has not joined
 * a world again within one tick and is not loaded anywhere, so a teleport by another mod that skips the travel
 * event does not end its marks.</p>
 *
 * <p>Contributions follow activity, with one rule for the time between a participant joining a world and its
 * marks being checked again (the next tick): its mark contributions stay exactly as its saved record has them,
 * so a mark that stays active never dips a contribution, and the maximum health it raises is never clamped.
 * After the check, every entity whose marks changed is reconciled once, after all marks were checked.</p>
 */
public final class MarkStore implements MarkService, MarkContributions {

    /** Callback keys of marks carry this prefix, so kinds with the same id are counted apart. */
    static final String KEY_PREFIX = "mark:";
    static final int RECHECK_TICKS = 20;
    /** Server ticks a removal that looks permanent waits for the same entity to join again. */
    static final long GONE_GRACE_TICKS = 1;

    private final DefinitionRegistry registry;
    private final CallbackRunner callbacks;
    private final ContributionManager contributions;
    private final DiagnosticLog log;
    private final Warnings warnings;
    private final Consumer<String> warn;

    @Nullable private MarkSavedData data;
    private final Set<UUID> dirty = new HashSet<>();
    private boolean recheckAll;
    private final Map<UUID, Long> pendingGone = new HashMap<>();
    private final Map<EntityLivingBase, Boolean> reconcileNext = new IdentityHashMap<>();
    /** Participants that joined a world and whose marks were not checked since; see the class comment. */
    private final Set<UUID> unchecked = new HashSet<>();

    public MarkStore(DefinitionRegistry registry, CallbackRunner callbacks, ContributionManager contributions,
                     DiagnosticLog log, Warnings warnings, Consumer<String> warn) {
        this.registry = registry;
        this.callbacks = callbacks;
        this.contributions = contributions;
        this.log = log;
        this.warnings = warnings;
        this.warn = warn;
    }

    // ---- loading -------------------------------------------------------------------------------------------

    /**
     * Opens the store of the overworld's save. Each mark keeps the activity it last reported, so the first check
     * after the load runs "became active" or "became inactive" only for a real change; marks of definitions that
     * no longer exist or are now invalid are dropped, and a source over a lowered target limit keeps its newest
     * marks.
     */
    public void load(World overworld) {
        MapStorage storage = overworld.getMapStorage();
        if (storage == null) {
            return;
        }
        MarkSavedData loaded = (MarkSavedData) storage.getOrLoadData(MarkSavedData.class, MarkSavedData.NAME);
        if (loaded == null) {
            loaded = new MarkSavedData();
            storage.setData(MarkSavedData.NAME, loaded);
        }
        data = loaded;
        for (String w : loaded.readWarnings) {
            warn.accept("Statusworks: " + w);
        }
        loaded.readWarnings.clear();
        int dropped = 0;
        int fitted = 0;
        Map<String, Integer> held = new HashMap<>();
        List<MarkRecord> all = loaded.records.all();
        // Newest first, so the marks kept under a lowered limit are the newest.
        for (int i = all.size() - 1; i >= 0; i--) {
            MarkRecord r = all.get(i);
            DefinitionRegistry.MarkEntry entry = registry.mark(r.id);
            if (entry == null) {
                loaded.records.remove(r);
                dropped++;
                continue;
            }
            int limit = entry.definition().targetLimit();
            int count = held.merge(r.source + " " + r.id, 1, Integer::sum);
            if (limit != MarkDefinition.UNLIMITED && count > limit) {
                loaded.records.remove(r);
                fitted++;
            }
        }
        if (dropped > 0) {
            warn.accept("Statusworks: " + dropped + " saved mark(s) of definitions that no longer exist or are"
                    + " invalid were dropped");
        }
        if (fitted > 0) {
            warn.accept("Statusworks: " + fitted + " saved mark(s) over a lowered target limit were dropped,"
                    + " oldest first");
        }
        if (dropped > 0 || fitted > 0) {
            loaded.markDirty();
        }
        recheckAll = true;
    }

    /** Closes the store when the overworld unloads (the server stops); the file was saved with the world. */
    public void unload() {
        data = null;
        dirty.clear();
        pendingGone.clear();
        reconcileNext.clear();
        unchecked.clear();
    }

    @Nullable
    private MarkRecords records() {
        return data == null ? null : data.records;
    }

    // ---- lifecycle signals ---------------------------------------------------------------------------------

    /** Whether the entity takes part in any mark; cheap, for the removal classifier. */
    public boolean participates(UUID entity) {
        MarkRecords records = records();
        return records != null && records.participates(entity);
    }

    /**
     * An entity joined a world: its marks are checked on the next tick (it is not findable yet), it is no longer
     * suspected gone, and it is reconciled then, which also drops recorded mark contributions of marks that ended
     * while it was unloaded. Until then its mark contributions stay as recorded. Called before the join's own
     * reconcile.
     */
    public void onJoin(EntityLivingBase entity, EntityState state) {
        MarkRecords records = records();
        if (records == null) {
            return;
        }
        UUID id = entity.getUniqueID();
        pendingGone.remove(id);
        boolean participant = records.participates(id);
        boolean recorded = hasMarkContribution(state);
        if (participant) {
            dirty.add(id);
        }
        if (participant || recorded) {
            unchecked.add(id);
            reconcileNext.put(entity, Boolean.TRUE);
        }
    }

    private static boolean hasMarkContribution(EntityState state) {
        for (String key : state.contributions().entries().keySet()) {
            if (ContributionValues.isMarkKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** A participant left its world without ending: unload, logout, or travel. Its marks are checked next tick. */
    public void participantLeft(UUID entity) {
        if (participates(entity)) {
            dirty.add(entity);
        }
    }

    /** A participant's removal looks permanent; its marks end with {@code gone} unless it comes back at once. */
    public void participantGone(UUID entity, long serverTick) {
        if (participates(entity)) {
            pendingGone.put(entity, serverTick);
        }
    }

    /**
     * A participant died: its marks end with reason {@code death}, except, on a player, the marks whose
     * definition keeps them on that side's death; those stay and are inactive until the respawn.
     */
    public void onDeath(EntityLivingBase entity) {
        MarkRecords records = records();
        if (records == null || !records.participates(entity.getUniqueID())) {
            return;
        }
        UUID id = entity.getUniqueID();
        boolean player = entity instanceof EntityPlayer;
        callbacks.batch(() -> {
            for (MarkRecord r : records.involving(id)) {
                MarkDefinition def = definition(r);
                if (def != null && MarkLimits.survivesDeath(def, r.source.equals(id), player)) {
                    dirty.add(id);
                } else {
                    end(r, RemovalReason.DEATH, StatusOperations.CAUSE_DEATH);
                }
            }
        });
    }

    /** A dimension unloaded; every mark is checked again on the next tick. */
    public void onWorldUnload(World world) {
        recheckAll = true;
    }

    // ---- the tick ------------------------------------------------------------------------------------------

    /** Once per server tick, after every world ticked: gone handling, activity checks, timers, then joiners. */
    public void tick(long serverTick) {
        MarkRecords records = records();
        if (records == null) {
            return;
        }
        if (records.isEmpty() && reconcileNext.isEmpty()) {
            pendingGone.clear();
            dirty.clear();
            unchecked.clear();
            recheckAll = false;
            return;
        }
        callbacks.batch(() -> {
            endGone(records, serverTick);
            // Every mark first, then each changed entity once, so several marks changing together never make
            // an entity lose and regain contributions within one tick.
            Map<EntityLivingBase, Boolean> changed = new IdentityHashMap<>(reconcileNext);
            reconcileNext.clear();
            if (recheckAll || serverTick % RECHECK_TICKS == 0) {
                for (MarkRecord r : records.all()) {
                    recheck(r, changed);
                }
            } else if (!dirty.isEmpty()) {
                // A copy: an author's answer during a recheck may kill a participant and mark it again.
                for (UUID id : new ArrayList<>(dirty)) {
                    for (MarkRecord r : records.involving(id)) {
                        recheck(r, changed);
                    }
                }
            }
            recheckAll = false;
            dirty.clear();
            unchecked.clear();
            for (EntityLivingBase e : changed.keySet()) {
                reconcile(e);
            }
            for (MarkRecord r : records.all()) {
                advance(r);
            }
        });
        if (!records.isEmpty() && data != null) {
            data.markDirty();
        }
    }

    private void endGone(MarkRecords records, long serverTick) {
        if (pendingGone.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Long>> it = pendingGone.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (serverTick - e.getValue() < GONE_GRACE_TICKS) {
                continue;
            }
            it.remove();
            if (find(e.getKey()) != null) {
                continue;
            }
            for (MarkRecord r : records.involving(e.getKey())) {
                end(r, RemovalReason.GONE, StatusOperations.CAUSE_TIMER);
            }
        }
    }

    /** Applies the activity rule to one mark and handles a change; entities to reconcile go to {@code changed}. */
    private void recheck(MarkRecord r, Map<EntityLivingBase, Boolean> changed) {
        if (r.ended) {
            return;
        }
        MarkDefinition def = definition(r);
        if (def == null) {
            return;
        }
        EntityLivingBase oldSource = r.sourceEntity;
        EntityLivingBase oldTarget = r.targetEntity;
        EntityLivingBase s = find(r.source);
        EntityLivingBase t = find(r.target);
        r.sourceEntity = s;
        r.targetEntity = t;
        if (s != null) {
            r.sourceName = s.getName();
        }
        if (t != null) {
            r.targetName = t.getName();
        }
        boolean sourceLoaded = s != null && s.isEntityAlive();
        boolean targetLoaded = t != null && t.isEntityAlive();
        boolean sameWorld = sourceLoaded && targetLoaded && s.world == t.world;
        boolean now = MarkLimits.active(def, sourceLoaded, targetLoaded, sameWorld,
                sameWorld ? s.getDistanceSq(t) : 0);
        // Inactive only because of the maximum distance: distance alone never ends a mark, so no timeout runs.
        r.distanceOnly = !now && sameWorld;
        if (now != r.active) {
            setActive(r, def, now);
            mark(changed, s);
            mark(changed, t);
        } else if (now && (s != oldSource || t != oldTarget)) {
            // Still active, but a participant is a new object (a respawned player): give it the contributions.
            r.lastSourceTicks = s.ticksExisted;
            mark(changed, s);
            mark(changed, t);
        }
    }

    private static void mark(Map<EntityLivingBase, Boolean> changed, @Nullable EntityLivingBase entity) {
        if (entity != null) {
            changed.put(entity, Boolean.TRUE);
        }
    }

    private void setActive(MarkRecord r, MarkDefinition def, boolean active) {
        r.active = active;
        if (active) {
            r.inactiveTicks = 0;
            if (r.sourceEntity != null) {
                r.lastSourceTicks = r.sourceEntity.ticksExisted;
            }
        }
        if (log.isEnabled()) {
            log.note("mark-activity def=" + r.id + " source=" + r.sourceName + " target=" + r.targetName
                    + " active=" + active + " remaining=" + remainingText(r));
        }
        MarkContext context = context(r, null);
        if (active && def.onActive() != null) {
            callbacks.run(KEY_PREFIX + r.id, "active", () -> def.onActive().onActive(context));
        } else if (!active && def.onInactive() != null) {
            callbacks.run(KEY_PREFIX + r.id, "inactive", () -> def.onInactive().onInactive(context));
        }
    }

    /** One tick of time: an active mark with an updated source ages; an inactive one counts toward its timeout. */
    private void advance(MarkRecord r) {
        if (r.ended) {
            return;
        }
        MarkDefinition def = definition(r);
        if (def == null) {
            // Disabled after load (the server-start attribute check); it can no longer run.
            end(r, RemovalReason.INVALID, StatusOperations.CAUSE_LOAD);
            return;
        }
        if (!r.active) {
            if (r.distanceOnly) {
                return;
            }
            r.inactiveTicks++;
            if (MarkLimits.timedOut(def, r.inactiveTicks)) {
                end(r, RemovalReason.INACTIVE_TIMEOUT, StatusOperations.CAUSE_TIMER);
            }
            return;
        }
        EntityLivingBase s = r.sourceEntity;
        EntityLivingBase t = r.targetEntity;
        if (s == null || t == null || !s.isEntityAlive() || !t.isEntityAlive()) {
            // A participant is leaving; its removal is checked next tick.
            return;
        }
        if (s.ticksExisted == r.lastSourceTicks) {
            return;
        }
        r.lastSourceTicks = s.ticksExisted;
        if (def.onInterval() != null && MarkLimits.intervalDue(def, r.age)) {
            MarkContext context = context(r, null);
            callbacks.run(KEY_PREFIX + r.id, "interval", () -> def.onInterval().onInterval(context));
            if (r.ended) {
                return;
            }
        }
        r.age++;
        if (MarkLimits.expired(r.age, r.endAge)) {
            end(r, RemovalReason.EXPIRED, StatusOperations.CAUSE_TIMER);
        }
    }

    // ---- operations ----------------------------------------------------------------------------------------

    @Override
    public MarkResult mark(Entity source, Entity target, String id) {
        return mark(source, target, id, 0, StatusOperations.CAUSE_OPERATION);
    }

    @Override
    public MarkResult mark(Entity source, Entity target, String id, int ticks) {
        return mark(source, target, id, ticks, StatusOperations.CAUSE_OPERATION);
    }

    public MarkResult mark(Entity source, Entity target, String id, int ticks, String cause) {
        MarkRecords records = records();
        if (records == null) {
            return MarkResult.rejected("marks are available only while the server runs");
        }
        if (data.newer != null) {
            return MarkResult.rejected("the saved marks come from a newer Statusworks and are read-only");
        }
        EntityTargets.Checked s = EntityTargets.check(source, true);
        if (s.failure != null) {
            return MarkResult.rejected("source: " + s.failure);
        }
        EntityTargets.Checked t = EntityTargets.check(target, true);
        if (t.failure != null) {
            return MarkResult.rejected("target: " + t.failure);
        }
        EntityLivingBase src = s.entity;
        EntityLivingBase tgt = t.entity;
        if (src.getUniqueID().equals(tgt.getUniqueID())) {
            return MarkResult.rejected("an entity cannot mark itself");
        }
        MarkDefinition def = lookup(id);
        if (def == null) {
            return MarkResult.rejected(unknownText(id));
        }
        String resolved = resolve(cause);
        MarkResult[] result = new MarkResult[1];
        callbacks.batch(() -> {
            MarkRecord existing = records.find(id, src.getUniqueID(), tgt.getUniqueID());
            List<MarkRecord> held = records.ofSource(src.getUniqueID(), id);
            int duration = MarkLimits.duration(def, ticks);
            switch (MarkLimits.decide(def, existing != null, held.size())) {
                case REFRESH: {
                    String before = describeChange(existing);
                    existing.endAge = MarkLimits.refresh(def, existing.age, existing.endAge, duration);
                    logChange(existing, before, describeChange(existing), resolved, null);
                    result[0] = MarkResult.refreshed();
                    break;
                }
                case REJECT:
                    result[0] = MarkResult.rejected("the source already marks " + held.size() + " of at most "
                            + def.targetLimit() + " targets and the definition rejects more");
                    break;
                case REPLACE_OLDEST: {
                    MarkRecord oldest = held.get(0);
                    end(oldest, RemovalReason.REPLACED, resolved);
                    create(records, def, src, tgt, duration, resolved);
                    result[0] = MarkResult.replaced(oldest.target);
                    break;
                }
                default:
                    create(records, def, src, tgt, duration, resolved);
                    result[0] = MarkResult.created();
            }
        });
        data.markDirty();
        return result[0];
    }

    private void create(MarkRecords records, MarkDefinition def, EntityLivingBase s, EntityLivingBase t,
                        int duration, String cause) {
        MarkRecord r = new MarkRecord(def.id(), s.getUniqueID(), t.getUniqueID(), records.takeOrder(), s.getName(),
                t.getName());
        r.endAge = MarkLimits.endAge(0, duration);
        r.sourceEntity = s;
        r.targetEntity = t;
        r.lastSourceTicks = s.ticksExisted;
        boolean sameWorld = s.world == t.world;
        r.active = MarkLimits.active(def, true, true, sameWorld, sameWorld ? s.getDistanceSq(t) : 0);
        records.add(r);
        logChange(r, "none", describeChange(r), cause, null);
        if (def.onApplied() != null) {
            MarkContext context = context(r, null);
            callbacks.run(KEY_PREFIX + r.id, "applied", () -> def.onApplied().onApplied(context));
        }
        if (r.active) {
            // A participant that joined this tick keeps only its recorded mark contributions until the end of the
            // tick; it gets this new one then, when the tick's reconcile of joiners runs.
            reconcile(s);
            reconcile(t);
        }
    }

    @Override
    public boolean unmark(Entity source, Entity target, String id) {
        return unmark(source, target, id, StatusOperations.CAUSE_OPERATION);
    }

    public boolean unmark(Entity source, Entity target, String id, String cause) {
        MarkRecords records = records();
        EntityTargets.Checked s = EntityTargets.check(source, false);
        EntityTargets.Checked t = EntityTargets.check(target, false);
        if (records == null || s.failure != null || t.failure != null || lookup(id) == null) {
            return false;
        }
        MarkRecord r = records.find(id, s.entity.getUniqueID(), t.entity.getUniqueID());
        if (r == null) {
            return false;
        }
        String resolved = resolve(cause);
        callbacks.batch(() -> end(r, RemovalReason.REMOVED, resolved));
        return true;
    }

    @Override
    public int clearSource(Entity source, String id) {
        return clear(source, id, true, StatusOperations.CAUSE_OPERATION);
    }

    @Override
    public int clearTarget(Entity target, String id) {
        return clear(target, id, false, StatusOperations.CAUSE_OPERATION);
    }

    /** Ends every mark of the definition the entity holds ({@code asSource}) or carries, with {@code removed}. */
    public int clear(Entity entity, String id, boolean asSource, String cause) {
        MarkRecords records = records();
        EntityTargets.Checked e = EntityTargets.check(entity, false);
        if (records == null || e.failure != null || lookup(id) == null) {
            return 0;
        }
        UUID uuid = e.entity.getUniqueID();
        List<MarkRecord> list = asSource ? records.ofSource(uuid, id) : records.ofTarget(uuid, id);
        String resolved = resolve(cause);
        callbacks.batch(() -> {
            for (MarkRecord r : list) {
                end(r, RemovalReason.REMOVED, resolved);
            }
        });
        return list.size();
    }

    /** Ends a mark: it leaves the store, its removal callback runs, and its participants lose its contributions. */
    private void end(MarkRecord r, RemovalReason reason, String cause) {
        if (r.ended) {
            return;
        }
        MarkRecords records = records();
        if (records == null) {
            return;
        }
        r.ended = true;
        String before = describeChange(r);
        records.remove(r);
        if (data != null) {
            data.markDirty();
        }
        logChange(r, before, "none", cause, reason.text());
        EntityLivingBase s = loaded(r.sourceEntity, r.source);
        EntityLivingBase t = loaded(r.targetEntity, r.target);
        MarkDefinition def = definition(r);
        if (def != null && def.onRemoved() != null) {
            MarkContext context = new MarkContext(r.id, r.source, r.target, s, t, reason.text());
            callbacks.run(KEY_PREFIX + r.id, "removed", () -> def.onRemoved().onRemoved(context));
        }
        reconcile(s);
        reconcile(t);
    }

    // ---- queries -------------------------------------------------------------------------------------------

    @Override
    public boolean isMarked(Entity source, Entity target, String id) {
        MarkRecords records = records();
        EntityTargets.Checked s = EntityTargets.check(source, false);
        EntityTargets.Checked t = EntityTargets.check(target, false);
        return records != null && s.failure == null && t.failure == null && lookup(id) != null
                && records.find(id, s.entity.getUniqueID(), t.entity.getUniqueID()) != null;
    }

    @Override
    public List<MarkSnapshot> targets(Entity source, String id) {
        return snapshots(source, id, true);
    }

    @Override
    public List<MarkSnapshot> sources(Entity target, String id) {
        return snapshots(target, id, false);
    }

    @Override
    public List<EntityLivingBase> targetEntities(Entity source, String id) {
        return loadedOthers(snapshots(source, id, true));
    }

    @Override
    public List<EntityLivingBase> sourceEntities(Entity target, String id) {
        return loadedOthers(snapshots(target, id, false));
    }

    @Override
    public int targetCount(Entity source, String id) {
        return snapshots(source, id, true).size();
    }

    @Override
    public int sourceCount(Entity target, String id) {
        return snapshots(target, id, false).size();
    }

    private List<MarkSnapshot> snapshots(Entity entity, String id, boolean asSource) {
        List<MarkSnapshot> out = new ArrayList<>();
        MarkRecords records = records();
        EntityTargets.Checked e = EntityTargets.check(entity, false);
        if (records == null || e.failure != null || lookup(id) == null) {
            return out;
        }
        UUID uuid = e.entity.getUniqueID();
        for (MarkRecord r : asSource ? records.ofSource(uuid, id) : records.ofTarget(uuid, id)) {
            out.add(new MarkSnapshot(r.id, r.source, r.target, find(asSource ? r.target : r.source), r.active,
                    MarkLimits.remaining(r.age, r.endAge), r.active ? 0 : r.inactiveTicks));
        }
        return out;
    }

    private static List<EntityLivingBase> loadedOthers(List<MarkSnapshot> snapshots) {
        List<EntityLivingBase> out = new ArrayList<>();
        for (MarkSnapshot s : snapshots) {
            if (s.other() != null) {
                out.add(s.other());
            }
        }
        return out;
    }

    /** {@code inspect} lines for every mark the entity takes part in, either side, every definition. */
    public List<String> describe(UUID entity) {
        List<String> out = new ArrayList<>();
        MarkRecords records = records();
        if (records == null) {
            return out;
        }
        for (MarkRecord r : records.asSource(entity)) {
            out.add("mark " + r.id + " target=" + r.targetName + " " + state(r));
        }
        for (MarkRecord r : records.asTarget(entity)) {
            out.add("marked-by " + r.id + " source=" + r.sourceName + " " + state(r));
        }
        return out;
    }

    private static String state(MarkRecord r) {
        return "active=" + r.active + " remaining=" + remainingText(r) + " inactive=" + (r.active ? 0 : r.inactiveTicks)
                + " age=" + r.age + " order=" + r.order;
    }

    /** Number of marks in the store, for diagnostics. */
    public int size() {
        MarkRecords records = records();
        return records == null ? 0 : records.size();
    }

    // ---- contributions -------------------------------------------------------------------------------------

    @Override
    public void collect(EntityLivingBase entity, BiConsumer<String, Contribution> out) {
        MarkRecords records = records();
        if (records == null) {
            return;
        }
        UUID id = entity.getUniqueID();
        Map<String, ?> recorded = null;
        if (unchecked.contains(id)) {
            EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
            recorded = state == null ? java.util.Collections.emptyMap() : state.contributions().entries();
        }
        for (MarkRecord r : records.asSource(id)) {
            add(r, MarkSide.SOURCE, recorded, out);
        }
        for (MarkRecord r : records.asTarget(id)) {
            add(r, MarkSide.TARGET, recorded, out);
        }
    }

    /**
     * @param recorded the entity's recorded contributions while it is unchecked (keep exactly those), else null
     *                 (follow the mark's activity)
     */
    private void add(MarkRecord r, MarkSide side, @Nullable Map<String, ?> recorded,
                     BiConsumer<String, Contribution> out) {
        if (recorded == null && !r.active) {
            return;
        }
        MarkDefinition def = definition(r);
        if (def == null) {
            return;
        }
        List<Contribution> list = def.contributions(side);
        String[] keys = r.keys(side, list.size());
        for (int i = 0; i < list.size(); i++) {
            if (recorded == null || recorded.containsKey(keys[i])) {
                out.accept(keys[i], list.get(i));
            }
        }
    }

    private void reconcile(@Nullable EntityLivingBase entity) {
        if (entity == null || entity.world == null || entity.world.isRemote) {
            return;
        }
        EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
        if (state != null) {
            contributions.reconcile(entity, state);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /**
     * A participant if it is loaded in any loaded world; never loads anything. Only the loaded worlds are asked,
     * and each only through its loaded-entity map.
     */
    @Nullable
    static EntityLivingBase find(UUID id) {
        for (WorldServer world : DimensionManager.getWorlds()) {
            Entity e = world.getEntityFromUuid(id);
            if (e instanceof EntityLivingBase && !e.isDead) {
                return (EntityLivingBase) e;
            }
        }
        return null;
    }

    /** The cached participant when it is still in a world, otherwise a fresh lookup. */
    @Nullable
    private static EntityLivingBase loaded(@Nullable EntityLivingBase cached, UUID id) {
        if (cached != null && !cached.isDead && cached.world != null) {
            return cached;
        }
        return find(id);
    }

    @Nullable
    private MarkDefinition definition(MarkRecord r) {
        DefinitionRegistry.MarkEntry entry = registry.mark(r.id);
        return entry == null ? null : entry.definition();
    }

    @Nullable
    private MarkDefinition lookup(@Nullable String id) {
        if (id == null) {
            return null;
        }
        DefinitionRegistry.MarkEntry entry = registry.mark(id);
        if (entry == null) {
            warnings.once("mark:" + id, "Statusworks: " + unknownText(id));
            return null;
        }
        return entry.definition();
    }

    private String unknownText(@Nullable String id) {
        if (id == null) {
            return "definition id is null";
        }
        return registry.isInvalidMark(id) ? "mark " + id + " is disabled because its definition is invalid"
                : "no mark is registered with id " + id;
    }

    private MarkContext context(MarkRecord r, @Nullable String reason) {
        return new MarkContext(r.id, r.source, r.target, loaded(r.sourceEntity, r.source),
                loaded(r.targetEntity, r.target), reason);
    }

    private String resolve(String cause) {
        return callbacks.inCallback() && StatusOperations.CAUSE_OPERATION.equals(cause)
                ? StatusOperations.CAUSE_CALLBACK : cause;
    }

    private static String remainingText(MarkRecord r) {
        int remaining = MarkLimits.remaining(r.age, r.endAge);
        return remaining < 0 ? "none" : Integer.toString(remaining);
    }

    private static String describeChange(MarkRecord r) {
        return "target=" + r.targetName + "/remaining=" + remainingText(r) + "/active=" + r.active;
    }

    private void logChange(MarkRecord r, String before, String after, String cause, @Nullable String reason) {
        if (log.isEnabled()) {
            log.change(r.id, new String[]{r.sourceName, r.source.toString(), r.targetName, r.target.toString()},
                    r.sourceName, "mark", before, after, cause, reason);
        }
    }
}
