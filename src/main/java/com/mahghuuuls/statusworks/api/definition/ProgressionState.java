package com.mahghuuuls.statusworks.api.definition;

/**
 * Whether a pool's progression rule is running. It sits with the definition types because the pure pool rules,
 * which may use only those types, compute it.
 */
public enum ProgressionState {
    /** The pool has no rule, or its {@code to_target} rule has reached the target at its deadline. */
    NONE,
    /** The rule changes the amount over active time. */
    ACTIVE,
    /** The rule is paused by an operation; a {@code to_target} deadline keeps its remaining time. */
    PAUSED
}
