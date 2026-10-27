// Statusworks example: a stacking counter.
//
// Each time a player hits a living thing, the player gains one stack of "Rage", up to 5. Each stack adds 1 attack
// damage. The stacks fall off one by one: 5 seconds after the last hit, one stack is lost, then another every 5
// seconds (the "gradual" expiration). A new hit resets the timer.
//
// Copy this file into your scripts folder to use it. Statusworks never runs it by itself. Use the default loader:
// scripts under "#loader preinit" are not supported.

import mods.statusworks.Statuses;
import mods.statusworks.Presentation;
import crafttweaker.event.EntityLivingHurtEvent;
import crafttweaker.player.IPlayer;

Statuses.define("examples:rage")
    .limit(5)
    .expiration("gradual")
    .refresh("reset")
    .defaultDuration(100)
    .contributeAttribute("generic.attackDamage", "add", "per_stack", 1.0)
    .presentation(Presentation.create()
        .name("Rage")
        .description("+1 attack damage per stack. Hit to keep it up.")
        .iconItem("minecraft:blaze_powder", 0))
    .register();

events.onEntityLivingHurt(function(event as EntityLivingHurtEvent) {
    val attacker = event.damageSource.trueSource;
    if (!isNull(attacker) && attacker instanceof IPlayer && !event.entityLivingBase.world.remote) {
        Statuses.add(attacker, "examples:rage", 1);
    }
});
