package com.mahghuuuls.statusworks.api;

import com.mahghuuuls.statusworks.api.definition.ProgressionState;

/** A read-only view of one pool instance at the moment of the query. */
public final class PoolSnapshot {

    private final double amount;
    private final double capacity;
    private final int remainingLifetime;
    private final ProgressionState progression;
    private final double target;
    private final int remainingToDeadline;

    public PoolSnapshot(double amount, double capacity, int remainingLifetime, ProgressionState progression,
                        double target, int remainingToDeadline) {
        this.amount = amount;
        this.capacity = capacity;
        this.remainingLifetime = remainingLifetime;
        this.progression = progression;
        this.target = target;
        this.remainingToDeadline = remainingToDeadline;
    }

    public double amount() {
        return amount;
    }

    public double capacity() {
        return capacity;
    }

    /** Amount divided by capacity, from 0 to 1. */
    public double fill() {
        return capacity <= 0 ? 0 : amount / capacity;
    }

    /** Active ticks until the lifetime ends, or -1 when the pool has no lifetime. */
    public int remainingLifetime() {
        return remainingLifetime;
    }

    public ProgressionState progression() {
        return progression;
    }

    /** The target of a {@code to_target} rule, or NaN for other rules. */
    public double target() {
        return target;
    }

    /** Active, unpaused ticks until a {@code to_target} deadline, or -1 when there is none. */
    public int remainingToDeadline() {
        return remainingToDeadline;
    }

    @Override
    public String toString() {
        return "amount=" + amount + " capacity=" + capacity + " fill=" + fill() + " lifetime=" + remainingLifetime
                + " progression=" + progression + (Double.isNaN(target) ? "" : " target=" + target
                + " deadlineIn=" + remainingToDeadline);
    }
}
