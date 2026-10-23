package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.CountChange;
import com.mahghuuuls.statusworks.api.StackChange;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
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
import java.util.UUID;

/**
 * {@code /statusworks status apply|set|remove|clear}: test operations with exactly the rules of the API.
 *
 * <pre>
 * status apply  &lt;entity&gt; &lt;id&gt; &lt;stacks&gt; [duration] [source]
 * status set    &lt;entity&gt; &lt;id&gt; &lt;count&gt; [source|none]
 * status remove &lt;entity&gt; &lt;id&gt; &lt;stacks&gt; [source|none]
 * status clear  &lt;entity&gt; &lt;id&gt;
 * </pre>
 * A duration of 0 means the definition's default.
 */
final class StatusCommand extends CommandBase {

    private final StatusworksCommand.Context context;

    StatusCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks status <apply <entity> <id> <stacks> [duration] [source] | set <entity> <id> <count> "
                + "[source|none] | remove <entity> <id> <stacks> [source|none] | clear <entity> <id>>";
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
        StatusOperations ops = context.statuses;
        String cause = StatusOperations.CAUSE_COMMAND;
        if ("apply".equals(action) && args.length >= 4 && args.length <= 6) {
            int stacks = parseInt(args[3], 1);
            int duration = args.length >= 5 ? parseInt(args[4], 0) : 0;
            Entity source = args.length == 6 ? getEntity(server, sender, args[5]) : null;
            StackChange r = ops.apply(entity, id, stacks, source, duration, cause);
            context.reply(sender, "status apply " + id + " on " + entity.getName() + ": " + r
                    + " total=" + ops.count(entity, id));
        } else if ("set".equals(action) && (args.length == 4 || args.length == 5)) {
            int count = parseInt(args[3], 0);
            CountChange r = ops.setCount(entity, id, count, sourceKey(server, sender, args, 4), cause);
            context.reply(sender, "status set " + id + " on " + entity.getName() + ": " + r);
        } else if ("remove".equals(action) && (args.length == 4 || args.length == 5)) {
            int stacks = parseInt(args[3], 1);
            CountChange r = ops.remove(entity, id, stacks, args.length == 5 ? sourceKey(server, sender, args, 4) : null,
                    cause);
            context.reply(sender, "status remove " + id + " on " + entity.getName() + ": " + r);
        } else if ("clear".equals(action) && args.length == 3) {
            CountChange r = ops.clear(entity, id, cause);
            context.reply(sender, "status clear " + id + " on " + entity.getName() + ": " + r);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    /** The source group named at {@code index}: an entity selector, or {@code none} for the no-source group. */
    private static UUID sourceKey(MinecraftServer server, ICommandSender sender, String[] args, int index)
            throws CommandException {
        if (args.length <= index || "none".equals(args[index])) {
            return StatusRules.NO_SOURCE;
        }
        return getEntity(server, sender, args[index]).getUniqueID();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "apply", "set", "remove", "clear");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3) {
            List<String> ids = new ArrayList<>();
            context.registry.statuses().forEach(e -> ids.add(e.definition().id()));
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
