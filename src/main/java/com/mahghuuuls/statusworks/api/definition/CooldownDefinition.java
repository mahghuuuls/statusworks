package com.mahghuuuls.statusworks.api.definition;

import com.mahghuuuls.statusworks.api.callback.CooldownEndedCallback;

import javax.annotation.Nullable;

/**
 * An immutable cooldown definition: a named timer on an entity that is either running or not. Several actions
 * may share one cooldown. A cooldown carries no managed contributions; the builder has no option for them.
 *
 * <p>Build one with {@link #builder(String)} and register it through
 * {@link com.mahghuuuls.statusworks.api.StatusworksApi#registerCooldown(CooldownDefinition)} during game
 * startup. The registry validates it when definitions freeze.</p>
 */
public final class CooldownDefinition {

    private final String id;
    private final int defaultDuration;
    @Nullable private final DeathRule deathRule;
    private final String deathRuleText;
    @Nullable private final CooldownEndedCallback onEnded;
    private final Presentation presentation;
    private final int contributionCount;

    private CooldownDefinition(Builder b) {
        this.id = b.id;
        this.defaultDuration = b.defaultDuration;
        this.deathRuleText = b.deathRuleText;
        this.deathRule = DeathRule.fromText(b.deathRuleText);
        this.onEnded = b.onEnded;
        this.presentation = b.presentation;
        this.contributionCount = b.contributionCount;
    }

    /** Contributions given to the builder; any makes the definition invalid (cooldowns carry none). */
    public int contributionCount() {
        return contributionCount;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** Duration in active ticks of a start that gives none. */
    public int defaultDuration() {
        return defaultDuration;
    }

    /** What a player's death does: {@code keep} (the default for cooldowns) or {@code clear}; null when unknown. */
    @Nullable
    public DeathRule deathRule() {
        return deathRule;
    }

    public String deathRuleText() {
        return deathRuleText;
    }

    /** How the definition looks on the HUD and detail view; {@link Presentation#DEFAULT} unless set. */
    public Presentation presentation() {
        return presentation;
    }

    @Nullable
    public CooldownEndedCallback onEnded() {
        return onEnded;
    }

    /** Builder with the documented defaults: 200 ticks, {@code keep} on player death. */
    public static final class Builder {
        private final String id;
        private int defaultDuration = 200;
        private String deathRuleText = DeathRule.KEEP.text();
        private CooldownEndedCallback onEnded;
        private Presentation presentation = Presentation.DEFAULT;
        private int contributionCount;

        private Builder(String id) {
            this.id = id;
        }

        public Builder defaultDuration(int ticks) {
            this.defaultDuration = ticks;
            return this;
        }

        /** Sets what a player's death does: {@code keep} (the default) or {@code clear}. */
        public Builder deathRule(DeathRule rule) {
            this.deathRuleText = rule.text();
            return this;
        }

        /** Sets the death rule by its text value; an unknown value makes the definition invalid. */
        public Builder deathRule(String text) {
            this.deathRuleText = text;
            return this;
        }

        /** Runs when the cooldown ends, with reason {@code expired}, {@code removed}, or {@code death}. */
        public Builder onEnded(CooldownEndedCallback callback) {
            this.onEnded = callback;
            return this;
        }

        /** How the definition looks on the players' HUD and detail view. */
        public Builder presentation(Presentation presentation) {
            this.presentation = presentation;
            return this;
        }

        /**
         * A cooldown carries no contributions: any contribution given here makes the definition invalid when
         * definitions freeze. It exists so a script that supplies one gets that error instead of a script error.
         */
        public Builder contribute(Contribution contribution) {
            contributionCount++;
            return this;
        }

        public CooldownDefinition build() {
            return new CooldownDefinition(this);
        }
    }
}
