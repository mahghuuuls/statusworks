# Statusworks

Statusworks is a Minecraft 1.12.2 Forge library that tracks custom state on players and other living entities: statuses with stacks, pools, marks, and cooldowns. Mods define that state through a Java API and modpack authors through CraftTweaker scripts. It adds no effects of its own.

- Player-facing description: [`MOD-PAGE.md`](MOD-PAGE.md)
- Release history: [`CHANGELOG.md`](CHANGELOG.md)
- Guides: [Writing Statusworks scripts](docs/crafttweaker.md) for modpack creators, [Using Statusworks from a Java mod](docs/java-api.md) for mod authors
- Example scripts and a Java example: [`examples/`](examples/)
- License: MIT ([`LICENSE`](LICENSE)); build scripts derive from CleanroomMC ForgeDevEnv ([`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md))

## Identity and install sides

- Mod id `statusworks`, version `1.2.0`, root package `com.mahghuuuls.statusworks`.
- Required on the server and on every client. The server owns all state; clients only draw the HUD and particles they are sent.
- Optional dependency: CraftTweaker, declared as `after:crafttweaker@[4.1.20,)`. Without it, everything except scripts works.
- Built and tested for Forge 14.23.5 and Cleanroom.

## Concepts

- **Definition:** a registered kind of tracked state, by id (by convention `modid:name`). Definitions are registered during mod loading and validated when loading completes; an invalid definition is disabled and named in the log, or stops the game in strict mode.
- **Status:** stacks up to a limit. Expiration `shared`, `gradual`, `independent`, or `none`; refresh `reset`, `extend`, or `preserve`; ownership `shared` or `per_source`.
- **Pool:** a fractional amount up to a capacity, starting `empty`, `full`, or at an amount. Progression `none`, `fixed`, `percent_of_capacity`, `percent_of_current` (each `fill` or `drain`), or `to_target`; optional lifetime and cutoff.
- **Mark:** a link from a source entity to a target entity, with a target limit and overflow (`replace_oldest` or `reject`), an optional maximum distance and inactive time.
- **Cooldown:** a timer per entity with start, extend, reduce, restart, and start-if-ready operations.
- **Contribution:** attribute modifiers (`add`, `multiply_base`, `multiply_total`) or registered custom contribution types, kept in step with the state that owns them. Value forms: `fixed` for every kind, `per_stack` for statuses, `per_amount` and `per_fill` for pools; marks take `fixed` and contribute on the source or the target side. Cooldowns carry no contributions. Attributes from other mods, such as Potion Core's, work by name when a player or a zombie has them at server start.
- **Callbacks:** run on the server when state is applied, changes, reaches a value (pool full, empty, or at its target), becomes inactive or active (marks), ends, or at an interval. A callback that throws is caught, logged once, and counted.
- **Death rule:** `clear` or `keep` for statuses and pools (default `clear`) and cooldowns (default `keep`); a mark has a keep flag for each side, and a mob's death always ends it. State is saved with the entity and survives logout, unloading, and dimension travel.
- **Presentation:** name, description, icon (item or texture), HUD visibility, beneficial (the default) or harmful (a debuff on the HUD; for marks one value per side, default beneficial on the source and harmful on the target), and particles (type, color, rate, and an audience: `nearby` for statuses and pools; `nearby`, `source`, `target`, or `both` for marks; none for cooldowns).

## Java API

`com.mahghuuuls.statusworks.api.StatusworksApi` is the entry point.

- `registerStatus`, `registerPool`, `registerMark`, `registerCooldown`, `registerContributionType` with builders from `api.definition` (`StatusDefinition.builder(id)` and so on). Register before loading completes.
- `statuses()`, `pools()`, `marks()`, `cooldowns()`, `contributions()` return the services that change and query state. Most operations return a result that says whether they succeeded and why not; a few return a boolean or a count.
- A contribution type that implements `ContributionGroupType` also gets group signals: an invalidation before its values on an entity change and a settled signal with the complete values after; `contributions().group(entity, typeId)` says whether those values are current.
- `api.client.StatusworksClient` gives client-side code a read-only view of the entries the client was sent.

[`examples/JavaExample.java`](examples/JavaExample.java) registers a stacking status with a callback. The [Java guide](docs/java-api.md) covers registration, the services, callbacks, custom contribution types with group signals, and the client view.

## CraftTweaker

Scripts use `mods.statusworks.Statuses`, `Pools`, `Marks`, `Cooldowns`, `Contributions`, and `Presentation`. Builders take the same options as the Java API, with modes as lowercase text; an unknown text makes that one definition invalid and the log names its script file. Callbacks are functions. Use the default loader: scripts under `#loader preinit` are not supported. A syntax error in one script file stops CraftTweaker from running the files loaded after it. The [scripting guide](docs/crafttweaker.md) lists every class, method, and option, with examples; see also [`examples/README.md`](examples/README.md).

## Commands

`/statusworks` needs permission level 2.

- `inspect <entity>`: all tracked state on one entity.
- `status`, `pool`, `mark`, `cooldown`: apply, change, clear, and query state.
- `definitions`: every definition with its settings and where it was registered.
- `debug on|off [definition] [entity]`: the diagnostic log, one server-log line per change of tracked state, optionally for one definition or entity.
- `timing`: the mod's server time per tick over the last 200 ticks.
- `errors`: callback failure counts since start, and which callbacks are disabled.

## Configuration

Server, `config/statusworks.cfg`:

- `strictMode` (default `false`): stop at startup when any definition is invalid.
- `diagnosticLog` (default `false`) and `diagnosticLogLinesPerSecond` (default `20`).

Client, `config/statusworks-client.cfg`, section `hud`, read at start and at each server join:

- `enabled`, `anchor` (`top_left` to `bottom_right`, nine positions, default `top_right`), `offsetX`, `offsetY`, `entryDirection` and `rowDirection` (default `auto`), `scale`, `maxEntries`, `iconsPerRow`, `textScale`, `shortTime`, `showNames`, `hiddenEntries`.
- `potionEffects` (default `true`): show the player's own potion effects on this HUD and hide vanilla's top-right icons.
- `hideInventoryEffects` (default `true`): while this HUD and `potionEffects` are on, also hide vanilla's effect list beside the inventories.
- `separateDebuffs` (default `true`): draw harmful entries as their own group after a gap, below the others (above them at a bottom anchor); the maximum, `+N`, and icons per row apply to each group.
- `debuffBorder` (default `true`): a red border around each harmful entry's icon.
- `potionFrame` (default `true`): vanilla's potion frame behind each potion effect.

Section `particles`: `enabled`.

## Building

Gradle wrapper 9.7.0, run with a current JDK (the project uses Java 25); the mod compiles to Java 8 bytecode through the toolchain. `./gradlew build` builds the mod and runs the unit tests. Development runs include CraftTweaker unless Gradle is started with `-PnoCraftTweaker`.
