package com.mahghuuuls.statusworks.core.contribution;

/**
 * What a maximum-health change does to current health: raising the maximum never refills, and lowering it below
 * current health brings current health down to the new maximum. Plain Java.
 */
public final class HealthPolicy {

    private HealthPolicy() {
    }

    /** The health after the maximum changed from {@code oldMax} to {@code newMax}. */
    public static float afterMaxChange(float health, double oldMax, double newMax) {
        if (newMax < oldMax && health > newMax) {
            return (float) newMax;
        }
        return health;
    }
}
