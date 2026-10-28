# Writing Statusworks scripts with CraftTweaker

This guide is for modpack creators. It explains how to define statuses, pools, marks, and cooldowns in CraftTweaker scripts, how to change them from events, and how to react when they change. Complete scripts to copy are in [`examples/`](../examples/).

Statusworks needs [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker) 4.1.20 or newer for scripts. Without CraftTweaker, everything else in Statusworks works.

## Basics

- Put scripts in the pack's `scripts` folder and use the default loader. Scripts under `#loader preinit` are not supported.
- Define everything when the script loads, at the top level of a script file. Definitions are frozen when the game finishes loading; a definition made later is refused.
- Give every definition an id of the form `namespace:name`, for example `mypack:rage`, with your pack's own namespace, so ids from different packs and mods never clash. (Any text without spaces is accepted.)
- Times are in ticks: 20 ticks are one second.
- Options that choose a mode take lowercase text, such as `"gradual"` or `"per_source"`. An unknown text makes that one definition invalid; the game log names the definition and its script file, and `/statusworks definitions` lists what registered.
- A script syntax error stops CraftTweaker from running the script files loaded after it, so check `crafttweaker.log` after an edit.
- Change state only on the server: in an event handler, check `!entity.world.remote` first.

Import the classes you use:

```zenscript
import mods.statusworks.Statuses;
import mods.statusworks.Pools;
import mods.statusworks.Marks;
import mods.statusworks.Cooldowns;
import mods.statusworks.Contributions;
import mods.statusworks.Presentation;
```

## A first status

A stack of "Rage" for each hit, up to 5, each stack +1 attack damage, falling off one at a time 5 seconds after the last hit (from [`stacking_counter.zs`](../examples/stacking_counter.zs)):

```zenscript
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
```

`register()` returns `true` when the definition was accepted for registration. Mistakes in it, such as an unknown mode text or a bad number, are found when the game finishes loading: the log names the definition and its script file, and the definition is disabled.

## Statuses

A status is a number of stacks on an entity.

**Defining** (`Statuses.define(id)`, then any of these, then `.register()`):

