# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.1.0] - 2026-11-06

Client and server must run the same Statusworks version: update both to 1.1.0.

### Added

- A harmful option for definitions (`harmful()`, and `markHarmful(onSource, onTarget)` for marks) in the Java API and CraftTweaker. Without it, an entry is beneficial, except that a mark is harmful on the target's side.
- The HUD draws buffs and debuffs as two groups, each with its own maximum and `+N`; debuffs come after a gap, below the buffs or above them at a bottom anchor.
- A red border around harmful entries, and vanilla's potion frame behind potion effects; potion effects are harmful when vanilla marks them as bad.
- The inventory tooltip of a harmful entry shows "Harmful".
- Client settings `separateDebuffs`, `debuffBorder`, and `potionFrame`, all on by default.
- `ClientEntry.harmful()` in the client view for addons.
- The guides now document that other mods' attributes, such as Potion Core's, work in attribute contributions.

## [1.0.0] - 2026-10-27

### Added

- Statuses with stacks: limits, four expiration modes, refresh rules, and stacks kept apart for each source.
- Pools: fractional amounts with a capacity, progression toward full, empty, or a target, lifetime, and cutoff.
- Marks between a source and a target entity, with target limits, overflow, maximum distance, and inactive time.
- Cooldowns, with start, extend, reduce, restart, and start-if-ready.
- Contributions: attribute modifiers and addon-registered contribution types that follow the state that owns them.
- Callbacks on application, change, pool values, mark activity, end, and intervals; failing callbacks are caught and counted.
- Saving of all state with players and mobs, through logout, unloading, dimension travel, and death rules.
- A Java API for registering definitions and changing and querying state, and a read-only client view for addon visuals.
- CraftTweaker scripting through `mods.statusworks`, with five example scripts and a Java example in `examples/`, and guides for scripts and the Java API in `docs/`.
- A HUD with icons, stack counts, times, and pool bars; nine anchor positions, entry and row directions, a named list, hidden entries, and an inventory view that explains each entry.
- The player's own potion effects on the HUD, with vanilla's potion icons and inventory list hidden (client settings, on by default).
- Entity particles per definition, shown to an audience the author chooses.
- `/statusworks` commands to inspect and change state, list definitions, toggle a diagnostic log, and show server time and callback errors.
- Server settings for strict startup and the diagnostic log.
