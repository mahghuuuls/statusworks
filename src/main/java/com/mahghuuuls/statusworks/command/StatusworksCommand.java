package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.DiagnosticLog;
import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import com.mahghuuuls.statusworks.core.diagnostic.Timings;
import com.mahghuuuls.statusworks.core.state.CooldownOperations;
import com.mahghuuuls.statusworks.core.state.PoolOperations;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.server.command.CommandTreeBase;
import org.apache.logging.log4j.Logger;

/**
 * {@code /statusworks}: the operator command of the diagnostic mode. Every subcommand needs permission level 2.
 * Output goes to the sender and to the server log, so the log holds the evidence of each use.
 */
public final class StatusworksCommand extends CommandTreeBase {

    /** What the subcommands work with. */
    public static final class Context {
        final StatusOperations statuses;
        final PoolOperations pools;
        final CooldownOperations cooldowns;
        final DefinitionRegistry registry;
        final DiagnosticLog diagnosticLog;
        final Timings timings;
        final ErrorCounts errorCounts;
        final Logger logger;

        public Context(StatusOperations statuses, PoolOperations pools, CooldownOperations cooldowns,
                       DefinitionRegistry registry, DiagnosticLog diagnosticLog,
                       Timings timings, ErrorCounts errorCounts, Logger logger) {
            this.statuses = statuses;
            this.pools = pools;
            this.cooldowns = cooldowns;
            this.registry = registry;
            this.diagnosticLog = diagnosticLog;
            this.timings = timings;
            this.errorCounts = errorCounts;
            this.logger = logger;
        }

        /** Sends a line to the sender and writes it to the server log. */
        void reply(ICommandSender sender, String line) {
            sender.sendMessage(new TextComponentString(line));
            logger.info("[statusworks-cmd] {}", line);
        }
    }

    public StatusworksCommand(Context context) {
        addSubcommand(new InspectCommand(context));
        addSubcommand(new StatusCommand(context));
        addSubcommand(new PoolCommand(context));
        addSubcommand(new CooldownCommand(context));
        addSubcommand(new DefinitionsCommand(context));
        addSubcommand(new DebugCommand(context));
        addSubcommand(new TimingCommand(context));
        addSubcommand(new ErrorsCommand(context));
    }

    @Override
    public String getName() {
        return "statusworks";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks <inspect|status|pool|cooldown|definitions|debug|timing|errors>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