| Option | Meaning |
| --- | --- |
| `limit(n)` | Most stacks (default 1); with `per_source`, for each source. |
| `defaultDuration(ticks)` | Duration when an operation gives none (default 200). |
| `expiration(mode)` | `shared` (default): one timer, all stacks end together. `gradual`: one timer, each expiry removes one stack and restarts it. `independent`: every application keeps its own timer. `none`: stacks stay until removed. |
| `refresh(rule)` | On a new application: `reset` (default) sets the timer to the new duration, `extend` adds it, `preserve` keeps the timer. No effect with `independent`. |
| `maximumRemaining(ticks)` | Cap for `extend`. |
| `refreshOnReject(bool)` | Whether an application past the limit still refreshes the timer (default `true`). |
| `ownership(mode)` | `shared` (default) or `per_source`: each source has its own stacks and timer on the same entity. |
| `deathRule(rule)` | `clear` (default) or `keep` through a player's death. |
| `contributeAttribute(attribute, operation, form, value)` | See [Contributions](#contributions). |
| `contributeType(typeId, form, value)` | A contribution type another mod registered. |
| `onApplied`, `onQuantityChanged`, `onRemoved` | Callbacks, see [Callbacks](#callbacks). |
| `onInterval(ticks, function)` | Runs every so many ticks while the status lasts. |
| `presentation(...)` | How it shows, see [Presentation](#presentation). |

**Changing and reading:**

| Method | What it does |
| --- | --- |
| `Statuses.add(entity, id, stacks)` | Adds stacks with the default duration and no source. |
| `Statuses.addFrom(entity, id, stacks, source, ticks)` | Adds stacks from a source entity, with a duration (below 1: the default). |
| `Statuses.remove(entity, id, stacks)` | Removes stacks. |
| `Statuses.removeFrom(entity, id, stacks, sourceId)` | Removes stacks of one source. |
| `Statuses.setCount(entity, id, count, sourceId)` | Sets the count of one source (`""` for no source). |
| `Statuses.clear(entity, id)` | Removes the status. |
| `Statuses.hasStatus(entity, id)` | `true` when the entity has any stacks. |
| `Statuses.count(entity, id)`, `countFrom(entity, id, sourceId)` | Stacks in total, or of one source. |
| `Statuses.remaining(entity, id)`, `remainingFrom(entity, id, sourceId)` | Ticks left (`-1` without a timer). |
| `Statuses.sources(entity, id)` | The source ids holding stacks. |
| `Statuses.idOf(entity)` | An entity's id as text, to use as a `sourceId`. |

A source is an entity when stacks are added and its id text everywhere else. `idOf` gives that text. A combo kept apart for each attacker (from [`combo_per_attacker.zs`](../examples/combo_per_attacker.zs)):

```zenscript
Statuses.addFrom(target, "examples:combo", 1, attacker, 60);
val count = Statuses.countFrom(target, "examples:combo", Statuses.idOf(attacker));
if (count >= 5) {
    event.amount = event.amount * 2.0f;
    Statuses.removeFrom(target, "examples:combo", count, Statuses.idOf(attacker));
}
```

## Pools

A pool is an amount between 0 and a capacity, with fractions.

**Defining** (`Pools.define(id)`):

| Option | Meaning |
| --- | --- |
| `capacity(value)` | Maximum amount (default 1). |
| `start(mode)`, `startAmount(value)` | `empty` (default), `full`, or `amount` with `startAmount`. |
| `progression(rule, direction, ratePerSecond)` | Changes the amount by itself: `fixed` (amount per second), `percent_of_capacity`, or `percent_of_current` (percent per second); direction `fill` or `drain`. |
| `toTarget(target, ticks)` | Moves to a target amount over a time instead. |
| `cutoff(amount)` | For a `percent_of_current` drain, the amount that counts as 0. |
| `lifetime(ticks)` | Ends the pool after this much time. |
| `deathRule(rule)` | `clear` (default) or `keep`. |
| `contributeAttribute(...)`, `contributeType(...)`, `updateStep(percent)` | Contributions; the step is how much the fill must change before the contribution updates (default 1 percent). |
| `onAmountChanged`, `onEmpty`, `onFull`, `onTargetReached`, `onRemoved` | Callbacks. |
| `presentation(...)` | How it shows. |

**Changing and reading:**

- `Pools.add`, `subtract`, `set` change the amount; they create the pool when the entity has none.
- `Pools.setCapacity`, `setLifetime`, `extendLifetime`, `pause`, `resume`, `remove` need an existing pool.
- `Pools.hasPool(entity, id)`.
- `Pools.get(entity, id)` gives a `PoolInfo` (`amount`, `capacity`, `fill`, `remainingLifetime`, `progression`, `target`, `remainingToDeadline`) or `null`.

A shield that absorbs damage (from [`fractional_shield.zs`](../examples/fractional_shield.zs)):

```zenscript
val shield = Pools.get(player, "examples:shield");
if (!isNull(shield) && shield.amount > 0.0) {
    val damage = event.amount as double;
    val absorbed = shield.amount < damage ? shield.amount : damage;
    Pools.subtract(player, "examples:shield", absorbed);
    event.amount = (damage - absorbed) as float;
}
```

## Marks

A mark links a source entity to a target entity, such as a player and the mob it hunts.

**Defining** (`Marks.define(id)`):

| Option | Meaning |
| --- | --- |
| `targetLimit(n)`, `unlimitedTargets()` | How many targets one source may mark (default 1). |
| `overflow(rule)` | At the limit: `replace_oldest` (default) or `reject`. |
| `defaultDuration(ticks)` (default 200), `noDuration()`, `refresh(rule)`, `maximumRemaining(ticks)` | The mark's timer, as for statuses. |
| `keepOnSourceDeath(bool)`, `keepOnTargetDeath(bool)` | Keep the mark through a player participant's death (it is inactive until the respawn). A mob's death always ends the mark. |
| `maximumDistance(blocks)` | Beyond this distance the mark is inactive (paused); 0 (the default) means no maximum. |
| `maximumInactive(ticks)` | Ends the mark after this long inactive for any other reason (an unloaded participant). |
| `contributeAttribute(side, ...)`, `contributeType(side, ...)` | Side `source` or `target`: who gets the contribution. |
| `onApplied`, `onInactive`, `onActive`, `onRemoved`, `onInterval(ticks, ...)` | Callbacks. |
| `presentation(...)` | How it shows; `markHud(onSource, onTarget)` picks whose HUD shows it. |

**Changing and reading:**

- `Marks.mark(source, target, id)`, `markFor(source, target, id, ticks)`: the result's `outcome` is `created`, `refreshed`, `replaced`, or `rejected`.
- `Marks.unmark(source, target, id)`, `clearSource(source, id)`, `clearTarget(target, id)`, `isMarked(source, target, id)`.
- `Marks.targets(source, id)` and `sources(target, id)` give arrays of `MarkInfo` (`definitionId`, `sourceId`, `targetId`, `other`, `active`, `remaining`, `inactiveTicks`); `targetEntities` and `sourceEntities` give the entities; `targetCount` and `sourceCount` the numbers.

## Cooldowns

A cooldown is a timer on an entity.

**Defining** (`Cooldowns.define(id)`): `defaultDuration(ticks)`, `deathRule(rule)` (default `keep`, unlike statuses and pools), `onEnded(function)`, `presentation(...)`. Cooldowns carry no contributions.

**Changing and reading:** `Cooldowns.start(entity, id)`, `startFor(entity, id, ticks)`, `restart`, `extend`, `reduce`, `clear`, `isRunning`, `remaining`, and `startIfReady(entity, id)` / `startIfReadyFor(entity, id, ticks)`, which start the cooldown only when it is not running and return whether they did.

One cooldown shared by two items (from [`shared_cooldown.zs`](../examples/shared_cooldown.zs)):

```zenscript
if (<minecraft:feather>.matches(event.item) || <minecraft:sugar>.matches(event.item)) {
    if (Cooldowns.startIfReady(player, "examples:dash")) {
        player.addPotionEffect(<potion:minecraft:speed>.makePotionEffect(40, 2));
    } else {
        player.sendChat("Dash is not ready.");
    }
}
```

## Contributions

A contribution is a value that state adds to an entity while it lasts, kept in step with the state.

- `contributeAttribute(attribute, operation, form, value)`: an attribute such as `generic.attackDamage`, `generic.maxHealth`, `generic.movementSpeed`, or `generic.armor`; operation `add`, `multiply_base`, or `multiply_total`.
- `contributeType(typeId, form, value)`: a contribution type another mod registered through the Java API.
- Form: `fixed` (the value) for every kind; `per_stack` (value times the stacks) for statuses; `per_amount` (value times the amount) and `per_fill` (value times the fill, 0 to 1) for pools. Marks take `fixed` only. Another form makes the definition invalid.
- `Contributions.keys(entity, typeId)`, `value(entity, typeId, key)`, and `total(entity, typeId)` read the current values of a contribution type.

## Callbacks

A callback is a function that runs on the server when state changes:

```zenscript
Statuses.define("mypack:burning_rage")
    .limit(3)
    .defaultDuration(200)
    .onApplied(function(ctx) {
        print(ctx.definitionId + " now has " + ctx.countAfter + " stacks");
    })
    .onRemoved(function(ctx) {
        print("rage ended: " + ctx.reason);
    })
    .register();
```

The context depends on the kind:

- Status: `entity`, `definitionId`, `countBefore`, `countAfter`, `source` (the source's id as text, empty when none), `sourceEntity` (the source entity, or `null` when it is not loaded), `reason`.
- Pool: `entity`, `definitionId`, `amountBefore`, `amountAfter`, `capacity`, `reason`.
- Mark: `definitionId`, `sourceId` and `targetId` (ids as text), `source` and `target` (the entities, or `null` when not loaded), `reason`.
- Cooldown: `entity`, `definitionId`, `reason`.

`reason` is `expired`, `removed`, `replaced`, `death`, `gone`, `inactive_timeout`, or `invalid` when state ends, and empty otherwise. A callback that throws an error is caught and logged once, and `/statusworks errors` counts the failures; after 10 failures in a row the callback is disabled until the next restart. It never shows a red chat line to players.

## Presentation

`Presentation.create()` and then any of:

- `name(text)` or `nameKey(langKey)`, `description(text)` or `descriptionKey(langKey)`.
- `iconItem(itemId, meta)` or `iconTexture(resourceLocation)`.
- `showCount(bool)`, `showAmount(bool)`, `showTime(bool)`, `decimals(n)` (for pool amounts), `priority(n)` (lower first on the HUD).
- `hidden()`: never on any HUD; players cannot reveal it.
- `markHud(onSource, onTarget)`: for marks.
- `particles(type)`, `particleColor(red, green, blue)` (for `reddust`, `mobSpell`, `mobSpellAmbient`), `particlesPerSecond(rate)` (above 0, at most 100, default 4), `particleAudience(audience)`, `particlesLongDistance()`. The audience is `nearby` (the default, and the only one for statuses and pools) or, for marks, `source`, `target`, or `both`. Cooldowns take no particles. Particle types are vanilla's names, such as `flame`, `heart`, `angryVillager`, `happyVillager`, `reddust`, or `mobSpell`.

## Operation results

Most change methods return a result: `succeeded`, `failure` (why not), and depending on the kind `added`, `rejected`, `before`, `after`, `outcome`, `replacedTarget`. For example:

```zenscript
val result = Statuses.add(player, "mypack:focus", 2);
if (!result.succeeded) {
    print("focus not added: " + result.failure);
}
```

## Testing your scripts

- `/statusworks definitions` lists every definition with its file.
- `/statusworks inspect <entity>` shows all state on an entity.
- `/statusworks status|pool|mark|cooldown ...` change state by command, to test without scripts.
- `/statusworks debug on` writes each change to the server log.
