package com.mahghuuuls.statusworks.core.rule;

/**
 * Why an instance ended. Exactly one reason is attached to every end, so callbacks and the diagnostic log can
 * tell an expiration from a death, a replacement, or an operator's command. Callbacks receive {@link #text()}.
 */
public enum RemovalReason {
    /** Its configured lifetime ran out. */
    EXPIRED("expired"),
    /** An operation, an operator command, or a count reaching 0 ended it. */
    REMOVED("removed"),
    /** A mark was replaced at its target limit. */
    REPLACED("replaced"),
    /** Its entity, or a mark participant, died. */
    DEATH("death"),
    /** A mark participant was seen despawning or otherwise removed for good. */
    GONE("gone"),
    /** A mark stayed inactive longer than its definition allows. */
    INACTIVE_TIMEOUT("inactive_timeout"),
    /** Its definition no longer exists. */
    INVALID("invalid");

    private final String text;

    RemovalReason(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
