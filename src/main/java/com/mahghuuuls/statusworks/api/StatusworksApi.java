package com.mahghuuuls.statusworks.api;

import com.mahghuuuls.statusworks.api.definition.StatusDefinition;

/**
 * Entry point of the Statusworks API.
 *
 * <p>Register definitions during game startup (pre-init to post-init, or from CraftTweaker scripts); definitions
 * freeze when loading completes, and later registrations are refused. Operations and queries go through the
 * services and work on the logical server.</p>
 */
public final class StatusworksApi {

    /** Implemented by the Statusworks mod and installed when it is constructed. */
    public interface Backend {
        RegistrationResult registerStatus(StatusDefinition definition);

        StatusService statuses();

        boolean isFrozen();
    }

    private static volatile Backend backend;

    private StatusworksApi() {
    }

    /** Registers a status definition for validation when definitions freeze. */
    public static RegistrationResult registerStatus(StatusDefinition definition) {
        Backend current = backend;
        if (current == null) {
            return RegistrationResult.refused("Statusworks is not loaded yet");
        }
        return current.registerStatus(definition);
    }

    public static StatusService statuses() {
        Backend current = requireBackend();
        return current.statuses();
    }

    /** Whether definitions are frozen; after that, registrations are refused. */
    public static boolean isFrozen() {
        Backend current = backend;
        return current != null && current.isFrozen();
    }

    /** Installs the backend. Called once by Statusworks; later calls are ignored. */
    public static synchronized void install(Backend implementation) {
        if (backend == null) {
            backend = implementation;
        }
    }

    private static Backend requireBackend() {
        Backend current = backend;
        if (current == null) {
            throw new IllegalStateException("Statusworks is not loaded");
        }
        return current;
    }
}
