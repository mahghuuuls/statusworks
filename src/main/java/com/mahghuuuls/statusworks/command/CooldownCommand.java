package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.CooldownChange;
import com.mahghuuuls.statusworks.core.state.CooldownOperations;
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
 * {@code /statusworks cooldown ...}: test operations with exactly the rules of the API.
 *
 * <pre>
 * cooldown apply  &lt;entity&gt; &lt;id&gt; [ticks]   (starts; a running cooldown keeps its time)
 * cooldown ready  &lt;entity&gt; &lt;id&gt; [ticks]   (starts only if not running; reports true or false)
 * cooldown set    &lt;entity&gt; &lt;id&gt; &lt;ticks&gt;   (restarts with a new time)
 * cooldown extend &lt;entity&gt; &lt;id&gt; &lt;ticks&gt;
 * cooldown reduce &lt;entity&gt; &lt;id&gt; &lt;ticks&gt;
 * cooldown clear  &lt;entity&gt; &lt;id&gt;
 * </pre>
 * Ticks of 0 or none mean the definition's default.
 */
final class CooldownCommand extends CommandBase {

    private static final String[] ACTIONS = {"apply", "ready", "set", "extend", "reduce", "clear"};

    private final StatusworksCommand.Context context;

    CooldownCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "cooldown";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks cooldown <apply|ready> <entity> <id> [ticks] | cooldown <set|extend|reduce> <entity> "
                + "<id> <ticks> | cooldown clear <entity> <id>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3 || args.length > 4) {
            throw new WrongUsageException(getUsage(sender));
        }
        String action = args[0];
        Entity entity = getEntity(server, sender, args[1]);
        String id = args[2];
        int ticks = args.length == 4 ? parseInt(args[3], 0) : 0;
        CooldownOperations cooldowns = context.cooldowns;
        String cause = StatusOperations.CAUSE_COMMAND;
        String result;
        switch (action) {
            case "apply":
                result = cooldowns.start(entity, id, ticks, cause).toString();
                break;
            case "ready":
                result = "started=" + cooldowns.startIfReady(entity, id, ticks, cause)
                        + " remaining=" + cooldowns.remaining(entity, id);
                break;
            case "set":
                requireTicks(args, sender);
                result = cooldowns.restart(entity, id, ticks, cause).toString();
                break;
            case "extend":
                requireTicks(args, sender);
                result = cooldowns.extend(entity, id, ticks, cause).toString();
                break;
            case "reduce":
                requireTicks(args, sender);
                result = cooldowns.reduce(entity, id, ticks, cause).toString();
                break;
            case "clear":
                if (args.length != 3) {
                    throw new WrongUsageException(getUsage(sender));
                }
                CooldownChange r = cooldowns.clear(entity, id, cause);
                result = r.toString();
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
        context.reply(sender, "cooldown " + action + " " + id + " on " + entity.getName() + ": " + result);
    }

    private Integer requireTicks(String[] args, ICommandSender sender) throws CommandException {
        if (args.length != 4) {
            throw new WrongUsageException(getUsage(sender));
        }
        return parseInt(args[3], 1);
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
            context.registry.cooldowns().forEach(e -> ids.add(e.definition().id()));
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
