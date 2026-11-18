# Using Statusworks from a Java mod

This guide is for mod authors. Statusworks gives your mod statuses, pools, marks, and cooldowns on any living entity, with saving, syncing to clients, HUD entries, particles, and attribute bonuses already handled. The full example is [`examples/JavaExample.java`](../examples/JavaExample.java). The same concepts, with every option explained, are in the [scripting guide](crafttweaker.md); the Java builders take the same options as enums or as the same lowercase text.

## Setup

- Compile against the Statusworks jar and declare `dependencies = "required-after:statusworks"` in your `@Mod`; a mod that uses the status snapshot declares `required-after:statusworks@[1.4.0,)`, so with an older Statusworks, Forge stops at startup with a missing-dependency error instead of failing later.
- Everything public is under `com.mahghuuuls.statusworks.api`. Nothing outside that package is API.
- Register definitions during startup (pre-init to post-init). Definitions freeze when loading completes; a later registration is refused.
- Operations run on the server. On a client world they do nothing.

## Defining

`StatusworksApi` registers definitions built with the builders in `api.definition`:

```java
StatusworksApi.registerStatus(StatusDefinition.builder("mymod:rage")
        .limit(5)
        .expiration(ExpirationMode.GRADUAL)
        .defaultDuration(100)
        .contribute(Contribution.attribute("generic.attackDamage", AttributeOperation.ADD, ValueForm.PER_STACK, 1))
        .onApplied(context -> MyMod.LOGGER.info("Rage on {}", context.entity().getName()))
        .presentation(Presentation.builder().name("Rage").iconItem("minecraft:blaze_powder", 0).build())
        .build());
```

