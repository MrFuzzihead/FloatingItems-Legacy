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
| `./gradlew test` | 72 headless JUnit 4 tests, no skipped tests (JUnit 4.13.2 + Mockito 4.11.0, Java 8 launcher) |
| `./gradlew check` | the above plus Spotless, Checkstyle, the coverage floor and the gameTest compile |
| `./gradlew gameTestServer` | boots a real dedicated server, runs 11 in-game checks, then exits with the result. Also writes an in-game coverage report to `build/reports/jacoco/gameTest/html` |
| `./gradlew runServer -PgameTest` | boots the server with the gameTest mod on the classpath (the `gameTestServer` task also prepares the world and EULA) |

The game test is a non-shipped `@Mod` in `src/gameTest/java`; it is only on the classpath for `gameTestServer` (or
`-PgameTest`) and never reaches the published jar. It covers what cannot be tested headlessly: a real Forge server,
real `EntityItem` physics, negative coordinates, the blacklist rules, and the event-bus wiring.

The P0/P1/P2 batches and the P3 cleanup are fixed. The unit and in-game suites currently have no skipped or xfail tests. Remaining feature work is tracked in
[CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md).

### Configuration

`config/FloatingItems.conf`, generated on first launch. The `util` category contains:

| Option | Format |
|---|---|
| Blacklist of Items that should not Float | `{itemId}(metadata)` |
| Blacklist of All Items regardless of Metadata | item ID |
| Blacklist of OreDict Entries that should not Float | ore-dictionary name — all metadata of the entry |
| Default Server language | locale code, e.g. `en_us` |
| Disable FloatingItems Update Check | boolean - disables both the GitHub request and the message |

See [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md) for the full review, the bug catalogue and the improvement plan.

## Addons

FloatingItems has a Dev release, which is available on the [CurseForge](https://www.curseforge.com/minecraft/mc-mods/floatingitems) page.
To see it in use check out the official addons:

* [SurvivalPlus](https://curseforge.com/minecraft/mc-mods/survivalplus)
* [More Than A Pickaxe](https://curseforge.com/minecraft/mc-mods/more-than-a-pickaxe)
* [SurvivalPlus Lightsabers](https://curseforge.com/minecraft/mc-mods/survivalplus-lightsabers)
* [ServerStop](https://curseforge.com/minecraft/mc-mods/serverstop)

## Contributing

Bug reports and pull requests are welcome. Please read [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md) before
structural changes: it records the known limitations, the test baseline and the ordered refactor plan.
