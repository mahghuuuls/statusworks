package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.MarkSnapshot;
import com.mahghuuuls.statusworks.core.mark.MarkStore;
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
 * {@code /statusworks mark ...}: test operations and queries with exactly the rules of the API.
 *
 * <pre>
 * mark apply       &lt;source&gt; &lt;target&gt; &lt;id&gt; [ticks]   (creates, refreshes, replaces, or rejects)
 * mark clear       &lt;source&gt; &lt;target&gt; &lt;id&gt;
 * mark clearsource &lt;source&gt; &lt;id&gt;
 * mark cleartarget &lt;target&gt; &lt;id&gt;
 * mark targets     &lt;source&gt; &lt;id&gt;   (the source's marks: count and loaded entities)
 * mark sources     &lt;target&gt; &lt;id&gt;   (the marks on the target: count and loaded entities)
 * </pre>
 * Ticks of 0 or none mean the definition's default.
 */
final class MarkCommand extends CommandBase {

    private static final String[] ACTIONS = {"apply", "clear", "clearsource", "cleartarget", "targets", "sources"};

    private final StatusworksCommand.Context context;

    MarkCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "mark";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks mark apply <source> <target> <id> [ticks] | mark clear <source> <target> <id> | "
                + "mark <clearsource|cleartarget|targets|sources> <entity> <id>";
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
        MarkStore marks = context.marks;
        String cause = StatusOperations.CAUSE_COMMAND;
        String action = args[0];
        switch (action) {
            case "apply":
            case "clear": {
                if (args.length < 4 || args.length > (action.equals("apply") ? 5 : 4)) {
                    throw new WrongUsageException(getUsage(sender));
                }
                Entity source = getEntity(server, sender, args[1]);
                Entity target = getEntity(server, sender, args[2]);
                String id = args[3];
                String result;
                if (action.equals("apply")) {
                    int ticks = args.length == 5 ? parseInt(args[4], 0) : 0;
                    result = marks.mark(source, target, id, ticks, cause).toString();
                } else {
                    result = "removed=" + marks.unmark(source, target, id, cause);
                }
                context.reply(sender, "mark " + action + " " + id + " " + source.getName() + "->" + target.getName()
                        + ": " + result);
                return;
            }
            case "clearsource":
            case "cleartarget": {
                if (args.length != 3) {
                    throw new WrongUsageException(getUsage(sender));
                }
                Entity entity = getEntity(server, sender, args[1]);
                int ended = marks.clear(entity, args[2], action.equals("clearsource"), cause);
                context.reply(sender, "mark " + action + " " + args[2] + " on " + entity.getName() + ": ended="
                        + ended);
                return;
            }
            case "targets":
            case "sources": {
                if (args.length != 3) {
                    throw new WrongUsageException(getUsage(sender));
                }
                Entity entity = getEntity(server, sender, args[1]);
                boolean targets = action.equals("targets");
                List<MarkSnapshot> list = targets ? marks.targets(entity, args[2]) : marks.sources(entity, args[2]);
                int loaded = (targets ? marks.targetEntities(entity, args[2]) : marks.sourceEntities(entity, args[2]))
                        .size();
                context.reply(sender, "mark " + action + " " + args[2] + " of " + entity.getName() + ": count="
                        + list.size() + " loaded=" + loaded);
                for (MarkSnapshot s : list) {
                    context.reply(sender, "  " + (targets ? "target " + s.targetId() : "source " + s.sourceId())
                            + " entity=" + (s.other() == null ? "not-loaded" : s.other().getName() + "#"
                            + s.other().getEntityId() + " dim=" + s.other().dimension)
                            + " active=" + s.isActive() + " remaining="
                            + (s.remaining() == MarkSnapshot.NO_DURATION ? "none" : Integer.toString(s.remaining()))
                            + " inactive=" + s.inactiveTicks());
                }
                return;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2 || (args.length == 3 && ("apply".equals(args[0]) || "clear".equals(args[0])))) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 3 || args.length == 4) {
            List<String> ids = new ArrayList<>();
            context.registry.marks().forEach(e -> ids.add(e.definition().id()));
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
