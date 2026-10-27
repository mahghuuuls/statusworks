// Statusworks example: target tracking with a mark.
//
// The last mob a player hits is "Hunted" by that player for 30 seconds: one target per player, a new hit moves the
// mark to the new mob. Only that player sees yellow particles on the hunted mob. While the mark is active the player
// gets 2 armor. If the player goes more than 48 blocks away, the mark pauses and the armor is withdrawn until they come
// back. The HUD shows the mark with the target's name in its tooltip. When the hunted mob dies, the player is told.
//
// Copy this file into your scripts folder to use it. Statusworks never runs it by itself. Use the default loader:
// scripts under "#loader preinit" are not supported.

import mods.statusworks.Marks;
import mods.statusworks.Presentation;
import crafttweaker.event.EntityLivingHurtEvent;
import crafttweaker.player.IPlayer;

Marks.define("examples:hunted")
    .targetLimit(1)
    .overflow("replace_oldest")
    .defaultDuration(600)
    .maximumDistance(48.0)
    .contributeAttribute("source", "generic.armor", "add", "fixed", 2.0)
    .onRemoved(function(ctx) {
        val hunter = ctx.source;
        if (!isNull(hunter) && hunter instanceof IPlayer && ctx.reason == "death") {
            val player as IPlayer = hunter;
            player.sendChat("Your hunt is over.");
        }
    })
    .presentation(Presentation.create()
        .name("Hunting")
        .iconItem("minecraft:bow", 0)
        .particles("mobSpellAmbient")
        .particleColor(255, 220, 0)
        .particleAudience("source"))
    .register();

events.onEntityLivingHurt(function(event as EntityLivingHurtEvent) {
    val attacker = event.damageSource.trueSource;
    if (!isNull(attacker) && attacker instanceof IPlayer && !event.entityLivingBase.world.remote) {
        Marks.mark(attacker, event.entityLivingBase, "examples:hunted");
    }
});
