package com.mahghuuuls.statusworks.core.definition;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collects definitions during startup, validates them once when they freeze, and answers lookups afterwards.
 *
 * <p>This class owns what makes a definition valid, which registration wins for a duplicate id (the first, even
 * when it is invalid, so a broken definition is reported rather than silently replaced),
 * the registration order every tick follows, and the freeze. After the freeze it never changes.</p>
 */
public final class DefinitionRegistry {

    /** An id is any non-empty text without whitespace, so it can be typed as one command argument. */
    private static final Pattern ID = Pattern.compile("\\S+");

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

    private static final class Pending {
        final StatusDefinition definition;
        final String origin;

        Pending(StatusDefinition definition, String origin) {
            this.definition = definition;
            this.origin = origin;
        }
    }

    private final List<Pending> pending = new ArrayList<>();
    private final Map<String, Entry> statuses = new LinkedHashMap<>();
    private final Map<String, String> invalidOrigins = new LinkedHashMap<>();
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
        frozen = true;
        return Collections.unmodifiableList(errors);
    }

    static List<Error> validate(StatusDefinition d, String origin) {
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
        return found;
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

    /** Ids of disabled definitions with their origins. */
    public synchronized Map<String, String> invalid() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(invalidOrigins));
    }

    public synchronized List<Error> errors() {
        return Collections.unmodifiableList(new ArrayList<>(errors));
    }
}
