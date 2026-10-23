package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import com.mahghuuuls.statusworks.core.state.EntityState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /statusworks inspect <entity>}: every instance on an entity, author-hidden ones included. */
final class InspectCommand extends CommandBase {

    private final StatusworksCommand.Context context;

    InspectCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "inspect";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks inspect <entity>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        Entity entity = getEntity(server, sender, args[0]);
        context.reply(sender, "inspect " + entity.getName() + "#" + entity.getEntityId() + " uuid="
                + entity.getUniqueID() + " dim=" + entity.dimension);
        if (!(entity instanceof EntityLivingBase)) {
            context.reply(sender, "  not a living entity; it holds no tracked state");
            return;
        }
        EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
        if (state == null) {
            context.reply(sender, "  cannot hold tracked state");
            return;
        }
        for (EntityState.LoadNote note : state.loadNotes()) {
            context.reply(sender, "  loaded " + note.id + ": " + (note.reason != null
                    ? "ended with reason " + note.reason + " (saved count " + note.countBefore + ")"
                    : "fitted to its changed definition, count " + note.countBefore + " -> " + note.countAfter));
        }
        if (state.isEmpty()) {
            context.reply(sender, "  no tracked state");
            return;
        }
        for (EntityState.StatusSlot slot : state.statuses()) {
            DefinitionRegistry.Entry entry = context.registry.status(slot.id);
            if (entry == null) {
                context.reply(sender, "  status " + slot.id + " (definition missing)");
                continue;
            }
            StatusDefinition def = entry.definition();
            StatusState s = slot.state;
            context.reply(sender, "  status " + slot.id + " total=" + s.total() + "/" + def.limit()
                    + " remaining=" + StatusRules.remaining(def, s, null) + " mode=" + def.expiration().text()
                    + " refresh=" + def.refresh().text() + " ownership=" + def.ownership().text()
                    + " age=" + s.age());
            for (Map.Entry<UUID, StatusState.Group> g : s.groups().entrySet()) {
                String source = StatusRules.NO_SOURCE.equals(g.getKey()) ? "no-source" : g.getKey().toString();
                StringBuilder line = new StringBuilder("    group ").append(source).append(" count=")
                        .append(g.getValue().count()).append(" remaining=")
                        .append(StatusRules.remaining(def, s, g.getKey()));
                Map<Integer, Integer> stacks = StatusRules.stackRemainders(s, g.getKey());
                if (!stacks.isEmpty()) {
                    line.append(" stacks=");
                    for (Map.Entry<Integer, Integer> st : stacks.entrySet()) {
                        line.append(st.getValue()).append("@").append(st.getKey()).append(' ');
                    }
                }
                context.reply(sender, line.toString().trim());
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames())
                : Collections.<String>emptyList();
    }
}
