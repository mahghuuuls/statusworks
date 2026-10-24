package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.definition.AttributeOperation;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.ValueForm;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContributionValuesTest {

    private static final double EPS = 1e-9;

    @Test
    void perStackFollowsTheTotalCount() {
        Contribution c = Contribution.attribute("generic.movementSpeed", AttributeOperation.MULTIPLY_TOTAL,
                ValueForm.PER_STACK, 0.05);
        assertEquals(0.15, ContributionValues.forStatus(c, 3), EPS);
        assertEquals(2, ContributionValues.operation(c.operation()));
        Contribution fixed = Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.FIXED, 2);
        assertEquals(2, ContributionValues.forStatus(fixed, 5), EPS);
    }

    @Test
    void perFillAndPerAmountFollowThePool() {
        Contribution fill = Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.PER_FILL, 10);
        assertEquals(2.5, ContributionValues.forPool(fill, 5, 20, 1), EPS);
        Contribution amount = Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.PER_AMOUNT, 2);
        assertEquals(10, ContributionValues.forPool(amount, 5, 20, 1), EPS);
    }

    @Test
    void theUpdateStepMovesInWholeStepsAndIsExactAtEmptyAndFull() {
        assertEquals(0.38, ContributionValues.steppedFill(37.9, 100, 1), EPS);
        assertEquals(0.35, ContributionValues.steppedFill(34.9, 100, 5), EPS);
        assertEquals(0, ContributionValues.steppedFill(0, 100, 1), EPS);
        assertEquals(0.01, ContributionValues.steppedFill(0.5, 100, 1), EPS, "not empty: not the final value");
        assertEquals(0.01, ContributionValues.steppedFill(0.01, 100, 1), EPS);
        assertEquals(1, ContributionValues.steppedFill(100, 100, 1), EPS);
        assertEquals(0.99, ContributionValues.steppedFill(99.5, 100, 1), EPS, "not full: not the final value");
        assertEquals(0.99, ContributionValues.steppedFill(99.99, 100, 1), EPS);
    }

    @Test
    void aSmoothDrainChangesTheValueAboutOncePerStep() {
        Contribution fill = Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.PER_FILL, 1);
        int changes = 0;
        double last = ContributionValues.forPool(fill, 100, 100, 1);
        for (int tick = 1; tick <= 1000; tick++) {
            double value = ContributionValues.forPool(fill, 100 - tick * 0.1, 100, 1);
            if (value != last) {
                changes++;
                last = value;
            }
        }
        assertTrue(changes >= 99 && changes <= 101, "changes: " + changes);
        assertEquals(0, last, EPS, "exact at empty");
    }

    @Test
    void keysAndModifierIdsAreStable() {
        String key = ContributionValues.key("status", "a:rage", 0);
        assertEquals("status:a:rage#0", key);
        assertEquals(ContributionValues.modifierId(key), ContributionValues.modifierId("status:a:rage#0"));
        assertNotEquals(ContributionValues.modifierId(key), ContributionValues.modifierId("status:a:rage#1"));
    }

    @Test
    void thePlanRemovesAppliesAndRepairsMissingModifiers() {
        Map<String, Applied> applied = new LinkedHashMap<>();
        applied.put("a", new Applied(true, "generic.armor", 0, 2));
        applied.put("b", new Applied(true, "generic.armor", 0, 3));
        applied.put("c", new Applied(false, "t:power", -1, 1));
        Map<String, Applied> desired = new LinkedHashMap<>();
        desired.put("a", new Applied(true, "generic.armor", 0, 2));
        desired.put("c", new Applied(false, "t:power", -1, 2));
        desired.put("d", new Applied(true, "generic.maxHealth", 0, 4));

        ContributionPlan plan = ContributionPlan.diff(desired, applied, key -> true);
        assertEquals(Collections.singletonList("b"), plan.remove, "only the ended instance's part goes");
        assertEquals(java.util.Arrays.asList("c", "d"), plan.apply);

        ContributionPlan repair = ContributionPlan.diff(desired, applied, key -> !"a".equals(key));
        assertTrue(repair.apply.contains("a"), "a modifier missing after a load is applied again");

        Map<String, Applied> moved = new LinkedHashMap<>();
        moved.put("a", new Applied(true, "generic.attackDamage", 0, 2));
        ContributionPlan retarget = ContributionPlan.diff(moved, applied, key -> true);
        assertTrue(retarget.remove.contains("a") && retarget.apply.contains("a"));

        assertTrue(ContributionPlan.diff(applied, applied, key -> true).isEmpty(), "ten reloads add nothing");
    }

    @Test
    void healthIsNeverRefilledAndClampedWhenTheMaximumDrops() {
        assertEquals(20f, HealthPolicy.afterMaxChange(20f, 20, 30), 1e-6);
        assertEquals(20f, HealthPolicy.afterMaxChange(30f, 30, 20), 1e-6);
        assertEquals(15f, HealthPolicy.afterMaxChange(15f, 30, 20), 1e-6);
    }
}
