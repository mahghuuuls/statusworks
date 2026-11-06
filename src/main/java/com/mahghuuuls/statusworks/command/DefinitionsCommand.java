package com.mahghuuuls.statusworks.command;

import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolStart;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.Progression;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.diagnostic.Numbers;
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
                + registry.invalid().size() + " invalid; " + registry.pools().size() + " pools valid, "
                + registry.invalidPools().size() + " invalid; " + registry.marks().size() + " marks valid, "
                + registry.invalidMarks().size() + " invalid; " + registry.cooldowns().size() + " cooldowns valid, "
                + registry.invalidCooldowns().size() + " invalid");
        for (DefinitionRegistry.Entry e : registry.statuses()) {
            StatusDefinition d = e.definition();
            context.reply(sender, "  status " + d.id() + " origin=" + e.origin() + " limit=" + d.limit()
                    + " duration=" + d.defaultDuration() + " mode=" + d.expiration().text()
                    + " refresh=" + d.refresh().text() + " ownership=" + d.ownership().text()
                    + (d.maximumRemaining() >= 1 ? " max=" + d.maximumRemaining() : "")
                    + (d.refreshOnReject() ? "" : " refreshOnReject=false")
                    + (d.presentation().harmful() ? " harmful" : ""));
        }
        for (DefinitionRegistry.PoolEntry e : registry.pools()) {
            PoolDefinition d = e.definition();
            Progression rule = d.progression();
            context.reply(sender, "  pool " + d.id() + " origin=" + e.origin() + " capacity="
                    + Numbers.format(d.capacity()) + " start=" + d.start().text()
                    + (d.start() == PoolStart.AMOUNT ? " " + Numbers.format(d.startAmount()) : "")
                    + " rule=" + rule.text()
                    + (rule == Progression.TO_TARGET ? " target=" + Numbers.format(d.target()) + " in "
                    + d.targetDuration() : "")
                    + (rule != Progression.NONE && rule != Progression.TO_TARGET ? " " + d.direction().text() + " "
                    + Numbers.format(d.rate()) : "")
                    + (d.lifetime() >= 1 ? " lifetime=" + d.lifetime() : "")
                    + (d.cutoff() >= 0 ? " cutoff=" + Numbers.format(d.cutoff()) : "")
                    + " death=" + d.deathRule().text()
                    + (d.presentation().harmful() ? " harmful" : ""));
        }
        for (DefinitionRegistry.MarkEntry e : registry.marks()) {
            MarkDefinition d = e.definition();
            context.reply(sender, "  mark " + d.id() + " origin=" + e.origin() + " limit="
                    + (d.targetLimit() == MarkDefinition.UNLIMITED ? "unlimited" : Integer.toString(d.targetLimit()))
                    + " overflow=" + d.overflow().text() + " refresh=" + d.refresh().text()
                    + " duration=" + (d.defaultDuration() == MarkDefinition.NO_DURATION ? "none"
                    : Integer.toString(d.defaultDuration()))
                    + (d.maximumRemaining() >= 1 ? " max=" + d.maximumRemaining() : "")
                    + (d.maximumDistance() > 0 ? " distance=" + Numbers.format(d.maximumDistance()) : "")
                    + (d.maximumInactive() >= 1 ? " inactiveMax=" + d.maximumInactive() : "")
                    + (d.keepOnSourceDeath() ? " keepOnSourceDeath" : "")
                    + (d.keepOnTargetDeath() ? " keepOnTargetDeath" : "")
                    + " contributions=" + d.sourceContributions().size() + "/" + d.targetContributions().size()
                    + " harmful=" + harmfulSides(d.presentation()));
        }
        for (DefinitionRegistry.CooldownEntry e : registry.cooldowns()) {
            context.reply(sender, "  cooldown " + e.definition().id() + " origin=" + e.origin() + " duration="
                    + e.definition().defaultDuration() + " death=" + e.definition().deathRule().text()
                    + (e.definition().presentation().harmful() ? " harmful" : ""));
        }
        for (DefinitionRegistry.Error error : registry.errors()) {
            context.reply(sender, "  invalid " + error);
        }
    }

    /** On which HUD a mark is harmful: {@code source}, {@code target}, {@code both}, or {@code none}. */
    private static String harmfulSides(Presentation p) {
        if (p.markHarmfulOnSource()) {
            return p.markHarmfulOnTarget() ? "both" : "source";
        }
        return p.markHarmfulOnTarget() ? "target" : "none";
    }
}
