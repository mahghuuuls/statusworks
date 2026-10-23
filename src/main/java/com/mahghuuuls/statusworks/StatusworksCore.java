package com.mahghuuuls.statusworks;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import com.mahghuuuls.statusworks.core.diagnostic.Timings;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.lifecycle.DeathAndClone;
import com.mahghuuuls.statusworks.core.lifecycle.TickDriver;
import com.mahghuuuls.statusworks.core.state.EntityStateCodec;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

/**
 * The composition root: builds every core part once and connects the public API to them. Nothing in
 * {@code core} reaches back here.
 */
public final class StatusworksCore implements StatusworksApi.Backend {

    final DefinitionRegistry registry = new DefinitionRegistry();
    final Timings timings = new Timings();
    final Warnings warnings;
    final ErrorCounts errorCounts = new ErrorCounts();
    final CallbackRunner callbacks;
    final DiagnosticLog diagnosticLog;
    final StatusOperations statuses;
    final EntityStateCodec codec;
    final DeathAndClone deathAndClone;
    // Not final: StatusOperations reaches it through a lambda that only runs after construction.
    TickDriver tickDriver;
    private final Logger logger;

    StatusworksCore(Logger logger, boolean diagnosticOn, int linesPerSecond) {
        this.logger = logger;
        this.warnings = new Warnings(logger::warn);
        this.diagnosticLog = new DiagnosticLog(logger::info, System::currentTimeMillis, diagnosticOn, linesPerSecond);
        this.callbacks = new CallbackRunner(errorCounts, warnings, logger::error);
        this.statuses = new StatusOperations(registry, diagnosticLog, warnings, callbacks,
                entity -> this.tickDriver.track(entity));
        this.codec = new EntityStateCodec(registry, (key, message) ->
                warnings.once("load:" + key, "Statusworks: " + message));
        this.tickDriver = new TickDriver(statuses, timings, diagnosticLog);
        this.deathAndClone = new DeathAndClone(statuses, codec, registry);
    }

    @Override
    public RegistrationResult registerStatus(StatusDefinition definition) {
        RegistrationResult result = registry.registerStatus(definition, currentOrigin());
        if (!result.isAccepted()) {
            logger.warn("Statusworks: registration refused: {}", result.error());
        }
        return result;
    }

    @Override
    public StatusService statuses() {
        return statuses;
    }

    @Override
    public boolean isFrozen() {
        return registry.isFrozen();
    }

    /** The mod that is registering, as the definition's origin. */
    private static String currentOrigin() {
        ModContainer container = Loader.instance().activeModContainer();
        return container == null ? "unknown" : container.getModId();
    }
}
