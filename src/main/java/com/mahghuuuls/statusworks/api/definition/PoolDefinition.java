package com.mahghuuuls.statusworks.api.definition;

import com.mahghuuuls.statusworks.api.callback.PoolAmountCallback;
import com.mahghuuuls.statusworks.api.callback.PoolEmptyCallback;
import com.mahghuuuls.statusworks.api.callback.PoolFullCallback;
import com.mahghuuuls.statusworks.api.callback.PoolRemovedCallback;
import com.mahghuuuls.statusworks.api.callback.PoolTargetCallback;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable pool definition: a named decimal amount between 0 and a capacity, with an optional progression
 * rule and lifetime.
 *
 * <p>Build one with {@link #builder(String)} and register it through
 * {@link com.mahghuuuls.statusworks.api.StatusworksApi#registerPool(PoolDefinition)} during game startup. The
 * builder accepts any values; the registry validates them when definitions freeze and disables an invalid
 * definition with an error that names it.</p>
 *
 * <p>Rates are per second of active time (20 ticks). For {@code percent_of_capacity} and
 * {@code percent_of_current} the rate is a percentage, so 10 means 10 percent. A {@code percent_of_current} fill
 * from 0 stays at 0.</p>
 */
public final class PoolDefinition {

    /** Marks "no lifetime" and "default cutoff". */
    public static final int NO_LIFETIME = -1;
    public static final double DEFAULT_CUTOFF = -1;
    /** The default contribution update step: 1 percent of the capacity. */
    public static final double DEFAULT_UPDATE_STEP = 1;

    private final String id;
    private final double capacity;
    @Nullable private final PoolStart start;
    private final String startText;
    private final double startAmount;
    private final int lifetime;
    @Nullable private final Progression progression;
    private final String progressionText;
    @Nullable private final Direction direction;
    private final String directionText;
    private final double rate;
    private final double target;
    private final int targetDuration;
    private final double cutoff;
    @Nullable private final DeathRule deathRule;
    private final String deathRuleText;
    @Nullable private final PoolAmountCallback onAmountChanged;
    @Nullable private final PoolEmptyCallback onEmpty;
    @Nullable private final PoolFullCallback onFull;
    @Nullable private final PoolTargetCallback onTargetReached;
    @Nullable private final PoolRemovedCallback onRemoved;
    private final List<Contribution> contributions;
    private final Presentation presentation;
    private final double updateStep;

    private PoolDefinition(Builder b) {
        this.id = b.id;
        this.capacity = b.capacity;
        this.startText = b.startText;
        this.start = PoolStart.fromText(b.startText);
        this.startAmount = b.startAmount;
        this.lifetime = b.lifetime;
        this.progressionText = b.progressionText;
        this.progression = Progression.fromText(b.progressionText);
        this.directionText = b.directionText;
        this.direction = Direction.fromText(b.directionText);
        this.rate = b.rate;
        this.target = b.target;
        this.targetDuration = b.targetDuration;
        this.cutoff = b.cutoff;
        this.deathRuleText = b.deathRuleText;
        this.deathRule = DeathRule.fromText(b.deathRuleText);
        this.onAmountChanged = b.onAmountChanged;
        this.onEmpty = b.onEmpty;
        this.onFull = b.onFull;
        this.onTargetReached = b.onTargetReached;
        this.onRemoved = b.onRemoved;
        this.contributions = Collections.unmodifiableList(new ArrayList<>(b.contributions));
        this.presentation = b.presentation;
        this.updateStep = b.updateStep;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** The capacity a new instance starts with. */
    public double capacity() {
        return capacity;
    }

    /** The start rule, or null when the text given to the builder named none. */
    @Nullable
    public PoolStart start() {
        return start;
    }

    public String startText() {
        return startText;
    }

    /** The starting amount for the {@code amount} start rule. */
    public double startAmount() {
        return startAmount;
    }

    /** Lifetime of a new instance in active ticks, or {@link #NO_LIFETIME}. */
    public int lifetime() {
        return lifetime;
    }

    /** The progression rule, or null when the text given to the builder named none. */
    @Nullable
    public Progression progression() {
        return progression;
    }

    public String progressionText() {
        return progressionText;
    }

    /** Fill or drain, or null when the text given to the builder named neither. */
    @Nullable
    public Direction direction() {
        return direction;
    }

    public String directionText() {
        return directionText;
    }

    /** Amount per second for {@code fixed}; percentage per second for the two percent rules. */
    public double rate() {
        return rate;
    }

    /** Target amount of a {@code to_target} rule, clamped to the range when used. */
    public double target() {
        return target;
    }

    /** Active ticks a {@code to_target} rule takes from creation to its target. */
    public int targetDuration() {
        return targetDuration;
    }

    /** Amount below which a {@code percent_of_current} drain snaps to 0, or {@link #DEFAULT_CUTOFF}. */
    public double cutoff() {
        return cutoff;
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
    public PoolAmountCallback onAmountChanged() {
        return onAmountChanged;
    }

    @Nullable
    public PoolEmptyCallback onEmpty() {
        return onEmpty;
    }

    @Nullable
    public PoolFullCallback onFull() {
        return onFull;
    }

    @Nullable
    public PoolTargetCallback onTargetReached() {
        return onTargetReached;
    }

    @Nullable
    public PoolRemovedCallback onRemoved() {
        return onRemoved;
    }

    /** How the definition looks on the HUD and detail view; {@link Presentation#DEFAULT} unless set. */
    public Presentation presentation() {
        return presentation;
    }

    /** The managed contributions, in declaration order. */
    public List<Contribution> contributions() {
        return contributions;
    }

    /**
     * How far, in percent of the capacity, the fill moves before a pool-driven contribution changes; it always
     * changes at empty and full.
     */
    public double updateStep() {
        return updateStep;
    }

    /**
     * Builder with the documented defaults: capacity 1, start {@code empty}, no lifetime, no progression,
     * direction {@code fill}, default cutoff (0.1 percent of the capacity), {@code clear} on player death.
     */
    public static final class Builder {
        private final String id;
        private double capacity = 1;
        private String startText = PoolStart.EMPTY.text();
        private double startAmount;
        private int lifetime = NO_LIFETIME;
        private String progressionText = Progression.NONE.text();
        private String directionText = Direction.FILL.text();
        private double rate;
        private double target;
        private int targetDuration;
        private double cutoff = DEFAULT_CUTOFF;
        private String deathRuleText = DeathRule.CLEAR.text();
        private PoolAmountCallback onAmountChanged;
        private PoolEmptyCallback onEmpty;
        private PoolFullCallback onFull;
        private PoolTargetCallback onTargetReached;
        private PoolRemovedCallback onRemoved;
        private final List<Contribution> contributions = new ArrayList<>();
        private Presentation presentation = Presentation.DEFAULT;
        private double updateStep = DEFAULT_UPDATE_STEP;

        private Builder(String id) {
            this.id = id;
        }

        public Builder capacity(double capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder start(PoolStart start) {
            this.startText = start.text();
            return this;
        }

        /** Sets the start rule by its text value; an unknown value makes the definition invalid. */
        public Builder start(String text) {
            this.startText = text;
            return this;
        }

        /** Starts new instances at this amount (the {@code amount} start rule), clamped to the range. */
        public Builder startAmount(double amount) {
            this.startText = PoolStart.AMOUNT.text();
            this.startAmount = amount;
            return this;
        }

        /** Gives new instances a lifetime in active ticks. */
        public Builder lifetime(int ticks) {
            this.lifetime = ticks;
            return this;
        }

        /** A {@code fixed} rule: {@code amountPerSecond} per second, filling or draining. */
        public Builder fixed(Direction direction, double amountPerSecond) {
            return rule(Progression.FIXED, direction, amountPerSecond);
        }

        /** A {@code percent_of_capacity} rule: {@code percentPerSecond} percent of the capacity per second. */
        public Builder percentOfCapacity(Direction direction, double percentPerSecond) {
            return rule(Progression.PERCENT_OF_CAPACITY, direction, percentPerSecond);
        }

        /** A {@code percent_of_current} rule: {@code percentPerSecond} percent of the amount per second. */
        public Builder percentOfCurrent(Direction direction, double percentPerSecond) {
            return rule(Progression.PERCENT_OF_CURRENT, direction, percentPerSecond);
        }

        /**
         * A {@code to_target} rule: reach {@code target} {@code ticks} active ticks after creation. Each step moves
         * the amount toward the target over the remaining time, so an outside change is recovered by the deadline.
         * "Target reached" runs at the deadline, even when an operation reached the target earlier; after the
         * deadline the rule is done, and later changes are not undone.
         */
        public Builder toTarget(double target, int ticks) {
            this.progressionText = Progression.TO_TARGET.text();
            this.target = target;
            this.targetDuration = ticks;
            return this;
        }

        /** Sets the progression rule by text values; unknown values make the definition invalid. */
        public Builder progression(String rule, String direction, double rate) {
            this.progressionText = rule;
            this.directionText = direction;
            this.rate = rate;
            return this;
        }

        private Builder rule(Progression rule, Direction direction, double rate) {
            this.progressionText = rule.text();
            this.directionText = direction.text();
            this.rate = rate;
            return this;
        }

        /**
         * The amount below which a {@code percent_of_current} drain snaps to 0. Every pool amount within a billionth
         * of the capacity of 0 already counts as 0, so a smaller cutoff (0 included) acts as that billionth.
         */
        public Builder cutoff(double amount) {
            this.cutoff = amount;
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

        public Builder onAmountChanged(PoolAmountCallback callback) {
            this.onAmountChanged = callback;
            return this;
        }

        public Builder onEmpty(PoolEmptyCallback callback) {
            this.onEmpty = callback;
            return this;
        }

        public Builder onFull(PoolFullCallback callback) {
            this.onFull = callback;
            return this;
        }

        public Builder onTargetReached(PoolTargetCallback callback) {
            this.onTargetReached = callback;
            return this;
        }

        public Builder onRemoved(PoolRemovedCallback callback) {
            this.onRemoved = callback;
            return this;
        }

        /** Adds a managed contribution; {@code fixed}, {@code per_amount}, and {@code per_fill} apply to pools. */
        public Builder contribute(Contribution contribution) {
            this.contributions.add(contribution);
            return this;
        }

        /** The contribution update step in percent of the capacity (default 1). */
        public Builder updateStep(double percentOfCapacity) {
            this.updateStep = percentOfCapacity;
            return this;
        }

        /** How the definition looks on the players' HUD and detail view. */
        public Builder presentation(Presentation presentation) {
            this.presentation = presentation;
            return this;
        }

        public PoolDefinition build() {
            return new PoolDefinition(this);
        }
    }
}
