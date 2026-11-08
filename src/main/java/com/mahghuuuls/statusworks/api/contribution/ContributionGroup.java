package com.mahghuuuls.statusworks.api.contribution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The answer to {@link com.mahghuuuls.statusworks.api.ContributionService#group}: a contribution group's
 * availability and, when it is {@link GroupAvailability#AVAILABLE}, its complete committed values by contribution
 * key. For any other availability the values are empty and must not be read as "none".
 */
public final class ContributionGroup {

    private static final ContributionGroup UNAVAILABLE =
            new ContributionGroup(GroupAvailability.UNAVAILABLE, Collections.emptyMap());

    private final GroupAvailability availability;
    private final Map<String, Double> values;

    private ContributionGroup(GroupAvailability availability, Map<String, Double> values) {
        this.availability = availability;
        this.values = values;
    }

    /**
     * A group that is not available as current; its values are empty.
     *
     * @throws IllegalArgumentException for {@link GroupAvailability#AVAILABLE}, which needs its values: use
     *                                  {@link #available(Map)}
     */
    public static ContributionGroup of(GroupAvailability availability) {
        if (availability == GroupAvailability.AVAILABLE) {
            throw new IllegalArgumentException("an available group needs its values");
        }
        return availability == GroupAvailability.UNAVAILABLE ? UNAVAILABLE
                : new ContributionGroup(availability, Collections.emptyMap());
    }

    /** An available group with these complete values (copied). */
    public static ContributionGroup available(Map<String, Double> values) {
        return new ContributionGroup(GroupAvailability.AVAILABLE,
                Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    public GroupAvailability availability() {
        return availability;
    }

    public boolean isAvailable() {
        return availability == GroupAvailability.AVAILABLE;
    }

    /** The complete values by contribution key when available; read-only; empty otherwise. */
    public Map<String, Double> values() {
        return values;
    }

    @Override
    public String toString() {
        return availability + (isAvailable() ? " " + values : "");
    }
}
