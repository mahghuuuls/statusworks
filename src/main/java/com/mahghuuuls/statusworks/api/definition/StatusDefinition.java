package com.mahghuuuls.statusworks.api.definition;

import com.mahghuuuls.statusworks.api.callback.StatusAppliedCallback;
import com.mahghuuuls.statusworks.api.callback.StatusIntervalCallback;
import com.mahghuuuls.statusworks.api.callback.StatusQuantityCallback;
import com.mahghuuuls.statusworks.api.callback.StatusRemovedCallback;

import javax.annotation.Nullable;

/**
 * An immutable status definition: a named condition with a count of stacks and the rules for its timers.
 *
 * <p>Build one with {@link #builder(String)} and register it through
 * {@link com.mahghuuuls.statusworks.api.StatusworksApi#registerStatus(StatusDefinition)} during game startup.
 * The builder accepts any values; the registry validates them when definitions freeze and disables an invalid
 * definition with an error that names it.</p>
 */
public final class StatusDefinition {

    /** Marks "no maximum remaining time". */
    public static final int NO_MAXIMUM = -1;

    private final String id;
    private final int limit;
    private final int defaultDuration;
    @Nullable private final ExpirationMode expiration;
    private final String expirationText;
    @Nullable private final RefreshRule refresh;
    private final String refreshText;
    private final int maximumRemaining;
    private final boolean refreshOnReject;
    @Nullable private final Ownership ownership;
    private final String ownershipText;
    @Nullable private final DeathRule deathRule;
    private final String deathRuleText;
    @Nullable private final StatusAppliedCallback onApplied;
    @Nullable private final StatusQuantityCallback onQuantityChanged;
    @Nullable private final StatusRemovedCallback onRemoved;
    private final int interval;
    @Nullable private final StatusIntervalCallback onInterval;

