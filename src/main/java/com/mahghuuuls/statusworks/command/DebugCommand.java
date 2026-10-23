package com.mahghuuuls.statusworks.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * {@code /statusworks debug on|off [definition|-] [entity|-]}: switches the diagnostic log at runtime, optionally
 * limited to one definition and one entity (name, numeric id, or UUID). {@code -} means no filter.
 */
final class DebugCommand extends CommandBase {

    private final StatusworksCommand.Context context;

    DebugCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks debug <on|off> [definition|-] [entity|-]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 3 || !("on".equals(args[0]) || "off".equals(args[0]))) {
            throw new WrongUsageException(getUsage(sender));
        }
        boolean on = "on".equals(args[0]);
        String definition = args.length >= 2 && !"-".equals(args[1]) ? args[1] : null;
        String entity = args.length == 3 && !"-".equals(args[2]) ? args[2] : null;
        if (!on) {
            context.diagnosticLog.flush();
        }
        context.diagnosticLog.configure(on, definition, entity);
        context.reply(sender, "diagnostic log " + (on ? "on" : "off")
                + (definition != null ? " definition=" + definition : "")
                + (entity != null ? " entity=" + entity : ""));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "on", "off")
                : Collections.<String>emptyList();
    }
}
