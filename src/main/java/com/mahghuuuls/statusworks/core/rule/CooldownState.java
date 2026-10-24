package com.mahghuuuls.statusworks.core.rule;

/**
 * The data of one running cooldown, in plain Java: its active-tick age and the age at which it ends. A cooldown
 * that is not running has no state. {@link CooldownRules} owns every change.
 */
public final class CooldownState {

    long age;
    long end;

    public long age() {
        return age;
    }

    public long end() {
        return end;
    }

    /** Restores saved data; used by the save format only. */
    public void restore(long age, long end) {
        this.age = age;
        this.end = end;
    }
}