    private StatusDefinition(Builder builder) {
        this.id = builder.id;
        this.limit = builder.limit;
        this.defaultDuration = builder.defaultDuration;
        this.expirationText = builder.expirationText;
        this.expiration = ExpirationMode.fromText(builder.expirationText);
        this.refreshText = builder.refreshText;
        this.refresh = RefreshRule.fromText(builder.refreshText);
        this.maximumRemaining = builder.maximumRemaining;
        this.refreshOnReject = builder.refreshOnReject;
        this.ownershipText = builder.ownershipText;
        this.ownership = Ownership.fromText(builder.ownershipText);
        this.deathRuleText = builder.deathRuleText;
        this.deathRule = DeathRule.fromText(builder.deathRuleText);
        this.onApplied = builder.onApplied;
        this.onQuantityChanged = builder.onQuantityChanged;
        this.onRemoved = builder.onRemoved;
        this.interval = builder.interval;
        this.onInterval = builder.onInterval;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** The most stacks one group can hold. */
    public int limit() {
        return limit;
    }

    /** The duration, in ticks, of an application that gives none. */
    public int defaultDuration() {
        return defaultDuration;
    }

    /** The expiration mode, or null when the text given to the builder named no mode. */
    @Nullable
    public ExpirationMode expiration() {
        return expiration;
    }

    public String expirationText() {
        return expirationText;
    }

    /** The refresh rule, or null when the text given to the builder named no rule. */
    @Nullable
    public RefreshRule refresh() {
        return refresh;
    }

    public String refreshText() {
        return refreshText;
    }

    /** The most remaining time, in ticks, an {@code extend} refresh can reach, or {@link #NO_MAXIMUM}. */
    public int maximumRemaining() {
        return maximumRemaining;
    }

    /** Whether an application whose stacks are all rejected still applies the refresh rule. */
    public boolean refreshOnReject() {
        return refreshOnReject;
    }

    /** The ownership, or null when the text given to the builder named none. */
    @Nullable
    public Ownership ownership() {
        return ownership;
    }

    public String ownershipText() {
        return ownershipText;
    }

    /** What a player's death does to the instance, or null when the text given to the builder named no rule. */
    @Nullable
    public DeathRule deathRule() {
        return deathRule;
    }

    public String deathRuleText() {
        return deathRuleText;
    }

    @Nullable
    public StatusAppliedCallback onApplied() {
        return onApplied;
    }

    @Nullable
    public StatusQuantityCallback onQuantityChanged() {
        return onQuantityChanged;
    }

    @Nullable
    public StatusRemovedCallback onRemoved() {
        return onRemoved;
    }

    /** Active ticks between interval callbacks, or 0 when there is no interval callback. */
    public int interval() {
        return interval;
    }

    @Nullable
    public StatusIntervalCallback onInterval() {
        return onInterval;
    }

    /**
     * Builder with the documented defaults: limit 1, 200 ticks, shared expiration, reset, shared ownership, and
     * {@code clear} on player death.
     */
    public static final class Builder {
        private final String id;
        private int limit = 1;
        private int defaultDuration = 200;
        private String expirationText = ExpirationMode.SHARED.text();
        private String refreshText = RefreshRule.RESET.text();
        private int maximumRemaining = NO_MAXIMUM;
        private boolean refreshOnReject = true;
        private String ownershipText = Ownership.SHARED.text();
        private String deathRuleText = DeathRule.CLEAR.text();
        private StatusAppliedCallback onApplied;
        private StatusQuantityCallback onQuantityChanged;
        private StatusRemovedCallback onRemoved;
        private int interval;
        private StatusIntervalCallback onInterval;

        private Builder(String id) {
            this.id = id;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder defaultDuration(int ticks) {
            this.defaultDuration = ticks;
            return this;
        }

        public Builder expiration(ExpirationMode mode) {
            this.expirationText = mode.text();
            return this;
        }

        /** Sets the expiration mode by its text value; an unknown value makes the definition invalid. */
        public Builder expiration(String text) {
            this.expirationText = text;
            return this;
        }

        public Builder refresh(RefreshRule rule) {
            this.refreshText = rule.text();
            return this;
        }

        /** Sets the refresh rule by its text value; an unknown value makes the definition invalid. */
        public Builder refresh(String text) {
            this.refreshText = text;
            return this;
        }

        public Builder maximumRemaining(int ticks) {
            this.maximumRemaining = ticks;
            return this;
        }

        public Builder refreshOnReject(boolean refreshOnReject) {
            this.refreshOnReject = refreshOnReject;
            return this;
        }

        public Builder ownership(Ownership ownership) {
            this.ownershipText = ownership.text();
            return this;
        }

        /** Sets the ownership by its text value; an unknown value makes the definition invalid. */
        public Builder ownership(String text) {
            this.ownershipText = text;
            return this;
        }

        /** Sets what a player's death does: {@code clear} (the default) or {@code keep}. */
        public Builder deathRule(DeathRule rule) {
            this.deathRuleText = rule.text();
            return this;
        }

        /** Sets the death rule by its text value; an unknown value makes the definition invalid. */
        public Builder deathRule(String text) {
            this.deathRuleText = text;
            return this;
        }

        /** Runs once when the status is first applied to an entity. */
        public Builder onApplied(StatusAppliedCallback callback) {
            this.onApplied = callback;
            return this;
        }

        /** Runs on every change of the total count. */
        public Builder onQuantityChanged(StatusQuantityCallback callback) {
            this.onQuantityChanged = callback;
            return this;
        }

        /** Runs when the status ends, with the removal reason. */
        public Builder onRemoved(StatusRemovedCallback callback) {
            this.onRemoved = callback;
            return this;
        }

        /** Runs the callback every {@code ticks} active ticks while the status exists. */
        public Builder onInterval(int ticks, StatusIntervalCallback callback) {
            this.interval = ticks;
            this.onInterval = callback;
            return this;
        }

        public StatusDefinition build() {
            return new StatusDefinition(this);
        }
    }
}
