package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.PoolSnapshot;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.Numbers;
import com.mahghuuuls.statusworks.core.state.PoolOperations;
import com.mahghuuuls.statusworks.core.rule.CooldownRules;
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
            context.reply(sender, "  loaded " + note.kind + " " + note.id + ": " + (note.reason != null
                    ? "ended with reason " + note.reason + " (saved " + Numbers.format(note.countBefore) + ")"
                    : "fitted to its changed definition, " + Numbers.format(note.countBefore) + " -> "
                    + Numbers.format(note.countAfter)));
        }
        List<String> markLines = context.marks.describe(entity.getUniqueID());
        if (state.hasNothingToSave() && markLines.isEmpty()) {
            context.reply(sender, "  no tracked state");
            return;
        }
        context.reply(sender, "  health=" + Numbers.format(((EntityLivingBase) entity).getHealth()) + "/"
                + Numbers.format(((EntityLivingBase) entity).getMaxHealth()));
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
        for (EntityState.PoolSlot slot : state.pools()) {
            DefinitionRegistry.PoolEntry entry = context.registry.pool(slot.id);
            if (entry == null) {
                context.reply(sender, "  pool " + slot.id + " (definition missing)");
                continue;
            }
            PoolSnapshot p = PoolOperations.snapshot(entry.definition(), slot.state);
            StringBuilder line = new StringBuilder("  pool ").append(slot.id)
                    .append(" amount=").append(Numbers.format(p.amount()))
                    .append('/').append(Numbers.format(p.capacity()))
                    .append(" fill=").append(Numbers.format(p.fill()))
                    .append(" lifetime=").append(p.remainingLifetime())
                    .append(" rule=").append(entry.definition().progression().text())
                    .append(" progression=").append(p.progression().name().toLowerCase(java.util.Locale.ROOT));
            if (!Double.isNaN(p.target())) {
                line.append(" target=").append(Numbers.format(p.target()))
                        .append(" deadlineIn=").append(p.remainingToDeadline());
            }
            line.append(" age=").append(slot.state.age());
            context.reply(sender, line.toString());
        }
        for (java.util.Map.Entry<String, com.mahghuuuls.statusworks.core.contribution.Applied> e
                : state.contributions().entries().entrySet()) {
            com.mahghuuuls.statusworks.core.contribution.Applied a = e.getValue();
            String where = a.attribute ? "attribute " + a.target + " op=" + a.operation + " current="
                    + attributeValue((EntityLivingBase) entity, a.target) : "type " + a.target;
            context.reply(sender, "  contribution " + e.getKey() + " " + where + " value=" + Numbers.format(a.value));
        }
        for (EntityState.CooldownSlot slot : state.cooldowns()) {
            context.reply(sender, "  cooldown " + slot.id + " running=" + CooldownRules.running(slot.state)
                    + " remaining=" + CooldownRules.remaining(slot.state) + " age=" + slot.state.age()
                    + (context.registry.cooldown(slot.id) == null ? " (definition missing)" : ""));
        }
        for (String line : markLines) {
            context.reply(sender, "  " + line);
        }
    }

    private static String attributeValue(EntityLivingBase entity, String attribute) {
        net.minecraft.entity.ai.attributes.IAttributeInstance instance = entity.getAttributeMap()
                .getAttributeInstanceByName(attribute);
        return instance == null ? "absent" : Numbers.format(instance.getAttributeValue());
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames())
                : Collections.<String>emptyList();
    }
}
