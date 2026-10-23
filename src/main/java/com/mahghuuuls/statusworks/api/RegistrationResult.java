package com.mahghuuuls.statusworks.api;

import javax.annotation.Nullable;

/**
 * The answer to a registration call. A registration is accepted for validation, or refused at once with a
 * message (for example after definitions froze). Validation itself happens when definitions freeze; its errors
 * are logged and listed by {@code /statusworks definitions}.
 */
public final class RegistrationResult {

    private static final RegistrationResult ACCEPTED = new RegistrationResult(null);

    @Nullable private final String error;

    private RegistrationResult(@Nullable String error) {
        this.error = error;
    }

    public static RegistrationResult accepted() {
        return ACCEPTED;
    }

    public static RegistrationResult refused(String error) {
        return new RegistrationResult(error);
    }

    public boolean isAccepted() {
        return error == null;
    }

    /** Why the registration was refused, or null when it was accepted. */
    @Nullable
    public String error() {
        return error;
    }

    @Override
    public String toString() {
        return error == null ? "accepted" : "refused: " + error;
    }
}
