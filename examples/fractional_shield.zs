// Statusworks example: a fractional shield pool with capacity set from max health.
//
// Every player gets a "Shield" pool when they log in. Its capacity is half their max health (10 for a normal player).
// It starts empty and fills by 0.5 per second. When the player is hurt, the shield takes the damage first: the damage
// is reduced by what the shield holds, and the shield loses that much. The amount is fractional and shows as a bar.
// The shield stays through death ("keep").
//
// Copy this file into your scripts folder to use it. Statusworks never runs it by itself. Use the default loader:
// scripts under "#loader preinit" are not supported.

import mods.statusworks.Pools;
import mods.statusworks.Presentation;
import crafttweaker.event.EntityLivingHurtEvent;
import crafttweaker.event.PlayerLoggedInEvent;
import crafttweaker.player.IPlayer;

Pools.define("examples:shield")
    .capacity(10.0)
    .start("empty")
    .progression("fixed", "fill", 0.5)
    .deathRule("keep")
    .presentation(Presentation.create()
        .name("Shield")
        .description("Takes damage before your health. Refills slowly.")
        .iconItem("minecraft:shield", 0)
        .decimals(1))
    .register();

events.onPlayerLoggedIn(function(event as PlayerLoggedInEvent) {
    val player = event.player;
    if (!player.world.remote) {
        // Creates the pool on first use, then sets its capacity from the player's max health.
        if (!Pools.hasPool(player, "examples:shield")) {
            Pools.set(player, "examples:shield", 0.0);
        }
        Pools.setCapacity(player, "examples:shield", player.maxHealth / 2.0);
    }
});

events.onEntityLivingHurt(function(event as EntityLivingHurtEvent) {
    val hurt = event.entityLivingBase;
    if (hurt instanceof IPlayer && !hurt.world.remote) {
        val shield = Pools.get(hurt, "examples:shield");
        if (!isNull(shield) && shield.amount > 0.0) {
            val damage = event.amount as double;
            val absorbed = shield.amount < damage ? shield.amount : damage;
            Pools.subtract(hurt, "examples:shield", absorbed);
            event.amount = (damage - absorbed) as float;
        }
    }
});