- `registerStatus`, `registerPool`, `registerMark`, `registerCooldown` take a `StatusDefinition`, `PoolDefinition`, `MarkDefinition`, or `CooldownDefinition` from its `builder(id)`.
- Attribute contributions can name other mods' attributes, such as Potion Core's `potioncore.jumpHeight`, when a player or a zombie has the attribute at server start; see the [scripting guide](crafttweaker.md#contributions) for the rules.
- Each returns a `RegistrationResult`: `isAccepted()`, or `error()` with the reason (for example, registration after the freeze). Acceptance does not check the definition: it is validated when loading completes, and an invalid one is named in the log and disabled (or stops the game when the server's `strictMode` is on).
- `Presentation.builder()` sets the name (or a language key), description, icon, which numbers show, priority, `hidden()`, `harmful()` (a debuff on the HUD; for a mark, on both sides), `markHarmful(onSource, onTarget)` for a mark's two sides (default `false, true`; the last of the two calls wins), and `particles(Particles.builder(type)...build())` with color, rate, and audience.

## Changing and querying state

| Service | Main methods |
| --- | --- |
| `StatusworksApi.statuses()` | `apply(entity, id, stacks)`, `apply(entity, id, stacks, source, ticks)`, `remove`, `setCount`, `clear`, `has`, `count`, `remainingTicks`, `sources`, `snapshot(entity)` (see Status snapshot) |
| `StatusworksApi.pools()` | `add`, `subtract`, `set`, `setCapacity`, `setLifetime`, `extendLifetime`, `pause`, `resume`, `remove`, `has`, `get` (a `PoolSnapshot`) |
| `StatusworksApi.marks()` | `mark(source, target, id)`, `mark(source, target, id, ticks)`, `unmark`, `clearSource`, `clearTarget`, `isMarked`, `targets`, `sources`, `targetEntities`, `sourceEntities`, `targetCount`, `sourceCount` |
| `StatusworksApi.cooldowns()` | `start`, `restart`, `extend`, `reduce`, `clear`, `startIfReady`, `isRunning`, `remaining` |
| `StatusworksApi.contributions()` | `currentValues(entity, typeId)`, `group(entity, typeId)` |

Change methods return a result object with `succeeded()` and what changed: `StackChange` (`added()`, `rejected()`), `CountChange`, `PoolChange`, and `CooldownChange` (values before and after), each with `failure()` saying why it did not succeed, and `MarkResult` with `outcome()`, `replacedTarget()`, and `reason()` (set only when rejected). `unmark`, `isMarked`, `clearSource`, `clearTarget`, and `startIfReady` return a boolean or a count. Queries change nothing.

### Status snapshot

`StatusworksApi.statuses().snapshot(entity)` (added in 1.4.0) returns a `StatusSnapshot` of every status on one living entity that its author did not hide, for a mod that shows statuses its own way, such as a HUD of its own or a list above mobs:

```java
StatusSnapshot snapshot = StatusworksApi.statuses().snapshot(mob);   // on the server thread
for (StatusSnapshot.Entry row : snapshot.entries()) {
    String name = row.name();             // a translation key when row.nameIsKey(): translate it on the client
    int stacks = row.count();
    int ticks = row.remainingTicks();     // -1: the status does not expire
    for (StatusSnapshot.Group group : row.groups()) {
        // per-source statuses only: group.source(), group.count(), group.remainingTicks()
    }
}
```

- Each `Entry` has `definitionId()`, `name()` and `nameIsKey()` (a definition without a name gives its id's path, as Statusworks' HUD shows it, never a key), `iconItem()` and `iconMeta()` or `iconTexture()` (either may be null), `count()` (the total), `remainingTicks()`, `harmful()`, `priority()`, `showCount()` and `showTime()` (the author's choice of what the HUD shows), and `groups()`.
- For a per-source status the total is the sum of the groups and the remaining time is the soonest group's; a shared status has no groups. Rows come in registration order, not by priority.
- A status whose definition is `hidden()` is left out. A player's own HUD hiding (`hiddenEntries`) is not applied: it is that player's view, not the author's.
- Names are given as the definition writes them and are not translated: on a dedicated server there is no language to translate them into.
- Call it on the server thread. It reads only that entity's state and the definitions it names, scans no other entity or chunk, costs about as much as the number of statuses on that entity, and changes nothing. The snapshot is a copy that holds no entity, state or definition, and its lists cannot be changed.
- It is empty (`StatusSnapshot.EMPTY`, `isEmpty()`) on the client, for an entity that is not living, for a fake player, and for an entity object that is not in a world: a mob whose chunk unloaded, the old object after a dimension change, a player object replaced at a respawn. This differs from the per-id queries, which still read such an object. It already lists the statuses of a mob that Forge is adding to a world, so you can read it in `PlayerEvent.StartTracking` to send the rows to a player who starts seeing that mob.
- A dead entity still in its world lists what it kept: nothing for a mob, the kept statuses (death rule `keep`) of a player until it respawns.
- Statusworks sends no snapshot to clients; your mod does its own syncing and decides who may see what. Two snapshots with the same rows are equal, but the rows include remaining times, which change every tick for a timed status: to send only on change, compare the rows without their times. A countdown on the client from a remaining time is an estimate: a status's time passes only while the game updates its entity, so resend the rows when they change, and from time to time to correct the countdown.
- It covers statuses only, not pools, marks or cooldowns, and it does not include a status's description or particle settings. The constructors of `StatusSnapshot`, `Entry` and `Group` are for Statusworks and for test doubles; they may gain parameters in a later version, so read snapshots rather than build them.

## Callbacks

Builders take callbacks as lambdas: `onApplied`, `onQuantityChanged`, `onRemoved`, `onInterval(ticks, ...)` for statuses; `onAmountChanged`, `onEmpty`, `onFull`, `onTargetReached`, `onRemoved` for pools; `onApplied`, `onInactive`, `onActive`, `onRemoved`, `onInterval` for marks; `onEnded` for cooldowns. Each receives a context from `api.callback`: `StatusContext` (entity, definition id, counts before and after, source and source entity, removal reason), `PoolContext` (entity, definition id, amounts before and after, capacity, reason), `MarkContext` (definition id, source and target with their ids, reason), `CooldownContext` (entity, definition id, reason). They run on the server; an exception is caught, logged once, and counted, and after 10 failures in a row the callback is disabled until restart.

## Your own contribution types

Attribute modifiers are built in. To let statuses, pools, and marks drive something else, such as a mana regeneration rate or a custom stat, register a `ContributionType` (`api.contribution`) during startup:

```java
StatusworksApi.registerContributionType(new ContributionType() {
    public String id() { return "mymod:mana_regen"; }
    public boolean appliesTo(EntityLivingBase entity) { return entity instanceof EntityPlayer; }
    public void onValue(EntityLivingBase entity, String key, double value) { /* apply the value */ }
    public void onEnd(EntityLivingBase entity, String key) { /* remove it */ }
});
```

Statusworks calls `onValue` whenever a contribution's value changes, and again with the same value whenever the entity joins a world (load, dimension change, respawn), so it must be safe to repeat; it calls `onEnd` when the contribution ends. There is one key per contributing instance. `appliesTo` decides which entities the type acts on. Definitions then use `Contribution.type("mymod:mana_regen", ValueForm.PER_STACK, 0.5)`, and scripts use `contributeType`.

Notices run after Statusworks has applied the whole change (attribute modifiers, saved state, health), so `contributions().currentValues(...)` already shows the new values, and the callback depth limit never drops them. Like a callback, a notice that throws 10 times in a row is switched off until restart. If your notice changes Statusworks state, the change takes effect at once, and the update of this entity's contributions that it causes runs after the current one; changes to other entities are applied at once. At most 8 such updates run in a row; the next waits for the next server tick, and the log warns once per contribution type. While updates for an entity wait, its groups read `PENDING` (see Group signals).

### Group signals

When your mod computes one result from all of a type's values on an entity, such as a maximum built from several bonuses, implement `ContributionGroupType` instead. It adds two signals:

```java
private final Map<EntityLivingBase, Double> cache = new WeakHashMap<>();

public boolean invalidate(EntityLivingBase entity) {
    cache.remove(entity);   // stop treating the old result as current
    return true;            // confirm
}

public void settled(EntityLivingBase entity, Map<String, Double> group) {
    cache.put(entity, sum(group));   // the complete group; empty means none
}
```

- For each entity the type applies to, Statusworks calls `invalidate` before the group can change, then the change's `onValue` and `onEnd` notices, then `settled` with every value. Every join of the entity to a world sends a pair too, even with an empty group; at a join, the replayed `onValue` notices and any notices of the join's own update come first, then the pair.
- Only `true` confirms an invalidation. `false` or an exception makes the group failed: Statusworks still applies the change, sends no `settled`, and retries once a second, up to 60 times, then logs one warning and the group stays failed. The next change to that type's values, join, respawn, or End exit starts a new series. Group signals are never switched off after errors.
- `contributions().group(entity, typeId)` returns a `ContributionGroup`: `availability()` is `UNAVAILABLE` (no group for this entity object yet, an object that was replaced, an entity that left its world until it joins again, an entity the type does not apply to, or on the client), `UPDATING` (invalidated, settled signal not started), `FAILED`, `PENDING`, or `AVAILABLE`, and `values()` holds the complete group only when available. Use a stored result only while the group is available; inside `settled` it already is, unless more updates for the entity are waiting. An entity that leaves its world (unloading, removal, the start of a dimension change, logout) gets no signal, so read `group(...)` before each use, and keep cached results in a weak-keyed map.
- `PENDING` (added in 1.3.0) means contribution updates for this entity object are still waiting: an update asked for while another one runs, or updates held back to the next server tick. Every group type on the entity answers it, even types the waiting updates will not change, and even when read inside `settled` or another Statusworks callback. When several answers apply, `UNAVAILABLE` comes first, then `FAILED`, then `PENDING`, then `UPDATING` or `AVAILABLE`.
- While a group is pending, do not use a stored result, but you need not discard it: if the waiting updates change your type, `invalidate` comes first and then `settled`; if they do not, no signal comes and your stored result is current again once the group reads available, with the same values. An update asked for from inside a Statusworks callback runs before Statusworks returns from the change that started those callbacks, so code outside callbacks sees `PENDING` only while updates are held back; those run at the latest at the end of the next server tick, and in a loop that keeps changing the entity they keep being held back, so its groups stay pending while it lasts. Read the group again on a later tick.
- A respawn or an End exit creates a new player object: the old one gets an invalidation for each group that was available and stays unavailable, and the new one becomes available after its own pair.
- If `settled` throws, the error is logged once and the group still answers available with the committed values (`PENDING` while more updates wait), so a result you were storing there may be missing.
- `appliesTo` is also asked when the group is read, so keep it cheap and do not read the group from it; when it fails, that read answers failed.

## Client view

On the client, `api.client.StatusworksClient.view()` gives a read-only `StatusworksClientView` (it is `null` on a dedicated server and before the client has started): `ownEntries()` (the player's own entries, including ones the player hid on the HUD) and `nearbyEntries()` (entries with particles that this player is sent, by entity id; author-hidden definitions never appear). Each `ClientEntry` has the definition id, kind, name, count, amount, capacity, remaining time, the other participant of a mark, whether it is active, and `harmful()`: whether the entry is a debuff by its definition (a mark in your own entries uses your side's value; a mark on another entity uses the target's value). Use it to draw your own visuals; it changes nothing.

## What you can build

- Buffs and debuffs that stack, decay one stack at a time, or keep a timer per application.
- Resources such as mana, rage, or shields that fill or drain by themselves and scale attributes with their fill.
- Links between two entities: a hunter and its quarry, a healer and a patient, a tether that breaks with distance.
- Per-attacker state such as combo counters or bleed stacks from each player.
- Shared cooldowns between items or abilities.
- Stat systems driven by any of these through your own contribution type.
