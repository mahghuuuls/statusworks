// Statusworks example: a combo status kept per attacker.
//
// When a player hits a mob, the mob gets one "Combo" stack from that player, up to 10 per player. Each attacker's
// stacks are kept apart (ownership "per_source") and each stack lasts 3 seconds on its own (expiration
// "independent"). The fifth hit in a row from the same player deals double damage and clears that player's combo.
//
// Copy this file into your scripts folder to use it. Statusworks never runs it by itself. Use the default loader:
// scripts under "#loader preinit" are not supported.

import mods.statusworks.Statuses;
import mods.statusworks.Presentation;
import crafttweaker.event.EntityLivingHurtEvent;
import crafttweaker.player.IPlayer;

Statuses.define("examples:combo")
    .limit(10)
    .ownership("per_source")
    .expiration("independent")
    .defaultDuration(60)
    .presentation(Presentation.create()
        .name("Combo")
        .iconItem("minecraft:iron_sword", 0)
        .particles("crit")
        .particlesPerSecond(6.0))
    .register();

events.onEntityLivingHurt(function(event as EntityLivingHurtEvent) {
    val target = event.entityLivingBase;
    val attacker = event.damageSource.trueSource;
    if (isNull(attacker) || !(attacker instanceof IPlayer) || target.world.remote) {
        return;
    }
    Statuses.addFrom(target, "examples:combo", 1, attacker, 60);
    // The attacker's own stacks on this target, found by the attacker's UUID.
    val attackerId = Statuses.idOf(attacker);
    val count = Statuses.countFrom(target, "examples:combo", attackerId);
    if (count >= 5) {
        event.amount = event.amount * 2.0f;
        Statuses.removeFrom(target, "examples:combo", count, attackerId);
    }
});
