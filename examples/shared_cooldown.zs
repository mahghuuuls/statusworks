// Statusworks example: a shared cooldown.
//
// Right-clicking a feather or a sugar gives a short burst of speed. Both items share one "Dash" cooldown of
// 5 seconds: using either starts it, and neither works again until it ends. When it ends, the player is told.
//
// Copy this file into your scripts folder to use it. Statusworks never runs it by itself. Use the default loader:
// scripts under "#loader preinit" are not supported.

import mods.statusworks.Cooldowns;
import mods.statusworks.Presentation;
import crafttweaker.event.PlayerRightClickItemEvent;
import crafttweaker.player.IPlayer;

Cooldowns.define("examples:dash")
    .defaultDuration(100)
    .onEnded(function(ctx) {
        val entity = ctx.entity;
        if (entity instanceof IPlayer) {
            val player as IPlayer = entity;
            player.sendChat("Dash is ready.");
        }
    })
    .presentation(Presentation.create().name("Dash").iconItem("minecraft:feather", 0))
    .register();

events.onPlayerRightClickItem(function(event as PlayerRightClickItemEvent) {
    val player = event.player;
    if (player.world.remote || isNull(event.item)) {
        return;
    }
    if (<minecraft:feather>.matches(event.item) || <minecraft:sugar>.matches(event.item)) {
        if (Cooldowns.startIfReady(player, "examples:dash")) {
            player.addPotionEffect(<potion:minecraft:speed>.makePotionEffect(40, 2));
        } else {
            player.sendChat("Dash is not ready: " + ((Cooldowns.remaining(player, "examples:dash") + 19) / 20) + " s left.");
        }
    }
});
