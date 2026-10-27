# Statusworks examples

These files show Statusworks in use. Statusworks never loads them by itself. Nothing here is active until you copy it.

## Scripts (CraftTweaker)

Copy a `.zs` file into your pack's `scripts` folder. Then restart the game.

| File | What it shows |
| --- | --- |
| `stacking_counter.zs` | A status with up to 5 stacks that fall off one by one; +1 attack damage per stack. |
| `fractional_shield.zs` | A pool with fractional amounts, its capacity set from max health, that absorbs damage. |
| `combo_per_attacker.zs` | A status kept apart for each attacker; the fifth hit in a row deals double damage. |
| `target_tracking.zs` | A mark from a player to the last mob it hit, with particles only that player sees. |
| `shared_cooldown.zs` | One cooldown shared by two items. |

- Scripts use the classes `mods.statusworks.Statuses`, `Pools`, `Marks`, `Cooldowns`, `Contributions`, and `Presentation`. The entity always comes first.
- Modes and rules are text, such as `"gradual"`, `"reset"`, or `"per_source"`. An unknown text makes that one definition invalid. The game log names the definition and its script file.
- Use the default loader. Scripts under `#loader preinit` are not supported.
- Callbacks are functions, such as `.onApplied(function(ctx) { ... })`. They run only on the server.

## Java

`JavaExample.java` is the stacking counter for a mod written in Java. Copy the class into your mod, and change the package, mod id, and definition id.
