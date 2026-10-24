package com.mahghuuuls.statusworks.api;

import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
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

        RegistrationResult registerPool(PoolDefinition definition);

        RegistrationResult registerCooldown(CooldownDefinition definition);

        RegistrationResult registerContributionType(ContributionType type);

        StatusService statuses();

        PoolService pools();

        CooldownService cooldowns();

        ContributionService contributions();

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

    /** Registers a pool definition for validation when definitions freeze. */
    public static RegistrationResult registerPool(PoolDefinition definition) {
        Backend current = backend;
        if (current == null) {
            return RegistrationResult.refused("Statusworks is not loaded yet");
        }
        return current.registerPool(definition);
    }

    /** Registers a cooldown definition for validation when definitions freeze. */
    public static RegistrationResult registerCooldown(CooldownDefinition definition) {
        Backend current = backend;
        if (current == null) {
            return RegistrationResult.refused("Statusworks is not loaded yet");
        }
        return current.registerCooldown(definition);
    }

    /** Registers an addon contribution type; definitions may name it once registered, before the freeze. */
    public static RegistrationResult registerContributionType(ContributionType type) {
        Backend current = backend;
        if (current == null) {
            return RegistrationResult.refused("Statusworks is not loaded yet");
        }
        return current.registerContributionType(type);
    }

    public static StatusService statuses() {
        Backend current = requireBackend();
        return current.statuses();
    }

    public static PoolService pools() {
        Backend current = requireBackend();
        return current.pools();
    }

    public static CooldownService cooldowns() {
        Backend current = requireBackend();
        return current.cooldowns();
    }

    public static ContributionService contributions() {
        Backend current = requireBackend();
        return current.contributions();
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
