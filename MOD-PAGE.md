Statusworks is a library that tracks custom state on players and mobs: statuses with stacks, pools, marks between two entities, and cooldowns. Modpack creators define them with [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker) scripts, and mods define them through a Java API. Statusworks adds no content or gameplay effects of its own. On the client, it shows your potion effects on its HUD instead of vanilla's icons and inventory list, unless you turn that off.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

---

## What packs and mods can build with it

None of these come with Statusworks. They are examples of what a pack's scripts or another mod can make with it:

- **Rage:** each hit gives a stack, up to 5; each stack adds attack damage, and the stacks fall off one at a time when you stop fighting.
- **Shield:** a pool that fills slowly and absorbs damage before your health.
- **Combo:** stacks counted separately for each attacker, so the fifth hit in a row from the same player deals double damage.
- **Hunt:** a mark from a player to the last mob they hit, with particles only that player sees, and armor while the hunt lasts.
- **Dash:** one cooldown shared by two items.
- **Also:** bleed or poison with a timer per application; mana or stamina that drains and refills; a tether between two players that pauses when they move apart.

## For Players

- A HUD at the top right shows each entry's icon, its stack count, its remaining time, and a bar for pools. Your own potion effects show there too, so one place shows everything.
- Buffs and debuffs are separate groups: debuffs sit in their own group after a gap, with a red border, and potion effects sit on vanilla's potion frame, so you can tell them apart at a glance.
- In the inventory, pointing at an entry explains it: what it does, its exact time, who it came from, and whether it is harmful.
- Some entries show particles on mobs and players; the pack decides who sees them.
- `config/statusworks-client.cfg` lets you move the HUD to any of nine screen positions, change its size and layout direction, show a named list instead of the icon grid, hide single entries, turn off particles, show buffs and debuffs as one group, turn off the red borders or the potion frames, or keep vanilla's potion display. These settings only change what you see.

## For Pack Creators

With CraftTweaker installed, scripts define state, change it from any event, and run code when it changes:

```zenscript
import mods.statusworks.Statuses;
import mods.statusworks.Presentation;
import crafttweaker.event.EntityLivingHurtEvent;
import crafttweaker.player.IPlayer;

Statuses.define("mypack:rage")
    .limit(5)
    .expiration("gradual")
    .defaultDuration(100)
    .contributeAttribute("generic.attackDamage", "add", "per_stack", 1.0)
    .presentation(Presentation.create().name("Rage").iconItem("minecraft:blaze_powder", 0))
    .register();

events.onEntityLivingHurt(function(event as EntityLivingHurtEvent) {
    val attacker = event.damageSource.trueSource;
    if (!isNull(attacker) && attacker instanceof IPlayer && !event.entityLivingBase.world.remote) {
        Statuses.add(attacker, "mypack:rage", 1);
    }
});
```

- Statuses, pools, marks, and cooldowns each have their own options: timers that are shared, decay one stack at a time, or run per application; stacks per source; pools that fill, drain, or move to a target; marks with target limits and distance; death rules.
- They can add attribute bonuses, such as attack damage, max health, speed, or armor, that follow the stacks or the pool's fill. Attributes that other mods add to players or to all mobs work too, such as [Potion Core](https://www.curseforge.com/minecraft/mc-mods/potion-core)'s jump height and step height.
- `.harmful()` makes an entry a debuff: it shows in the debuff group with a red border. By default a mark is a buff for the hunter and a debuff for the hunted.
- Callbacks run when state is applied, changes, fills, empties, ends, or at an interval.
- `/statusworks` (operators only) inspects and changes state on any entity, lists every definition and its script file, and logs each change for testing.
- Use the default script loader; scripts under `#loader preinit` are not supported.
- Server settings in `config/statusworks.cfg`: strict startup (the game stops at startup when a definition is invalid, instead of disabling it) and the diagnostic log. The [README](https://github.com/mahghuuuls/statusworks#configuration) lists every server and client setting.

Guide: [Writing Statusworks scripts](https://github.com/mahghuuuls/statusworks/blob/main/docs/crafttweaker.md). Five complete scripts to copy: [examples](https://github.com/mahghuuuls/statusworks/tree/main/examples).

## For Mod Authors

The Java API in `com.mahghuuuls.statusworks.api` gives your mod the same four kinds of state on any living entity, with saving, client sync, HUD entries, particles, and attribute bonuses handled for you:

- Register definitions with builders during startup, then add, remove, and query state from your code.
- Register your own contribution type, so statuses and pools drive your own stats, such as mana regeneration.
- If your mod computes one result from all of a contribution type's values on an entity, `ContributionGroupType` tells it before those values change and hands it the complete set afterwards, so it can tell when that result is current.
- Get callbacks as lambdas when state changes.
- Read the player's visible entries on the client to draw your own visuals.

Guide: [Using Statusworks from a Java mod](https://github.com/mahghuuuls/statusworks/blob/main/docs/java-api.md). Example: [JavaExample.java](https://github.com/mahghuuuls/statusworks/blob/main/examples/JavaExample.java).

## Installation

Install the same version of Statusworks on the server and every client. It needs no other library; scripts need CraftTweaker 4.1.20 or newer.

Source code: [github.com/mahghuuuls/statusworks](https://github.com/mahghuuuls/statusworks)
