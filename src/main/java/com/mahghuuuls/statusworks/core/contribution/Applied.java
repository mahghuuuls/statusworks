package com.mahghuuuls.statusworks.core.contribution;

import java.util.Objects;

/**
 * One contribution as applied (or wanted) on an entity: its target and value. For an attribute the target is the
 * attribute id and the operation is the vanilla modifier operation (0 add, 1 multiply base, 2 multiply total);
 * for a contribution type the target is the type id and the operation is -1.
 */
public final class Applied {

    public final boolean attribute;
    public final String target;
    public final int operation;
    public final double value;

    public Applied(boolean attribute, String target, int operation, double value) {
        this.attribute = attribute;
        this.target = target;
        this.operation = attribute ? operation : -1;
        this.value = value;
    }

    /** Whether the target (kind, id, operation) is the same, whatever the value. */
    public boolean sameTarget(Applied other) {
        return attribute == other.attribute && operation == other.operation && target.equals(other.target);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Applied)) {
            return false;
        }
        Applied other = (Applied) o;
        return sameTarget(other) && Double.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, target, operation, value);
    }

    @Override
    public String toString() {
        return (attribute ? "attribute " + target + " op=" + operation : "type " + target) + " value=" + value;
    }
}
