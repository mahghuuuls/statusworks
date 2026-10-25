package com.mahghuuuls.statusworks.api.definition;

import com.mahghuuuls.statusworks.api.callback.MarkActiveCallback;
import com.mahghuuuls.statusworks.api.callback.MarkAppliedCallback;
import com.mahghuuuls.statusworks.api.callback.MarkInactiveCallback;
import com.mahghuuuls.statusworks.api.callback.MarkIntervalCallback;
import com.mahghuuuls.statusworks.api.callback.MarkRemovedCallback;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable mark definition: a relation from one living entity (the source) to another (the target). A source
 * has at most one mark of a definition on a given target, and at most its target limit of such marks at once.
 * A mark grants nothing by itself; only its managed contributions and callbacks act.
 *
 * <p>A mark is active only while both participants are loaded in the same dimension and, when the definition
 * sets one, within the maximum distance. An inactive mark keeps existing: its timer and interval pause, and its
 * contributions are withdrawn until it is active again. Statusworks never loads a chunk or a dimension for a
 * mark.</p>
 *
 * <p>Build one with {@link #builder(String)} and register it through
 * {@link com.mahghuuuls.statusworks.api.StatusworksApi#registerMark(MarkDefinition)} during game startup. The
 * registry validates it when definitions freeze.</p>
 */
public final class MarkDefinition {

    /** A target limit of "unlimited". */
    public static final int UNLIMITED = -1;
    /** A duration of "none": the mark stays until a rule or an operation removes it. */
    public static final int NO_DURATION = -1;
    /** Marks "no maximum remaining time". */
    public static final int NO_MAXIMUM = -1;

    private final String id;
    private final int targetLimit;
    @Nullable private final Overflow overflow;
    private final String overflowText;
    @Nullable private final RefreshRule refresh;
    private final String refreshText;
    private final int maximumRemaining;
    private final int defaultDuration;
    private final boolean keepOnSourceDeath;
    private final boolean keepOnTargetDeath;
    private final double maximumDistance;
    private final int maximumInactive;
    private final int interval;
    @Nullable private final MarkIntervalCallback onInterval;
    @Nullable private final MarkAppliedCallback onApplied;
    @Nullable private final MarkInactiveCallback onInactive;
    @Nullable private final MarkActiveCallback onActive;
    @Nullable private final MarkRemovedCallback onRemoved;
    private final List<Contribution> sourceContributions;
    private final List<Contribution> targetContributions;
    private final Presentation presentation;

    private MarkDefinition(Builder b) {
        this.id = b.id;
        this.targetLimit = b.targetLimit;
        this.overflowText = b.overflowText;
        this.overflow = Overflow.fromText(b.overflowText);
        this.refreshText = b.refreshText;
        this.refresh = RefreshRule.fromText(b.refreshText);
        this.maximumRemaining = b.maximumRemaining;
        this.defaultDuration = b.defaultDuration;
        this.keepOnSourceDeath = b.keepOnSourceDeath;
        this.keepOnTargetDeath = b.keepOnTargetDeath;
        this.maximumDistance = b.maximumDistance;
        this.maximumInactive = b.maximumInactive;
        this.interval = b.interval;
        this.onInterval = b.onInterval;
        this.onApplied = b.onApplied;
        this.onInactive = b.onInactive;
        this.onActive = b.onActive;
        this.onRemoved = b.onRemoved;
        this.sourceContributions = Collections.unmodifiableList(new ArrayList<>(b.sourceContributions));
        this.targetContributions = Collections.unmodifiableList(new ArrayList<>(b.targetContributions));
        this.presentation = b.presentation;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** How many targets one source can mark at once with this definition, or {@link #UNLIMITED}. */
    public int targetLimit() {
        return targetLimit;
    }

    /** What a new target does at the limit, or null when the text given to the builder named no rule. */
    @Nullable
    public Overflow overflow() {
        return overflow;
    }

    public String overflowText() {
        return overflowText;
    }

    /** What marking an already marked target does to its timer, or null when the text named no rule. */
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

    /** Duration in active ticks of a mark whose call gives none, or {@link #NO_DURATION}. */
    public int defaultDuration() {
        return defaultDuration;
    }

    /** Whether a mark survives the death of its source when the source is a player. */
    public boolean keepOnSourceDeath() {
        return keepOnSourceDeath;
    }

    /** Whether a mark survives the death of its target when the target is a player. */
    public boolean keepOnTargetDeath() {
        return keepOnTargetDeath;
    }

    /** Beyond this distance in blocks the mark is inactive; 0 means no maximum. */
    public double maximumDistance() {
        return maximumDistance;
    }

    /**
     * Server ticks a mark may stay inactive before it ends with {@code inactive_timeout}; 0 means no maximum.
     * Time inactive only because of the maximum distance does not count: distance alone never ends a mark.
     */
    public int maximumInactive() {
        return maximumInactive;
    }

    /** Active ticks between interval callbacks, or 0 when there is no interval callback. */
    public int interval() {
        return interval;
    }

    @Nullable
    public MarkIntervalCallback onInterval() {
        return onInterval;
    }

    @Nullable
    public MarkAppliedCallback onApplied() {
        return onApplied;
    }

    @Nullable
    public MarkInactiveCallback onInactive() {
        return onInactive;
    }

    @Nullable
    public MarkActiveCallback onActive() {
        return onActive;
    }

    @Nullable
    public MarkRemovedCallback onRemoved() {
        return onRemoved;
    }

    /** Contributions applied to the source while the mark is active, one per active mark. */
    public List<Contribution> sourceContributions() {
        return sourceContributions;
    }

    /** Contributions applied to the target while the mark is active, one per active mark. */
    public List<Contribution> targetContributions() {
        return targetContributions;
    }

    /** How the definition looks on the HUD and detail view; {@link Presentation#DEFAULT} unless set. */
    public Presentation presentation() {
        return presentation;
    }

    /** The contributions of one side. */
    public List<Contribution> contributions(MarkSide side) {
        return side == MarkSide.SOURCE ? sourceContributions : targetContributions;
    }

    /**
     * Builder with the documented defaults: target limit 1, {@code replace_oldest}, {@code reset}, 200 ticks, no
     * maximum, ends on either participant's death, no maximum distance, no maximum inactive time.
     */
    public static final class Builder {
        private final String id;
        private int targetLimit = 1;
        private String overflowText = Overflow.REPLACE_OLDEST.text();
        private String refreshText = RefreshRule.RESET.text();
        private int maximumRemaining = NO_MAXIMUM;
        private int defaultDuration = 200;
        private boolean keepOnSourceDeath;
        private boolean keepOnTargetDeath;
        private double maximumDistance;
        private int maximumInactive;
        private int interval;
        private MarkIntervalCallback onInterval;
        private MarkAppliedCallback onApplied;
        private MarkInactiveCallback onInactive;
        private MarkActiveCallback onActive;
        private MarkRemovedCallback onRemoved;
        private final List<Contribution> sourceContributions = new ArrayList<>();
        private final List<Contribution> targetContributions = new ArrayList<>();
        private Presentation presentation = Presentation.DEFAULT;

        private Builder(String id) {
            this.id = id;
        }

        /** Targets one source can mark at once: 1 or more. */
        public Builder targetLimit(int targets) {
            this.targetLimit = targets;
            return this;
        }

        /** No target limit. */
        public Builder unlimitedTargets() {
            this.targetLimit = UNLIMITED;
            return this;
        }

        public Builder overflow(Overflow rule) {
            this.overflowText = rule.text();
            return this;
        }

        /** Sets the overflow rule by its text value; an unknown value makes the definition invalid. */
        public Builder overflow(String text) {
            this.overflowText = text;
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

        /** The most remaining time an {@code extend} refresh can reach. */
        public Builder maximumRemaining(int ticks) {
            this.maximumRemaining = ticks;
            return this;
        }

        public Builder defaultDuration(int ticks) {
            this.defaultDuration = ticks;
            return this;
        }

        /** Marks whose call gives no duration never expire; a rule or an operation ends them. */
        public Builder noDuration() {
            this.defaultDuration = NO_DURATION;
            return this;
        }

        /** Keeps the mark when its source player dies (it is inactive until the respawn). */
        public Builder keepOnSourceDeath(boolean keep) {
            this.keepOnSourceDeath = keep;
            return this;
        }

        /** Keeps the mark when its target player dies (it is inactive until the respawn). */
        public Builder keepOnTargetDeath(boolean keep) {
            this.keepOnTargetDeath = keep;
            return this;
        }

        /** Beyond this distance in blocks the mark is inactive; 0 (the default) means no maximum. */
        public Builder maximumDistance(double blocks) {
            this.maximumDistance = blocks;
            return this;
        }

        /** Server ticks the mark may stay inactive before it ends; 0 (the default) means no maximum. */
        public Builder maximumInactive(int ticks) {
            this.maximumInactive = ticks;
            return this;
        }

        /** Runs every {@code ticks} active ticks while the mark is active. */
        public Builder onInterval(int ticks, MarkIntervalCallback callback) {
            this.interval = ticks;
            this.onInterval = callback;
            return this;
        }

        public Builder onApplied(MarkAppliedCallback callback) {
            this.onApplied = callback;
            return this;
        }

        public Builder onInactive(MarkInactiveCallback callback) {
            this.onInactive = callback;
            return this;
        }

        public Builder onActive(MarkActiveCallback callback) {
            this.onActive = callback;
            return this;
        }

        public Builder onRemoved(MarkRemovedCallback callback) {
            this.onRemoved = callback;
            return this;
        }

        /** Adds a contribution to one side while the mark is active; its value form must be {@code fixed}. */
        public Builder contribute(MarkSide side, Contribution contribution) {
            (side == MarkSide.SOURCE ? sourceContributions : targetContributions).add(contribution);
            return this;
        }

        /** How the definition looks on the players' HUD and detail view. */
        public Builder presentation(Presentation presentation) {
            this.presentation = presentation;
            return this;
        }

        public MarkDefinition build() {
            return new MarkDefinition(this);
        }
    }
}
