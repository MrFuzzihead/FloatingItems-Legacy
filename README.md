# FloatingItems

[![](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)
[![](https://img.shields.io/badge/UniMixins-0.3.x-2ea44f)](https://github.com/LegacyModdingMC/UniMixins)

<p align="center"><img src="https://coolsimulations.net/wp-content/uploads/2021/11/floatingitems_logo_HD.png"></p>

**[FloatingItems](https://www.curseforge.com/minecraft/mc-mods/floatingitems/)** is a **Minecraft mod** that
backports items' ability to float in water to pre-1.13 versions of Minecraft. It works both as a server-side mod
as well as in single-player.

Originally written by coolsim; this fork keeps it building and running on 1.7.10 and adds a verification harness so
behaviour changes stay measurable.

## What it does

Items dropped in water are nudged upward and damped horizontally, so they settle near the surface instead of
sinking like vanilla 1.7.10 item entities. Lava is deliberately excluded; see `CODEBASE_ANALYSIS.md` for the
current modded-liquid behaviour.

Blacklists are available for individual item/metadata pairs, whole items, and ore-dictionary entries. The default
server language is configurable because the mod has to localise its update messages server-side.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.7.10 |
| Minecraft Forge | 10.13.4.1614 |
| [UniMixins](https://github.com/LegacyModdingMC/UniMixins) | 0.3.x (early mixin loader) |
| Java | 8+ |

## Building

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin:

```sh
./gradlew build              # compile, unit tests, format check, coverage gate
./gradlew test               # unit tests only
./gradlew jacocoTestReport   # HTML coverage -> build/reports/jacoco/test/html
./gradlew runClient          # dev client
./gradlew runServer          # dev server
```

### Tests

| Command | What it runs |
|---|---|
| `./gradlew test` | 122 headless JUnit 4 tests, no skipped tests (JUnit 4.13.2 + Mockito 4.11.0, Java 8 launcher) |
| `./gradlew check` | the above plus Spotless, Checkstyle, the coverage floor and the gameTest compile |
| `./gradlew gameTestServer` | boots a real dedicated server, runs 26 in-game checks, then exits with the result. Also writes an in-game coverage report to `build/reports/jacoco/gameTest/html` |
| `./gradlew runServer -PgameTest` | boots the server with the gameTest mod on the classpath (the `gameTestServer` task also prepares the world and EULA) |

The game test is a non-shipped `@Mod` in `src/gameTest/java`; it is only on the classpath for `gameTestServer` (or
`-PgameTest`) and never reaches the published jar. It covers what cannot be tested headlessly: a real Forge server,
real `EntityItem` physics, negative coordinates, the item filter rules, source vs downward-flowing water, partial
submersion, chunk borders, item NBT, merge/despawn, config reload, and the event-bus wiring.

The P0/P1/P2 batches and the P3 cleanup are fixed. The unit and in-game suites currently have no skipped or xfail tests. Remaining feature work is tracked in
[CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md).

### Configuration

`config/FloatingItems.conf`, generated on first launch. The `util` category contains:

| Option | Format |
|---|---|
| Item Filter Mode | `blacklist` (default) or `whitelist`; in whitelist mode the item/registry/ore lists are the only allowed entries |
| Blacklist of Items that should not Float | `{itemId}(metadata)` or `namespace:name[:metadata|:*]` (`*` = any metadata) |
| Blacklist of All Items regardless of Metadata | item ID |
| Blacklist of OreDict Entries that should not Float | ore-dictionary name — all metadata of the entry |
| Liquid Policy | `water_like` (default), `water`, or `material_water` |
| Floating Fluid Allowlist | Forge fluid names that always float, even if the policy would reject them |
| Floating Fluid Denylist | Forge fluid names that never float; wins over the allowlist |
| Floating Lift Acceleration | upward acceleration while floating; default `0.04` |
| Floating Horizontal Damping | horizontal motion multiplier; default `0.95` |
| Floating Maximum Upward Speed | upward speed cap; default `0.4` |
| Floating Surface Offset | extra height above the liquid surface where buoyancy still applies; default `0.0` |
| Default Server language | locale code, e.g. `en_us` |
| Disable FloatingItems Update Check | boolean - disables both the GitHub request and the message |

`water_like` is the default: vanilla water plus water-named Forge fluids whose density is 800–1200, temperature is
250–400 and viscosity is 500–1500. Oil-like and hot fluids are rejected. Use `water` for vanilla water only, `material_water` for the legacy
`Material.water` behaviour, or the allowlist for a specific modded fluid. The denylist always wins.

In `whitelist` item-filter mode, the three item/registry/ore lists become the only entries allowed to float. In
`blacklist` mode they remain exclusions. The list keys keep their historical names so existing configs continue to work.

The buoyancy values are validated and clamped. `Floating Surface Offset` extends the liquid-detection box downward, so
a positive value lets an item settle slightly above the surface while a negative value keeps it lower.

### Commands

| Command | Permission | Purpose |
|---|---|---|
| `/floatingitems status` | operator | Show the effective item filter, liquid policy and buoyancy settings |
| `/floatingitems reload` | operator | Reload `FloatingItems.conf` and re-resolve item entries against the registry |

See [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md) for the full review, the bug catalogue and the improvement plan.

## Contributing

Bug reports and pull requests are welcome. Please read [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md) before
structural changes: it records the known limitations, the test baseline and the ordered refactor plan.
