package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.PoolChange;
import com.mahghuuuls.statusworks.core.state.PoolOperations;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /statusworks pool ...}: test operations with exactly the rules of the API.
 *
 * <pre>
 * pool apply    &lt;entity&gt; &lt;id&gt; &lt;amount&gt;   (adds; creates the instance when absent)
 * pool subtract &lt;entity&gt; &lt;id&gt; &lt;amount&gt;
 * pool set      &lt;entity&gt; &lt;id&gt; &lt;amount&gt;
 * pool capacity &lt;entity&gt; &lt;id&gt; &lt;capacity&gt;
 * pool lifetime &lt;entity&gt; &lt;id&gt; &lt;ticks&gt;
 * pool extend   &lt;entity&gt; &lt;id&gt; &lt;ticks&gt;
 * pool pause    &lt;entity&gt; &lt;id&gt;
 * pool resume   &lt;entity&gt; &lt;id&gt;
 * pool clear    &lt;entity&gt; &lt;id&gt;
 * </pre>
 */
final class PoolCommand extends CommandBase {

    private static final String[] ACTIONS = {"apply", "subtract", "set", "capacity", "lifetime", "extend", "pause",
            "resume", "clear"};

    private final StatusworksCommand.Context context;

    PoolCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "pool";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks pool <apply|subtract|set|capacity|lifetime|extend> <entity> <id> <value> | "
                + "pool <pause|resume|clear> <entity> <id>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException(getUsage(sender));
        }
        String action = args[0];
        Entity entity = getEntity(server, sender, args[1]);
        String id = args[2];
        PoolOperations pools = context.pools;
        String cause = StatusOperations.CAUSE_COMMAND;
        PoolChange r;
        if (args.length == 4) {
            switch (action) {
                case "apply":
                    r = pools.add(entity, id, parseDouble(args[3]), cause);
                    break;
                case "subtract":
                    r = pools.subtract(entity, id, parseDouble(args[3]), cause);
                    break;
                case "set":
                    r = pools.set(entity, id, parseDouble(args[3]), cause);
                    break;
                case "capacity":
                    r = pools.setCapacity(entity, id, parseDouble(args[3]), cause);
                    break;
                case "lifetime":
                    r = pools.setLifetime(entity, id, parseInt(args[3]), cause);
                    break;
                case "extend":
                    r = pools.extendLifetime(entity, id, parseInt(args[3]), cause);
                    break;
                default:
                    throw new WrongUsageException(getUsage(sender));
            }
        } else if (args.length == 3) {
            switch (action) {
                case "pause":
                    r = pools.pause(entity, id, cause);
                    break;
                case "resume":
                    r = pools.resume(entity, id, cause);
                    break;
                case "clear":
                    r = pools.remove(entity, id, cause);
                    break;
                default:
                    throw new WrongUsageException(getUsage(sender));
            }
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
        context.reply(sender, "pool " + action + " " + id + " on " + entity.getName() + ": " + r);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3) {
            List<String> ids = new ArrayList<>();
            context.registry.pools().forEach(e -> ids.add(e.definition().id()));
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
