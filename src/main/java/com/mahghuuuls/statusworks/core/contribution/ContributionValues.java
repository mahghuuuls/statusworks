package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.definition.AttributeOperation;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.ValueForm;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pure parts of contributions: the contribution key, the modifier UUID derived from it, the value of each
 * form, the pool update step, and the vanilla operation number. Plain Java with no Minecraft types.
 */
public final class ContributionValues {

    // Hashing the key is not free and the same keys come back every tick.
    private static final Map<String, UUID> MODIFIER_IDS = new ConcurrentHashMap<>();

    private ContributionValues() {
    }

    /** The key of contribution {@code index} of a definition of a kind ({@code status} or {@code pool}). */
    public static String key(String kind, String definitionId, int index) {
        return kind + ":" + definitionId + "#" + index;
    }

    /**
     * The key of contribution {@code index} of a mark definition on one participant: one mark per pair, so the key
     * names the entity's side and the other participant ({@code mark:<id>#<index>@<side>:<other UUID>}); two
     * entities that mark each other get different keys.
     */
    public static String markKey(String definitionId, int index, String side, UUID other) {
        return key("mark", definitionId, index) + "@" + side + ":" + other;
    }

    public static boolean isMarkKey(String key) {
        return key.startsWith("mark:");
    }

    /** The attribute modifier UUID for a key; the same key always gives the same UUID. */
    public static UUID modifierId(String key) {
        return MODIFIER_IDS.computeIfAbsent(key,
                k -> UUID.nameUUIDFromBytes(("statusworks:" + k).getBytes(StandardCharsets.UTF_8)));
    }

    /** The vanilla attribute modifier operation: 0 add, 1 multiply base, 2 multiply total. */
    public static int operation(AttributeOperation operation) {
        switch (operation) {
            case MULTIPLY_BASE:
                return 1;
            case MULTIPLY_TOTAL:
                return 2;
            default:
                return 0;
        }
    }

    /** A status contribution's value for a total stack count. */
    public static double forStatus(Contribution c, int stacks) {
        return c.form() == ValueForm.PER_STACK ? c.value() * stacks : c.value();
    }

    /**
     * A pool contribution's value: {@code fixed} as declared, {@code per_amount} times the stepped amount,
     * {@code per_fill} times the stepped fill.
     */
    public static double forPool(Contribution c, double amount, double capacity, double stepPercent) {
        double fill = steppedFill(amount, capacity, stepPercent);
        if (c.form() == ValueForm.PER_FILL) {
            return c.value() * fill;
        }
        if (c.form() == ValueForm.PER_AMOUNT) {
            return c.value() * fill * capacity;
        }
        return c.value();
    }

    /**
     * The fill a pool contribution follows: exactly 0 only at empty, exactly 1 only at full, and otherwise the
     * fill rounded to the nearest whole step of {@code stepPercent} percent, kept between one step and one step
     * below full. So the contribution changes about once per step while the amount moves smoothly, in either
     * direction, and reaches its final value exactly when the pool is empty or full.
     */
    public static double steppedFill(double amount, double capacity, double stepPercent) {
        if (!(capacity > 0) || amount <= 0) {
            return 0;
        }
        if (amount >= capacity) {
            return 1;
        }
        double fill = amount / capacity;
        double step = stepPercent / 100.0;
        if (!(step > 0) || step >= 0.5) {
            return fill;
        }
        double stepped = Math.round(fill / step) * step;
        return Math.max(step, Math.min(1 - step, stepped));
    }
}
