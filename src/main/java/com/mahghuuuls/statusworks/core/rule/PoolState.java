package com.mahghuuuls.statusworks.core.rule;

/**
 * The data of one pool instance, in plain Java. Two clocks count active ticks: {@code age} for the lifetime, and
 * {@code progressAge}, which stops while the progression is paused, for the {@code to_target} deadline. Both
 * are ages, so an entity that is not ticked pauses everything without any work. {@link PoolMath} owns every
 * change to this data.
 */
public final class PoolState {

    double amount;
    double capacity;
    /** Whether an operation set the capacity; when not, a load takes the definition's current capacity. */
    boolean capacitySet;
    long age;
    /** Age at which the lifetime ends, or -1 when there is none. */
    long lifetimeEnd = -1;
    boolean paused;
    long progressAge;
    /** Progress age of the {@code to_target} deadline, or -1 when there is none. */
    long deadline = -1;
    /** Whether the {@code to_target} rule has reached its target at its deadline. */
    boolean targetDone;

    public double amount() {
        return amount;
    }

    public double capacity() {
        return capacity;
    }

    public boolean capacitySet() {
        return capacitySet;
    }

    public long age() {
        return age;
    }

    public long lifetimeEnd() {
        return lifetimeEnd;
    }

    public boolean paused() {
        return paused;
    }

    public long progressAge() {
        return progressAge;
    }

    public long deadline() {
        return deadline;
    }

    public boolean targetDone() {
        return targetDone;
    }

    /** Restores saved data; used by the save format only. */
    public void restore(double amount, double capacity, boolean capacitySet, long age, long lifetimeEnd,
                        boolean paused, long progressAge, long deadline, boolean targetDone) {
        this.amount = amount;
        this.capacity = capacity;
        this.capacitySet = capacitySet;
        this.age = age;
        this.lifetimeEnd = lifetimeEnd;
        this.paused = paused;
        this.progressAge = progressAge;
        this.deadline = deadline;
        this.targetDone = targetDone;
    }
}
