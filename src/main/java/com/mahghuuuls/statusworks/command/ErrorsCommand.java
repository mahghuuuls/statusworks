package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/** {@code /statusworks errors}: callback failure counts since start, and which callbacks are disabled. */
final class ErrorsCommand extends CommandBase {

    private final StatusworksCommand.Context context;

    ErrorsCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "errors";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks errors";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        List<ErrorCounts.Entry> entries = context.errorCounts.snapshot();
        if (entries.isEmpty()) {
            context.reply(sender, "errors: no callback failures");
            return;
        }
        context.reply(sender, "errors: " + entries.size() + " callback(s) failed");
        for (ErrorCounts.Entry e : entries) {
            context.reply(sender, "  " + e.key + ": " + e.total() + " failure(s), " + e.consecutive() + " in a row"
                    + (e.disabled() ? ", disabled" : ""));
        }
    }
}
