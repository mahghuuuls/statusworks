package com.mahghuuuls.statusworks;

import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.CooldownService;
import com.mahghuuuls.statusworks.api.MarkService;
import com.mahghuuuls.statusworks.api.PoolService;
import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.callback.CallbackRunner;
import com.mahghuuuls.statusworks.core.contribution.ContributionManager;
import com.mahghuuuls.statusworks.core.contribution.ContributionTypeRegistry;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import com.mahghuuuls.statusworks.core.diagnostic.Timings;
import com.mahghuuuls.statusworks.core.diagnostic.Warnings;
import com.mahghuuuls.statusworks.core.lifecycle.DeathAndClone;
import com.mahghuuuls.statusworks.core.lifecycle.TickDriver;
import com.mahghuuuls.statusworks.core.mark.MarkStore;
import com.mahghuuuls.statusworks.network.AudienceSender;
import com.mahghuuuls.statusworks.network.OwnEntriesSender;
import com.mahghuuuls.statusworks.core.state.CooldownOperations;
import com.mahghuuuls.statusworks.core.state.EntityStateCodec;
import com.mahghuuuls.statusworks.core.state.PoolOperations;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

/**
 * The composition root: builds every core part once and connects the public API to them. Nothing in
 * {@code core} reaches back here.
 */
public final class StatusworksCore implements StatusworksApi.Backend {

    final ContributionTypeRegistry contributionTypes = new ContributionTypeRegistry();
    final DefinitionRegistry registry = new DefinitionRegistry(contributionTypes::isKnown);
    final Timings timings = new Timings();
    final Warnings warnings;
    final ErrorCounts errorCounts = new ErrorCounts();
    final CallbackRunner callbacks;
    final DiagnosticLog diagnosticLog;
    final StatusOperations statuses;
    final PoolOperations pools;
    final CooldownOperations cooldowns;
    final ContributionManager contributions;
    final MarkStore marks;
    final OwnEntriesSender ownEntries;
    final AudienceSender audience;
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
        this.contributions = new ContributionManager(registry, contributionTypes, callbacks, diagnosticLog, warnings, errorCounts,
                logger::error);
        this.statuses = new StatusOperations(registry, diagnosticLog, warnings, callbacks,
                entity -> this.tickDriver.track(entity), contributions::reconcile);
        this.pools = new PoolOperations(registry, diagnosticLog, warnings, callbacks,
                entity -> this.tickDriver.track(entity), contributions::reconcile);
        this.cooldowns = new CooldownOperations(registry, diagnosticLog, warnings, callbacks,
                entity -> this.tickDriver.track(entity));
        this.marks = new MarkStore(registry, callbacks, contributions, diagnosticLog, warnings, logger::warn);
        contributions.setMarks(marks);
        this.ownEntries = new OwnEntriesSender(registry, marks, diagnosticLog);
        this.audience = new AudienceSender(registry, marks, diagnosticLog, ownEntries,
                visit -> this.tickDriver.forEachTracked(visit));
        this.codec = new EntityStateCodec(registry, (key, message) ->
                warnings.once("load:" + key, "Statusworks: " + message));
        this.tickDriver = new TickDriver(statuses, pools, cooldowns, marks, ownEntries, audience, contributions,
                timings, diagnosticLog);
        this.deathAndClone = new DeathAndClone(statuses, pools, cooldowns, marks, contributions, codec, registry);
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
    public RegistrationResult registerPool(PoolDefinition definition) {
        RegistrationResult result = registry.registerPool(definition, currentOrigin());
        if (!result.isAccepted()) {
            logger.warn("Statusworks: registration refused: {}", result.error());
        }
        return result;
    }

    @Override
    public RegistrationResult registerCooldown(CooldownDefinition definition) {
        RegistrationResult result = registry.registerCooldown(definition, currentOrigin());
        if (!result.isAccepted()) {
            logger.warn("Statusworks: registration refused: {}", result.error());
        }
        return result;
    }

    @Override
    public RegistrationResult registerMark(MarkDefinition definition) {
        RegistrationResult result = registry.registerMark(definition, currentOrigin());
        if (!result.isAccepted()) {
            logger.warn("Statusworks: registration refused: {}", result.error());
        }
        return result;
    }

    @Override
    public MarkService marks() {
        return marks;
    }

    @Override
    public CooldownService cooldowns() {
        return cooldowns;
    }

    @Override
    public RegistrationResult registerContributionType(ContributionType type) {
        RegistrationResult result = contributionTypes.register(type, currentOrigin());
        if (!result.isAccepted()) {
            logger.warn("Statusworks: registration refused: {}", result.error());
        }
        return result;
    }

    @Override
    public ContributionService contributions() {
        return contributions;
    }

    @Override
    public StatusService statuses() {
        return statuses;
    }

    @Override
    public PoolService pools() {
        return pools;
    }

    @Override
    public boolean isFrozen() {
        return registry.isFrozen();
    }

    /**
     * The definition's origin: the script file a CraftTweaker script registers from ({@code scripts/a.zs}), found as
     * the first ZenScript frame of the current call, or else the mod that is registering.
     */
    private static String currentOrigin() {
        String script = DefinitionRegistry.scriptOrigin(Thread.currentThread().getStackTrace());
        if (script != null) {
            return script;
        }
        ModContainer container = Loader.instance().activeModContainer();
        return container == null ? "unknown" : container.getModId();
    }
}
