package com.mahghuuuls.statusworks.api.definition;

import javax.annotation.Nullable;

/**
 * One managed contribution a status or pool definition declares: to an entity attribute (named by attribute id,
 * vanilla or from another mod, with an operation), or to an addon contribution type (named by type id). Statusworks
 * applies, updates, and removes it together with the instance; cooldowns cannot have contributions.
 *
 * <p>The values are checked when definitions freeze: an unknown operation, form, contribution type, or vanilla
 * attribute id makes the definition invalid. Another mod's attribute id is checked when the server starts.</p>
 */
public final class Contribution {

    private final boolean attribute;
    private final String target;
    @Nullable private final AttributeOperation operation;
    private final String operationText;
    @Nullable private final ValueForm form;
    private final String formText;
    private final double value;

    private Contribution(boolean attribute, String target, String operationText, String formText, double value) {
        this.attribute = attribute;
        this.target = target;
        this.operationText = operationText;
        this.operation = AttributeOperation.fromText(operationText);
        this.formText = formText;
        this.form = ValueForm.fromText(formText);
        this.value = value;
    }

    /** A contribution to an attribute, for example {@code generic.maxHealth} with {@code add}. */
    public static Contribution attribute(String attributeId, AttributeOperation operation, ValueForm form,
                                         double value) {
        return new Contribution(true, attributeId, operation.text(), form.text(), value);
    }

    /** {@link #attribute(String, AttributeOperation, ValueForm, double)} with text values. */
    public static Contribution attribute(String attributeId, String operation, String form, double value) {
        return new Contribution(true, attributeId, operation, form, value);
    }

    /** A contribution to an addon contribution type registered under {@code typeId}. */
    public static Contribution type(String typeId, ValueForm form, double value) {
        return new Contribution(false, typeId, AttributeOperation.ADD.text(), form.text(), value);
    }

    /** {@link #type(String, ValueForm, double)} with a text value form. */
    public static Contribution type(String typeId, String form, double value) {
        return new Contribution(false, typeId, AttributeOperation.ADD.text(), form, value);
    }

    /** Whether the target is an attribute; otherwise it is a contribution type. */
    public boolean isAttribute() {
        return attribute;
    }

    /** The attribute id or contribution type id. */
    public String target() {
        return target;
    }

    /** The attribute operation, or null when the text named none; always {@code add} for a type. */
    @Nullable
    public AttributeOperation operation() {
        return operation;
    }

    public String operationText() {
        return operationText;
    }

    @Nullable
    public ValueForm form() {
        return form;
    }

    public String formText() {
        return formText;
    }

    public double value() {
        return value;
    }

    @Override
    public String toString() {
        return (attribute ? "attribute " + target + " " + operationText : "type " + target) + " " + formText + " "
                + value;
    }
}
