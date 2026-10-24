package com.mahghuuuls.statusworks.core.rule;

import com.mahghuuuls.statusworks.api.definition.DeathRule;

import javax.annotation.Nullable;

/** What death and player cloning do to an instance of any kind, by its definition's death rule. */
public final class DeathRules {

    private DeathRules() {
    }

    /** Whether an instance outlives its entity's death: only on a player, and only with the {@code keep} rule. */
    public static boolean survivesDeath(@Nullable DeathRule rule, boolean player) {
        return player && rule == DeathRule.KEEP;
    }

    /**
     * Whether an instance is copied to the new player object of a clone: always when leaving the End, and after
     * a death only when it survives death.
     */
    public static boolean copiedOnClone(@Nullable DeathRule rule, boolean wasDeath) {
        return !wasDeath || survivesDeath(rule, true);
    }
}
