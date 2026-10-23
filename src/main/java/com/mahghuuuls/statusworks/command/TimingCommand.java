package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.core.diagnostic.Timings;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

/** {@code /statusworks timing}: Statusworks' own server time per tick over the last 200 ticks, in microseconds. */
final class TimingCommand extends CommandBase {

    private final StatusworksCommand.Context context;

    TimingCommand(StatusworksCommand.Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "timing";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/statusworks timing";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        Timings t = context.timings;
        context.reply(sender, String.format(Locale.ROOT,
                "timing over %d ticks: average %.1f us, highest %.1f us per tick",
                t.sampleCount(), t.averageMicros(), t.highestMicros()));
    }
}
