package com.mahghuuuls.statusworks.core.definition;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.Direction;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkSide;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.Progression;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Collects definitions during startup, validates them once when they freeze, and answers lookups afterwards.
 *
 * <p>This class owns what makes a definition valid, which registration wins for a duplicate id (the first, even
 * when it is invalid, so a broken definition is reported rather than silently replaced),
 * the registration order every tick follows, and the freeze. After the freeze only one change happens: the
 * check of other mods' attribute ids when the server starts ({@link #checkLateAttributes}) can disable
 * definitions.</p>
 *
 * <p>Each kind (status, pool, mark, cooldown) has its own ids and its own registration order.</p>
 */
public final class DefinitionRegistry {

    /** An id is any non-empty text without whitespace, so it can be typed as one command argument. */
    private static final Pattern ID = Pattern.compile("\\S+");

    /** Attribute ids of vanilla Minecraft and Forge, known when definitions freeze. */
    public static final Set<String> VANILLA_ATTRIBUTES = java.util.Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("generic.maxHealth", "generic.followRange", "generic.knockbackResistance",
                    "generic.movementSpeed", "generic.flyingSpeed", "generic.attackDamage", "generic.attackSpeed",
                    "generic.armor", "generic.armorToughness", "generic.luck", "generic.reachDistance",
                    "forge.swimSpeed", "horse.jumpStrength", "zombie.spawnReinforcements")));

    /** Whether a contribution type id is registered; checked when definitions freeze. */
    private final Predicate<String> knownType;

    /** A registry that knows no contribution type (tests and the default). */
    public DefinitionRegistry() {
        this(id -> false);
    }

    public DefinitionRegistry(Predicate<String> knownType) {
        this.knownType = knownType;
    }

    /** A valid, registered definition with where it came from and its position in registration order. */
    public static final class Entry {
        private final StatusDefinition definition;
        private final String origin;
        private final int order;

        Entry(StatusDefinition definition, String origin, int order) {
            this.definition = definition;
            this.origin = origin;
            this.order = order;
        }

        public StatusDefinition definition() {
            return definition;
        }

        public String origin() {
            return origin;
        }

        public int order() {
            return order;
        }
    }

    /** One validation error, naming the definition and its origin. */
    public static final class Error {
        public final String id;
        public final String reason;
        public final String message;
        public final String origin;

        Error(String id, String reason, String message, String origin) {
            this.id = id;
            this.reason = reason;
            this.message = message;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return id + " [" + reason + "] " + message + " (source " + origin + ")";
        }
    }

    /** A valid, registered pool definition with its origin and its position in pool registration order. */
    public static final class PoolEntry {
        private final PoolDefinition definition;
        private final String origin;
        private final int order;

        PoolEntry(PoolDefinition definition, String origin, int order) {
            this.definition = definition;
            this.origin = origin;
            this.order = order;
        }

        public PoolDefinition definition() {
            return definition;
        }

        public String origin() {
            return origin;
        }

        public int order() {
            return order;
        }
    }

    /** A valid, registered cooldown definition with its origin and its position in cooldown registration order. */
    public static final class CooldownEntry {
        private final CooldownDefinition definition;
        private final String origin;
        private final int order;

        CooldownEntry(CooldownDefinition definition, String origin, int order) {
            this.definition = definition;
            this.origin = origin;
            this.order = order;
        }

        public CooldownDefinition definition() {
            return definition;
        }

        public String origin() {
            return origin;
        }

        public int order() {
            return order;
        }
    }

    /** A valid, registered mark definition with its origin and its position in mark registration order. */
    public static final class MarkEntry {
        private final MarkDefinition definition;
        private final String origin;
        private final int order;

        MarkEntry(MarkDefinition definition, String origin, int order) {
            this.definition = definition;
            this.origin = origin;
            this.order = order;
        }

        public MarkDefinition definition() {
            return definition;
        }

        public String origin() {
            return origin;
        }

        public int order() {
            return order;
        }
    }

    private static final class PendingMark {
        final MarkDefinition definition;
        final String origin;

        PendingMark(MarkDefinition definition, String origin) {
            this.definition = definition;
            this.origin = origin;
        }
    }

    private static final class PendingCooldown {
        final CooldownDefinition definition;
        final String origin;

        PendingCooldown(CooldownDefinition definition, String origin) {
            this.definition = definition;
            this.origin = origin;
        }
    }

    private static final class Pending {
        final StatusDefinition definition;
        final String origin;

        Pending(StatusDefinition definition, String origin) {
            this.definition = definition;
            this.origin = origin;
        }
    }

    private static final class PendingPool {
        final PoolDefinition definition;
        final String origin;

        PendingPool(PoolDefinition definition, String origin) {
            this.definition = definition;
            this.origin = origin;
        }
    }

    private final List<Pending> pending = new ArrayList<>();
    private final List<PendingPool> pendingPools = new ArrayList<>();
    private final List<PendingCooldown> pendingCooldowns = new ArrayList<>();
    private final List<PendingMark> pendingMarks = new ArrayList<>();
    private final Map<String, MarkEntry> marks = new LinkedHashMap<>();
    private final Map<String, String> invalidMarkOrigins = new LinkedHashMap<>();
    private final Map<String, Entry> statuses = new LinkedHashMap<>();
    private final Map<String, PoolEntry> pools = new LinkedHashMap<>();
    private final Map<String, CooldownEntry> cooldowns = new LinkedHashMap<>();
    private final Map<String, String> invalidOrigins = new LinkedHashMap<>();
    private final Map<String, String> invalidPoolOrigins = new LinkedHashMap<>();
    private final Map<String, String> invalidCooldownOrigins = new LinkedHashMap<>();
    private final List<Error> errors = new ArrayList<>();
    private boolean frozen;

    /** Queues a status definition for validation at the freeze; refuses it once frozen. */
    public synchronized RegistrationResult registerStatus(@Nullable StatusDefinition definition, String origin) {
        if (frozen) {
            String id = definition == null ? "<null>" : definition.id();
            return RegistrationResult.refused("definitions are frozen; " + id + " from " + origin
                    + " was registered after game startup");
        }
        if (definition == null) {
            return RegistrationResult.refused("definition is null");
        }
        pending.add(new Pending(definition, origin));
        return RegistrationResult.accepted();
    }

    /** Queues a pool definition for validation at the freeze; refuses it once frozen. */
    public synchronized RegistrationResult registerPool(@Nullable PoolDefinition definition, String origin) {
        if (frozen) {
            String id = definition == null ? "<null>" : definition.id();
            return RegistrationResult.refused("definitions are frozen; pool " + id + " from " + origin
                    + " was registered after game startup");
        }
        if (definition == null) {
            return RegistrationResult.refused("definition is null");
        }
        pendingPools.add(new PendingPool(definition, origin));
        return RegistrationResult.accepted();
    }

    /** Queues a cooldown definition for validation at the freeze; refuses it once frozen. */
    public synchronized RegistrationResult registerCooldown(@Nullable CooldownDefinition definition, String origin) {
        if (frozen) {
            String id = definition == null ? "<null>" : definition.id();
            return RegistrationResult.refused("definitions are frozen; cooldown " + id + " from " + origin
                    + " was registered after game startup");
        }
        if (definition == null) {
            return RegistrationResult.refused("definition is null");
        }
        pendingCooldowns.add(new PendingCooldown(definition, origin));
        return RegistrationResult.accepted();
    }

    /** Queues a mark definition for validation at the freeze; refuses it once frozen. */
    public synchronized RegistrationResult registerMark(@Nullable MarkDefinition definition, String origin) {
        if (frozen) {
            String id = definition == null ? "<null>" : definition.id();
            return RegistrationResult.refused("definitions are frozen; mark " + id + " from " + origin
                    + " was registered after game startup");
        }
        if (definition == null) {
            return RegistrationResult.refused("definition is null");
        }
        pendingMarks.add(new PendingMark(definition, origin));
        return RegistrationResult.accepted();
    }

    /** Validates every queued definition in registration order and freezes the registry. */
    public synchronized List<Error> freeze() {
        if (frozen) {
            return Collections.unmodifiableList(errors);
        }
        Map<String, String> claimed = new LinkedHashMap<>();
        for (Pending p : pending) {
            String id = p.definition.id();
            if (id != null && claimed.containsKey(id)) {
                errors.add(new Error(id, "duplicate-id", "already registered by " + claimed.get(id), p.origin));
                continue;
            }
            if (id != null) {
                claimed.put(id, p.origin);
            }
            List<Error> found = validate(p.definition, p.origin);
            if (found.isEmpty()) {
                statuses.put(id, new Entry(p.definition, p.origin, statuses.size()));
            } else {
                errors.addAll(found);
                invalidOrigins.put(found.get(0).id, p.origin);
            }
        }
        pending.clear();
        Map<String, String> claimedPools = new LinkedHashMap<>();
        for (PendingPool p : pendingPools) {
            String id = p.definition.id();
            if (id != null && claimedPools.containsKey(id)) {
                errors.add(new Error(id, "duplicate-id", "pool already registered by " + claimedPools.get(id),
                        p.origin));
                continue;
            }
            if (id != null) {
                claimedPools.put(id, p.origin);
            }
            List<Error> found = validatePool(p.definition, p.origin);
            if (found.isEmpty()) {
                pools.put(id, new PoolEntry(p.definition, p.origin, pools.size()));
            } else {
                errors.addAll(found);
                invalidPoolOrigins.put(found.get(0).id, p.origin);
            }
        }
        pendingPools.clear();
        Map<String, String> claimedCooldowns = new LinkedHashMap<>();
        for (PendingCooldown p : pendingCooldowns) {
            String id = p.definition.id();
            if (id != null && claimedCooldowns.containsKey(id)) {
                errors.add(new Error(id, "duplicate-id", "cooldown already registered by "
                        + claimedCooldowns.get(id), p.origin));
                continue;
            }
            if (id != null) {
                claimedCooldowns.put(id, p.origin);
            }
            List<Error> found = validateCooldown(p.definition, p.origin);
            if (found.isEmpty()) {
                cooldowns.put(id, new CooldownEntry(p.definition, p.origin, cooldowns.size()));
            } else {
                errors.addAll(found);
                invalidCooldownOrigins.put(found.get(0).id, p.origin);
            }
        }
        pendingCooldowns.clear();
        Map<String, String> claimedMarks = new LinkedHashMap<>();
        for (PendingMark p : pendingMarks) {
            String id = p.definition.id();
            if (id != null && claimedMarks.containsKey(id)) {
                errors.add(new Error(id, "duplicate-id", "mark already registered by " + claimedMarks.get(id),
                        p.origin));
                continue;
            }
            if (id != null) {
                claimedMarks.put(id, p.origin);
            }
            List<Error> found = validateMark(p.definition, p.origin);
            if (found.isEmpty()) {
                marks.put(id, new MarkEntry(p.definition, p.origin, marks.size()));
            } else {
                errors.addAll(found);
                invalidMarkOrigins.put(found.get(0).id, p.origin);
            }
        }
        pendingMarks.clear();
        frozen = true;
        return Collections.unmodifiableList(errors);
    }

    List<Error> validate(StatusDefinition d, String origin) {
        List<Error> found = new ArrayList<>();
        String id = d.id() == null ? "<null>" : d.id();
        if (d.id() == null || !ID.matcher(d.id()).matches()) {
            found.add(new Error(id, "bad-id", "id must be non-empty and contain no whitespace", origin));
        }
        if (d.limit() < 1) {
            found.add(new Error(id, "bad-limit", "limit " + d.limit() + " is below 1", origin));
        }
        if (d.expiration() == null) {
            found.add(new Error(id, "unknown-mode", "expiration \"" + d.expirationText()
                    + "\" is not shared, gradual, independent, or none", origin));
        }
        if (d.refresh() == null) {
            found.add(new Error(id, "unknown-mode", "refresh \"" + d.refreshText()
                    + "\" is not reset, extend, or preserve", origin));
        }
        if (d.ownership() == null) {
            found.add(new Error(id, "unknown-mode", "ownership \"" + d.ownershipText()
                    + "\" is not shared or per_source", origin));
        }
        if (d.deathRule() == null) {
            found.add(new Error(id, "unknown-mode", "death rule \"" + d.deathRuleText()
                    + "\" is not clear or keep", origin));
        }
        if (d.expiration() != ExpirationMode.NONE && d.defaultDuration() < 1) {
            found.add(new Error(id, "bad-duration", "default duration " + d.defaultDuration()
                    + " is below 1 tick", origin));
        }
        if (d.maximumRemaining() != StatusDefinition.NO_MAXIMUM && d.maximumRemaining() < 1) {
            found.add(new Error(id, "bad-maximum", "maximum remaining time " + d.maximumRemaining()
                    + " is below 1 tick", origin));
        }
        if (d.onInterval() != null && d.interval() < 1) {
            found.add(new Error(id, "bad-interval", "interval " + d.interval() + " is below 1 tick", origin));
        }
        validateContributions(d.contributions(), Kind.STATUS, id, origin, found);
        validatePresentation(d.presentation(), id, origin, found);
        return found;
    }

    /** The kinds that declare contributions, each with the value forms it allows. */
    private enum Kind {
        STATUS("status", ValueForm.FIXED, ValueForm.PER_STACK),
        POOL("pool", ValueForm.FIXED, ValueForm.PER_AMOUNT, ValueForm.PER_FILL),
        MARK("mark", ValueForm.FIXED);

        final String text;
        final Set<ValueForm> forms;

        Kind(String text, ValueForm... forms) {
            this.text = text;
            this.forms = new HashSet<>(Arrays.asList(forms));
        }
    }

    /**
     * Checks declared contributions: operation, value form for the kind, value, and target. A contribution type
     * must be registered; a vanilla-namespace attribute id ({@code generic.}) must be a real vanilla attribute.
     * Other attribute ids are checked when the server starts ({@link #checkLateAttributes}).
     */
    private void validateContributions(List<Contribution> contributions, Kind kind, String id, String origin,
                                       List<Error> found) {
        for (Contribution c : contributions) {
            if (c == null || c.target() == null || !ID.matcher(c.target()).matches()) {
                found.add(new Error(id, "bad-contribution", "a contribution has no valid target id", origin));
                continue;
            }
            if (c.isAttribute()) {
                if (c.operation() == null) {
                    found.add(new Error(id, "unknown-mode", "attribute operation \"" + c.operationText()
                            + "\" is not add, multiply_base, or multiply_total", origin));
                }
                if (c.target().startsWith("generic.") && !VANILLA_ATTRIBUTES.contains(c.target())) {
                    found.add(new Error(id, "unknown-attribute", "attribute \"" + c.target()
                            + "\" is not a vanilla attribute", origin));
                }
            } else if (!knownType.test(c.target())) {
                found.add(new Error(id, "unknown-type", "contribution type \"" + c.target()
                        + "\" is not registered", origin));
            }
            ValueForm form = c.form();
            if (form == null) {
                found.add(new Error(id, "unknown-mode", "value form \"" + c.formText()
                        + "\" is not fixed, per_stack, per_amount, or per_fill", origin));
            } else if (!kind.forms.contains(form)) {
                found.add(new Error(id, "bad-form", "value form " + form.text() + " does not apply to a "
                        + kind.text, origin));
            }
            if (Double.isNaN(c.value()) || Double.isInfinite(c.value())) {
                found.add(new Error(id, "bad-value", "contribution value " + c.value() + " is not a number", origin));
            }
        }
    }

    /**
     * After the server starts: disables every valid status, pool, and mark definition with an attribute contribution
     * whose id is neither a vanilla attribute nor known to the server (by {@code known}), and reports them.
     */
    public synchronized List<Error> checkLateAttributes(Predicate<String> known) {
        List<Error> found = new ArrayList<>();
        for (Iterator<Entry> it = statuses.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            Error error = unknownAttribute(e.definition().id(), e.definition().contributions(), e.origin(), known);
            if (error != null) {
                it.remove();
                invalidOrigins.put(error.id, e.origin());
                found.add(error);
            }
        }
        for (Iterator<PoolEntry> it = pools.values().iterator(); it.hasNext(); ) {
            PoolEntry e = it.next();
            Error error = unknownAttribute(e.definition().id(), e.definition().contributions(), e.origin(), known);
            if (error != null) {
                it.remove();
                invalidPoolOrigins.put(error.id, e.origin());
                found.add(error);
            }
        }
        for (Iterator<MarkEntry> it = marks.values().iterator(); it.hasNext(); ) {
            MarkEntry e = it.next();
            List<Contribution> both = new ArrayList<>(e.definition().sourceContributions());
            both.addAll(e.definition().targetContributions());
            Error error = unknownAttribute(e.definition().id(), both, e.origin(), known);
            if (error != null) {
                it.remove();
                invalidMarkOrigins.put(error.id, e.origin());
                found.add(error);
            }
        }
        errors.addAll(found);
        return found;
    }

    @Nullable
    private static Error unknownAttribute(String id, List<Contribution> contributions, String origin,
                                          Predicate<String> known) {
        for (Contribution c : contributions) {
            if (c.isAttribute() && !VANILLA_ATTRIBUTES.contains(c.target()) && !known.test(c.target())) {
                return new Error(id, "unknown-attribute", "attribute \"" + c.target()
                        + "\" is not known to this server", origin);
            }
        }
        return null;
    }

    List<Error> validatePool(PoolDefinition d, String origin) {
        List<Error> found = new ArrayList<>();
        String id = d.id() == null ? "<null>" : d.id();
        if (d.id() == null || !ID.matcher(d.id()).matches()) {
            found.add(new Error(id, "bad-id", "id must be non-empty and contain no whitespace", origin));
        }
        if (!(d.capacity() > 0) || Double.isInfinite(d.capacity())) {
            found.add(new Error(id, "bad-capacity", "capacity " + d.capacity() + " is not above 0", origin));
        }
        if (d.start() == null) {
            found.add(new Error(id, "unknown-mode", "start \"" + d.startText() + "\" is not empty, full, or amount",
                    origin));
        }
        if (Double.isNaN(d.startAmount()) || Double.isInfinite(d.startAmount())) {
            found.add(new Error(id, "bad-amount", "start amount " + d.startAmount() + " is not a number", origin));
        }
        if (d.lifetime() != PoolDefinition.NO_LIFETIME && d.lifetime() < 1) {
            found.add(new Error(id, "bad-lifetime", "lifetime " + d.lifetime() + " is below 1 tick", origin));
        }
        Progression rule = d.progression();
        if (rule == null) {
            found.add(new Error(id, "unknown-mode", "progression \"" + d.progressionText()
                    + "\" is not none, fixed, percent_of_capacity, percent_of_current, or to_target", origin));
        } else if (rule == Progression.TO_TARGET) {
            if (d.targetDuration() < 1) {
                found.add(new Error(id, "bad-duration", "to_target duration " + d.targetDuration()
                        + " is below 1 tick", origin));
            }
            if (Double.isNaN(d.target()) || Double.isInfinite(d.target())) {
                found.add(new Error(id, "bad-amount", "target " + d.target() + " is not a number", origin));
            }
        } else if (rule != Progression.NONE) {
            if (d.direction() == null) {
                found.add(new Error(id, "unknown-mode", "direction \"" + d.directionText()
                        + "\" is not fill or drain", origin));
            }
            if (!(d.rate() > 0) || Double.isInfinite(d.rate())) {
                found.add(new Error(id, "bad-rate", "rate " + d.rate() + " is not above 0", origin));
            }
            if (rule == Progression.PERCENT_OF_CURRENT && d.direction() == Direction.DRAIN && d.rate() > 100) {
                found.add(new Error(id, "bad-rate", "a percent_of_current drain of " + d.rate()
                        + " percent is above 100", origin));
            }
        }
        if (d.cutoff() != PoolDefinition.DEFAULT_CUTOFF && !(d.cutoff() >= 0)) {
            found.add(new Error(id, "bad-cutoff", "cutoff " + d.cutoff() + " is below 0", origin));
        }
        if (d.deathRule() == null) {
            found.add(new Error(id, "unknown-mode", "death rule \"" + d.deathRuleText()
                    + "\" is not clear or keep", origin));
        }
        if (!(d.updateStep() > 0) || d.updateStep() > 100) {
            found.add(new Error(id, "bad-step", "update step " + d.updateStep() + " is not above 0 and at most 100",
                    origin));
        }
        validateContributions(d.contributions(), Kind.POOL, id, origin, found);
        validatePresentation(d.presentation(), id, origin, found);
        return found;
    }

    List<Error> validateMark(MarkDefinition d, String origin) {
        List<Error> found = new ArrayList<>();
        String id = d.id() == null ? "<null>" : d.id();
        if (d.id() == null || !ID.matcher(d.id()).matches()) {
            found.add(new Error(id, "bad-id", "id must be non-empty and contain no whitespace", origin));
        }
        if (d.targetLimit() != MarkDefinition.UNLIMITED && d.targetLimit() < 1) {
            found.add(new Error(id, "bad-limit", "target limit " + d.targetLimit() + " is below 1", origin));
        }
        if (d.overflow() == null) {
            found.add(new Error(id, "unknown-mode", "overflow \"" + d.overflowText()
                    + "\" is not replace_oldest or reject", origin));
        }
        if (d.refresh() == null) {
            found.add(new Error(id, "unknown-mode", "refresh \"" + d.refreshText()
                    + "\" is not reset, extend, or preserve", origin));
        }
        if (d.defaultDuration() != MarkDefinition.NO_DURATION && d.defaultDuration() < 1) {
            found.add(new Error(id, "bad-duration", "default duration " + d.defaultDuration()
                    + " is below 1 tick", origin));
        }
        if (d.maximumRemaining() != MarkDefinition.NO_MAXIMUM && d.maximumRemaining() < 1) {
            found.add(new Error(id, "bad-maximum", "maximum remaining time " + d.maximumRemaining()
                    + " is below 1 tick", origin));
        }
        if (!(d.maximumDistance() >= 0) || Double.isInfinite(d.maximumDistance())) {
            found.add(new Error(id, "bad-distance", "maximum distance " + d.maximumDistance()
                    + " is not 0 or more", origin));
        }
        if (d.maximumInactive() < 0) {
            found.add(new Error(id, "bad-inactive", "maximum inactive time " + d.maximumInactive()
                    + " is below 0", origin));
        }
        if (d.onInterval() != null && d.interval() < 1) {
            found.add(new Error(id, "bad-interval", "interval " + d.interval() + " is below 1 tick", origin));
        }
        for (MarkSide side : MarkSide.values()) {
            validateContributions(d.contributions(side), Kind.MARK, id, origin, found);
        }
        validatePresentation(d.presentation(), id, origin, found);
        return found;
    }

    static List<Error> validateCooldown(CooldownDefinition d, String origin) {
        List<Error> found = new ArrayList<>();
        String id = d.id() == null ? "<null>" : d.id();
        if (d.id() == null || !ID.matcher(d.id()).matches()) {
            found.add(new Error(id, "bad-id", "id must be non-empty and contain no whitespace", origin));
        }
        if (d.defaultDuration() < 1) {
            found.add(new Error(id, "bad-duration", "default duration " + d.defaultDuration() + " is below 1 tick",
                    origin));
        }
        if (d.deathRule() == null) {
            found.add(new Error(id, "unknown-mode", "death rule \"" + d.deathRuleText()
                    + "\" is not clear or keep", origin));
        }
        validatePresentation(d.presentation(), id, origin, found);
        return found;
    }

    /** Checks the presentation: decimals 0 to 2, an icon id or texture without whitespace, text present. */
    private static void validatePresentation(@Nullable Presentation p, String id, String origin, List<Error> found) {
        if (p == null) {
            found.add(new Error(id, "bad-presentation", "presentation is null", origin));
            return;
        }
        if (p.decimals() < 0 || p.decimals() > 2) {
            found.add(new Error(id, "bad-presentation", "decimals " + p.decimals() + " is not 0, 1, or 2", origin));
        }
        if (p.iconItem() != null && !ID.matcher(p.iconItem()).matches()) {
            found.add(new Error(id, "bad-presentation", "icon item id \"" + p.iconItem() + "\" is not an id", origin));
        }
        if (p.iconTexture() != null && !ID.matcher(p.iconTexture()).matches()) {
            found.add(new Error(id, "bad-presentation", "icon texture \"" + p.iconTexture() + "\" is not a resource"
                    + " location", origin));
        }
        if (p.iconMeta() < 0) {
            found.add(new Error(id, "bad-presentation", "icon metadata " + p.iconMeta() + " is below 0", origin));
        }
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    /** The valid status definition with this id, or null. */
    @Nullable
    public synchronized Entry status(String id) {
        return statuses.get(id);
    }

    /** Whether the id was registered but disabled as invalid. */
    public synchronized boolean isInvalid(String id) {
        return invalidOrigins.containsKey(id);
    }

    /** Valid status definitions in registration order. */
    public synchronized Collection<Entry> statuses() {
        return Collections.unmodifiableCollection(new ArrayList<>(statuses.values()));
    }

    /** The valid pool definition with this id, or null. */
    @Nullable
    public synchronized PoolEntry pool(String id) {
        return pools.get(id);
    }

    /** Whether the pool id was registered but disabled as invalid. */
    public synchronized boolean isInvalidPool(String id) {
        return invalidPoolOrigins.containsKey(id);
    }

    /** Valid pool definitions in registration order. */
    public synchronized Collection<PoolEntry> pools() {
        return Collections.unmodifiableCollection(new ArrayList<>(pools.values()));
    }

    /** Ids of disabled pool definitions with their origins. */
    public synchronized Map<String, String> invalidPools() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(invalidPoolOrigins));
    }

    /** The valid cooldown definition with this id, or null. */
    @Nullable
    public synchronized CooldownEntry cooldown(String id) {
        return cooldowns.get(id);
    }

    /** Whether the cooldown id was registered but disabled as invalid. */
    public synchronized boolean isInvalidCooldown(String id) {
        return invalidCooldownOrigins.containsKey(id);
    }

    /** Valid cooldown definitions in registration order. */
    public synchronized Collection<CooldownEntry> cooldowns() {
        return Collections.unmodifiableCollection(new ArrayList<>(cooldowns.values()));
    }

    /** Ids of disabled cooldown definitions with their origins. */
    public synchronized Map<String, String> invalidCooldowns() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(invalidCooldownOrigins));
    }

    /** The valid mark definition with this id, or null. */
    @Nullable
    public synchronized MarkEntry mark(String id) {
        return marks.get(id);
    }

    /** Whether the mark id was registered but disabled as invalid. */
    public synchronized boolean isInvalidMark(String id) {
        return invalidMarkOrigins.containsKey(id);
    }

    /** Valid mark definitions in registration order. */
    public synchronized Collection<MarkEntry> marks() {
        return Collections.unmodifiableCollection(new ArrayList<>(marks.values()));
    }

    /** Ids of disabled mark definitions with their origins. */
    public synchronized Map<String, String> invalidMarks() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(invalidMarkOrigins));
    }

    /** Ids of disabled definitions with their origins. */
    public synchronized Map<String, String> invalid() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(invalidOrigins));
    }

    public synchronized List<Error> errors() {
        return Collections.unmodifiableList(new ArrayList<>(errors));
    }
}
