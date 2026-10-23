package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/** {@code /statusworks definitions}: every registered definition with its origin, and every invalid one. */
final class DefinitionsCommand extends CommandBase {

    private final StatusworksCommand.Context context;

    DefinitionsCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "definitions";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks definitions";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        DefinitionRegistry registry = context.registry;
        context.reply(sender, "definitions: " + registry.statuses().size() + " statuses valid, "
                + registry.invalid().size() + " invalid");
        for (DefinitionRegistry.Entry e : registry.statuses()) {
            StatusDefinition d = e.definition();
            context.reply(sender, "  status " + d.id() + " origin=" + e.origin() + " limit=" + d.limit()
                    + " duration=" + d.defaultDuration() + " mode=" + d.expiration().text()
                    + " refresh=" + d.refresh().text() + " ownership=" + d.ownership().text()
                    + (d.maximumRemaining() >= 1 ? " max=" + d.maximumRemaining() : "")
                    + (d.refreshOnReject() ? "" : " refreshOnReject=false"));
        }
        for (DefinitionRegistry.Error error : registry.errors()) {
            context.reply(sender, "  invalid " + error);
        }
    }
}
