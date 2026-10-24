package com.mahghuuuls.statusworks.core.contribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The difference between the contributions an entity should have and the ones its record says are applied:
 * which to remove and which to apply. Plain Java; {@link ContributionManager} carries it out.
 */
public final class ContributionPlan {

    /** Keys whose applied contribution must go: not wanted any more, or wanted on another target. */
    public final List<String> remove;
    /** Keys whose wanted contribution must be applied: new, changed, or no longer present on the entity. */
    public final List<String> apply;

    private ContributionPlan(List<String> remove, List<String> apply) {
        this.remove = Collections.unmodifiableList(remove);
        this.apply = Collections.unmodifiableList(apply);
    }

    /**
     * @param present whether an applied entry is really in place on the entity (an attribute modifier can be
     *                missing after a load when the attribute was not registered yet)
     */
    public static ContributionPlan diff(Map<String, Applied> desired, Map<String, Applied> applied,
                                       Predicate<String> present) {
        List<String> remove = new ArrayList<>();
        List<String> apply = new ArrayList<>();
        for (Map.Entry<String, Applied> e : applied.entrySet()) {
            Applied want = desired.get(e.getKey());
            if (want == null || !want.sameTarget(e.getValue())) {
                remove.add(e.getKey());
            }
        }
        for (Map.Entry<String, Applied> e : desired.entrySet()) {
            Applied have = applied.get(e.getKey());
            if (have == null || !have.equals(e.getValue()) || !present.test(e.getKey())) {
                apply.add(e.getKey());
            }
        }
        return new ContributionPlan(remove, apply);
    }

    public boolean isEmpty() {
        return remove.isEmpty() && apply.isEmpty();
    }
}
