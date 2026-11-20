# FloatingItems-Legacy — Codebase Analysis, Improvement Plan & Test Baseline

Review date: 2026-02-20  
Scope: `src/main/java` (10 classes, ~1,500 LOC including the 1,024-line `FILang`), `src/main/resources`
(mixin configs, `mcmod.info`), Gradle/GTNH build, CI workflows.  
Baseline: Minecraft 1.7.10 / Forge 10.13.4.1614 / GTNH buildscript 2.0.34 / UniMixins 0.3.x.

This document records the review. The P0 batch (B-01, B-02, B-03, B-04, B-10, B-18 and B-19) has been fixed and is
covered by the tests described in §9. The text below describes the original defects; the P0 change log in §3.1
lists what was changed. Everything from P1 onward remains open unless stated otherwise.

The companion test harness is described in §9 and wired through `addon.gradle.kts`, `dependencies.gradle`,
`src/test/java`, `src/gameTest/java` and `.github/workflows/game-test.yml`. Known bugs that are asserted in tests
use the `B-xx` ids below. A failing xfail becomes an “XPASS” failure when the bug is fixed, which forces the test to
be updated instead of silently rotting.

---

## 1. At a glance

| Area | Finding |
|---|---|
| Critical correctness | The mixin still calls `Item.onEntityItemUpdate` **twice per tick** (vanilla already calls it — B-06, P1). The config-parser and null-item crash paths (B-01/B-02/B-03) are fixed: entries are validated individually and the mixin skips null entries. |
| Event wiring | **Fixed (B-10).** `FloatingItems` now registers on `FMLCommonHandler.bus()`, where both `PlayerLoggedInEvent` and `ConfigChangedEvent` are posted. The game test posts the config event and observes the reload. |
| Physics | The water test is implemented at `(int) posX`, so negative-coordinate blocks use the wrong block. The `getEyeHeight()`/liquid-height comparison is dimensionally meaningless (`EntityItem` inherits `getEyeHeight() == 0.0F`), so the metadata check is dead. The test uses a single block position rather than the entity's water bounding box, which allows surface bobbing/edge cases. |
| Config | **Fixed (B-01/B-02/B-03).** Entries are parsed independently, missing metadata defaults to 0, unknown IDs are retained until postInit so other mods can finish registering their items, and unresolved entries are removed by `resolveItems()`. A backend failure falls back to safe defaults. The `propOrder` list is still built but never applied (B-20/D-06). |
| Localisation | `FILang` compares keys with `==` and is a 1,024-line if-chain. Chinese tables use the fullwidth `％`, so placeholders are never substituted. `en_ud` is copied from ServerStop. Turkish locale lowercasing can miss every language code. |
| Dead code | `core/CoreMod` is not registered (the manifest points at `EarlyMixinsLoader`), `FIConfig.forceSave`, `FIConfigItem(ItemStack)`, `FloatingItems.instance`, two `FIConfigGUI` methods, the `propOrder` list, an empty late mixin config, and three ServerStop translation keys. |
| Performance | The mixin still scans every blacklist entry, normalises ore-dictionary names with a regex, and looks up `Item.getIdFromItem`/DataWatcher values on **every item entity every tick** (P-01/P-03/P-05). The ore list is cached once per entry now (P-02), and the stack is read once before/after the hook (P-04). |
| Packaging | **Fixed (B-19).** `@Mod(dependencies)` and `mcmod.info` now declare `unimixins`; `FIReferenceTest` and `FloatingItemsMetadataTest` guard both. |
| Testability | Most of the decision logic is still embedded in the `@Inject` method. The config parser, translation table, update-result builder and login guards are directly testable; extracting a pure `FloatingItemPolicy` would also make the blacklist/liquid rules unit-testable. |

Current verification baseline:

```text
./gradlew check                     PASS  (unit tests + Spotless + Checkstyle + JaCoCo floor + gameTest compile)
./gradlew gameTestServer            PASS  11/11 checks, 4 documented known bugs remain
Unit tests                          65 executed, 6 intentionally @Ignore'd (remaining P2/P3 bugs)
JaCoCo (unit run)                   line 574/716 = 80.2%, branch 421/536 = 78.5%
```

The 4 known bugs asserted by the game test are B-06, B-07, B-08 and B-09. The `@Ignore` unit tests cover B-05,
B-11, B-12, B-13, B-14 and B-16. B-10 is now a normal game-test check.

---

## 2. What the mod does

`FloatingItems` is a server-side (also single-player-compatible) mod with one real behaviour change:

1. `FloatingItems.preInit` loads `FloatingItems.conf`, runs the update checker and registers the event handler.
2. `EntityItemMixin` injects at the head of `EntityItem.onUpdate`, applies the blacklist rules, and if the item is
   allowed to float and is standing in a non-lava liquid, adds buoyancy and horizontal damping.
3. Vanilla `EntityItem.onUpdate` then applies gravity, drag, merging, lava fizz, despawn and pickup behaviour.
4. The mixin is registered as an early mixin through GTNHMixins/UniMixins; there is no late mixin.

The config surface is five properties in the `util` category:

| Property | Format | Notes |
|---|---|---|
| Items that should not Float | `{itemId}(metadata)` | Per-metadata item blacklist |
| All Items regardless of Metadata | `itemId` | Whole-item blacklist |
| OreDict Entries that should not Float | `oreDictName` | Exact item+metadata comparison today |
| Default Server language | locale code | Drives the hand-written `FILang` table |
| Disable Update Checker Message | boolean | Originally only hid the message; the HTTP requests still ran |

---

## 3. Bug catalogue

Severity: **P0** = crash/feature completely broken; **P1** = wrong behaviour users will notice; **P2** = edge case,
localisation or API contract; **P3** = polish.

| ID | Sev | File / line | Symptom | Verification |
|---|---|---|---|---|
| **B-01** | P0 | `FIConfig.java:58-63, 96-102` | **Fixed.** A malformed entry no longer aborts `syncConfig`; entries are parsed independently, invalid entries are logged/skipped, and a backend failure resets all fields to safe defaults. | `FIConfigTest.oneMalformedEntryDoesNotDiscardTheRestOfTheConfig`, `allInvalidEntriesLeaveEmptyBlacklistsButKeepOtherProperties`, `parserRejectsMalformedAndUnknownEntries` |
| **B-02** | P0 | `FIConfig.java:128-130`; `EntityItemMixin.java:43-46` | **Fixed.** Unknown ids are kept through preInit (so another mod can register the item later), then `resolveItems()` in postInit drops anything still unresolved. The mixin also skips null entries/null items defensively. | `FIConfigTest.unknownItemIdIsRejected...`, `resolveItemsRemovesOnlyUnresolvedEntries`, parser tests; game phase 0 null-item entry |
| **B-03** | P1 | `FIConfig.java:126-129` | **Fixed.** The metadata suffix is optional and defaults to 0; malformed suffixes are rejected without throwing. | `FIConfigTest.missingMetadataDefaultsToZero`, `parserAcceptsOptionalMetadataAndWhitespace` |
| **B-04** | P1 | `FIUpdateHandler.java:29-45, 109-128`; `FloatingItems.java:48-49` | **Fixed.** The check runs on a daemon worker instead of `preInit`, uses bounded connect/read timeouts, `try`-with-resources and UTF-8, and clears state before applying a result. | `FIUpdateHandlerTest` opt-outs, `processUpdateData` branches, local-HTTP `readFirstLine` success/empty/error |
| **B-05** | P2 | `FIConfig.java:164-198` | `equals` is overridden without `hashCode`, violating the Object contract. Two equal blacklist items hash differently, so a `HashSet`/`HashMap` of config items would misbehave. The long commented-out implementation is also dead code. | `FIConfigTest.equalItemsHaveEqualHashCodes` (`@Ignore`) |
| **B-06** | P1 | `EntityItemMixin.java:38-39` | The mixin calls `getEntityItem().getItem().onEntityItemUpdate(...)`; vanilla `EntityItem.onUpdate` calls the same hook at its own head (`EntityItem.java:91-100`). Every item's hook runs **twice per tick**. Items such as Botania/Thaumcraft-style water transformations can double-fire, and hooks with side effects or counters are wrong. | Game test `anEntityItemUpdateIsCalledOncePerTick` (xfail) |
| **B-07** | P1 | `EntityItemMixin.java:41-69` | The per-metadata item check sets `shouldFloat = true` when the item matches but the metadata differs. Both the registry blacklist and the ore-dictionary blacklist are guarded by `!shouldFloat`, so they are skipped. A `{280}(1)` entry therefore lets a stick with meta 0 float even though the stick ID is in the registry blacklist. | Game test `aRegistryBlacklistIsNotShadowed...` (xfail) |
| **B-08** | P1 | `EntityItemMixin.java:104, 110-113` | `worldObj.getBlock((int) posX, ...)` truncates toward zero; `getBlockMetadata` uses `MathHelper.floor_double`. At negative coordinates the two calls refer to different blocks. An item at `posX = -23.5` in water block `-24` checks block `-23` (often a wall), so it does not float. | Game test `anItemAtNegativeCoordinatesFloats` (xfail) |
| **B-09** | P2 | `EntityItemMixin.java:77-93` | The ore-dictionary blacklist compares `ItemStack.getItemDamage()` exactly. An entry registered with meta 0 (the common wildcard/representative stack) does not blacklist the same item with meta 1, even though the property is described as a blacklist of the *entry*. | Game test `anOreDictionaryBlacklistCoversEveryMetadata...` (xfail) |
| **B-10** | P1 | `FloatingItems.java:33, 36-50` | **Fixed.** The mod now registers on `FMLCommonHandler.instance().bus()`, so config reload and the login notification receive their events. | Game test `a ConfigChangedEvent on the FML bus re-syncs FIConfig (B-10)` (normal check) |
| **B-11** | P2 | `FILang.java:186-1020` (all `text ==` checks) | String keys are compared by identity. It happens to work for literals, but any key built at runtime (`new String`, concatenation, config-driven key) falls through and is returned untranslated. `getTranslations(null)` also returns null. | `FILangTest.constructedKeyIsTranslated` (`@Ignore`) |
| **B-12** | P3 | `FILang.java:57` | `de_alg` is not a Minecraft locale (the German locales are `de_de`, `de_at`, `de_ch`). It is an unnecessary alias. | `FILangTest.germanAlgeriaAliasIsNotNeeded` (`@Ignore`) |
| **B-13** | P2 | `FILang.java:978, 988, 1003, 1013` | Simplified/Traditional Chinese use the fullwidth `％` instead of `%`. `ChatComponentTranslation` recognises `%s`, so these messages print `％s` literally and never substitute the mod/version. | `FILangTest.chineseTablesUseAsciiPlaceholders` (`@Ignore`) |
| **B-14** | P3 | `FILang.java:526-544` | The upside-down English table is copied from ServerStop: display1/display3 say “ServerStop” and “1.7.x” rather than FloatingItems. The other locales were copied as well; see the dead keys below. | `FILangTest.upsideDownEnglishMentionsThisMod` (`@Ignore`) |
| **B-15** | P2 | `EntityItemMixin.java:72` | `oreDict.replaceAll("[^A-Za-z0-9 ]", "")` silently mutates valid ore-dictionary names that contain `_`, `-`, `:` or other characters. A config entry can therefore become a different name and stop matching. It also compiles the regex on every iteration of every tick. | Static review |
| **B-16** | P3 | `FILang.java:9` | `FIConfig.serverLang.toLowerCase()` uses the default locale. In a Turkish JVM, `"IT_IT"` lowercases to `"ıt_ıt"` and misses the Italian branch, falling back to English. Use `Locale.ROOT`. | `FILangTest.languageCodeLowercasingIsLocaleIndependent` (`@Ignore`) |
| **B-17** | P2 | `EntityItemMixin.java:105-114` | `getEyeHeight()` on `EntityItem` is the inherited `0.0F` (`Entity.java:1169`; EntityItem does not override it), so `eye = -0.11111111F`. `BlockLiquid.getLiquidHeightPercent` returns 1/9…8/9, so the comparison is always true for any non-lava liquid. The metadata/liquid-height check is dead and does not test the fluid surface. | Static review + game water test |
| **B-18** | P1 | `FIUpdateHandler.java:109-128`; `FloatingItems.java:49` | **Fixed.** The login handler returns early when the checker is disabled/current and adds each message only when it is non-null. | `FloatingItemsEventTest` no-message, one-message, both-message, disabled and current-version cases |
| **B-19** | P1 | `FIReference.java:9`; `mcmod.info`; built manifest | **Fixed.** `@Mod(dependencies)` now contains `required-after:unimixins` (and no trailing separator), and `mcmod.info` lists `unimixins` in `requiredMods`. | `FIReferenceTest.dependenciesDeclareForgeAndUniMixins`, `FloatingItemsMetadataTest.mcmodInfoDeclaresUniMixins` |
| **B-20** | P2 | `FIConfig.java:43, 65-90` | `propOrder` is populated but `config.setCategoryPropertyOrder(...)` is never called. The GUI therefore uses the Configuration's default ordering; the unit test confirms the current order is alphabetical rather than the intended logical order. | `FIConfigTest.allPropertiesAreExposedToTheConfigGui` (order deliberately not asserted) |
| **B-21** | P2 | `EntityItemMixin.java:104-118` | The float test samples only the block at the entity's feet position. `Entity.handleWaterMovement` uses the bounding box expanded downward by 0.4 (`Entity.java:1082-1084`). An item at the water surface, on a water boundary, or partially in water can oscillate between “float” and “fall” because the two systems disagree. | Static review; future game test with a partially submerged item |
| **B-22** | P3 | `EntityItemMixin.java:38-39, 45-48, 61-63, 77-85` | **Partly fixed in the P0 pass.** The stack is null-checked and cached before the hook, then re-read once after the hook; the remaining blacklist checks use that post-hook stack. | Covered indirectly by B-02/B-06 |
| **B-23** | P2 | `FIConfig.java:23-31`; `EntityItemMixin.java:41-100` | The blacklist arrays are public mutable static fields, read on the server tick while `syncConfig`/the config GUI can replace them on another thread. Array references are atomic, but the set of fields can be observed at different generations, and a caller can mutate the arrays in place. Use an immutable snapshot object and a volatile reference/generation counter. | Static review |
| **B-24** | P3 | `FIConfig.java:197` | `equals` compares the `Item` reference in addition to registry id and metadata. Registry ids are unique, but after a registry remap/reload two `FIConfigItem`s that print the same `toString()` can compare unequal. Use the registry name/id as the identity and add `hashCode`. | `FIConfigTest.equalsIsReflexiveSymmetricAndTypeSafe` |
| **B-25** | P3 | `FloatingItems.java:43-50` | The update message is sent on every login, every time, and the server language is global. There is no “show once per player”, no per-player locale, and no config to turn off the check without also disabling the message. | Static review |
| **B-26** | P3 | `FIUpdateHandler.java:80` | `latestVersion.equals(FIReference.VERSION)` is an exact string comparison. A remote value with whitespace, a `v` prefix, or a build-suffix mismatch produces a false “old version” message. There is no semantic-version comparison and no `trim()`. | Static review |
| **B-27** | P3 | `FIUpdateHandler.java:31-33, 40-42` | `Scanner` is not closed on the exception path and uses the platform default charset. Use `try`-with-resources and an explicit UTF-8 charset. | Static review |
| **B-28** | P3 | `FIConfig.java:105-108` | `getConfigElements()` dereferences `config` without a null check. It is only called from the GUI after `preInit`, but a client-side test or a reload race can NPE. | Static review |
| **B-29** | P3 | `FIConfig.java:56, 71, 79, 84` | `prop.comment = ...` is assigned after `Configuration.get` returns an existing property; the assignment does not mark the configuration dirty. On an existing file whose comments are missing, the comments are not written until some other value changes. | Static review of `Configuration.get`/`hasChanged` |

### 3.1 P0 change log (applied)

| ID | Change |
|---|---|
| B-01/B-02/B-03 | `FIConfigItem.parse(String)` validates the shape and optional metadata (default 0); item resolution is lazy so a mod that registers later in preInit is not lost. `FIConfig.syncConfig` parses each entry independently, logs/skips malformed entries, always assigns every blacklist array, and resets to safe defaults if the backend fails. `FIConfig.resolveItems()` runs in postInit (and after config reloads) to drop ids that still do not resolve. The mixin skips `null` entries and `null` items defensively. |
| B-04/B-18 | `FIUpdateHandler` now runs the check on a daemon worker, uses `HttpURLConnection` with 5 s connect/read timeouts and try-with-resources, reads UTF-8, exposes `processUpdateData` for branch tests, and clears state before applying a result. `FloatingItems.onPlayerLogin` guards the disabled/current cases and adds each non-null message independently. |
| B-10 | `FloatingItems.preInit` registers on `FMLCommonHandler.instance().bus()` instead of `MinecraftForge.EVENT_BUS`. |
| B-19 | `FIReference.DEPENDENCIES` now declares `required-after:unimixins` (no trailing separator), and `mcmod.info` lists `unimixins` in `requiredMods`. |

The remaining game-test xfails are B-06/B-07/B-08/B-09. The remaining `@Ignore` tests are B-05/B-11/B-12/B-13/B-14/B-16.

---

## 4. Dead code

| ID | Location | Finding |
|---|---|---|
| **D-01** | `core/CoreMod.java` | Not referenced by the build. `gradle.properties` sets `coreModClass = mixins.EarlyMixinsLoader`, and the built manifest contains `FMLCorePlugin: ...EarlyMixinsLoader`, not `CoreMod`. The class also calls `MixinBootstrap.init()` in its constructor, which would be a second initialisation if it were ever used. Delete it or make it the single coremod entry and remove `EarlyMixinsLoader`. |
| **D-02** | `FIConfig.java:38-40` | `forceSave()` has no callers. |
| **D-03** | `FIConfig.java:117-122` | `FIConfigItem(ItemStack)` has no callers. The string constructor is the only production path; tests use the stack constructor only to pin the identity contract. Either remove it or expose it as the public API and use it. |
| **D-04** | `FloatingItems.java:25-26` | `instance` is injected by FML but never read. Keep only if an addon/API depends on it; otherwise remove the annotation field. |
| **D-05** | `FIConfigGUI.java:46-52` | `createConfigGui` and `hasConfigGui` are not part of 1.7.10 `IModGuiFactory` (it only has `initialize`, `mainConfigGuiClass`, `runtimeGuiCategories`, `getHandlerFor`). They are unreachable API surface. |
| **D-06** | `FIConfig.java:43, 65, 73, 81, 86, 90` | `propOrder` is built and never used. Call `config.setCategoryPropertyOrder(FIReference.CONFIG_CATEGORY_UTIL, propOrder)` or delete the list. |
| **D-07** | `FIConfig.java:63` | **Removed in the P0 pass.** The always-true `blacklist != null` guard is gone; the array is assigned unconditionally. |
| **D-08** | `src/main/resources/mixins.floatingitems.json` | Empty late-mixin config. It is still listed in the manifest as `MixinConfigs: mixins.floatingitems.json` by the buildscript, so it loads an empty config on every launch. Either remove the late config/manifest entry or move the mixin there and drop the early loader. |
| **D-09** | `FILang.java:436-439, 506-514` | `floatingitems.one_second` (Greek) and `floatingitems.commands.cancel.usage/display1` (Pirate) are copied from ServerStop and never used by this mod. Delete them with the rest of the ServerStop text. |
| **D-10** | `FIConfig.java:177-195` | The large commented-out `equals` implementation. Remove it; the one-line expression is the live code. |
| **D-11** | `FloatingItems.java:30` | `System.out.println("Pre Init")` debug leftover. Use the mod logger (or delete). |
| **D-12** | `EarlyMixinsLoader.java:16-18` | `getASMTransformerClass()` returns `null`. FML tolerates it (`CoreModManager` null-checks), but the `IFMLLoadingPlugin` contract returns `String[]`; return an empty array for clarity. |
| **D-13** | `FIReference.java:9` | **Fixed in the P0 pass.** The dependency string no longer ends with a separator. |
| **D-14** | `FIConfigGUI.java:36-44` | `runtimeGuiCategories`/`getHandlerFor` returning null is correct for “no runtime options”, but the empty `initialize` body and the two dead methods make the class look unfinished. Add a comment that null means “no runtime categories”. |
| **D-15** | `FIUpdateHandler.java:49-78, 80-130` | The display3 and display1 branches duplicate the same chat-component construction (color, hover, click). Extract one helper, which also makes the null-message guard easy to test. |

---

## 5. Performance

| ID | Location | Finding / fix |
|---|---|---|
| **P-01** | `EntityItemMixin.java:41-100` | The entire blacklist decision runs per item entity per tick, including array scans, `Item.getIdFromItem`, ore-dictionary lookups and a regex. Cache the decision per `(Item, metadata)` (and per ore-dictionary membership) and invalidate on config reload. A `Long2ByteOpenHashMap` or a small immutable `Set<StackKey>` is enough; item IDs fit in an int, metadata in a short. |
| **P-02** | `EntityItemMixin.java:74-77` | **Fixed in the P0 pass.** The list is stored in a local. The deeper `getOreIDs`/cache optimization remains future work. |
| **P-03** | `EntityItemMixin.java:72` | `replaceAll` compiles a `Pattern` on every entry of every tick. Normalise the configured names once in `syncConfig`, or simply `trim()` and reject empty names; do not silently strip characters. |
| **P-04** | `EntityItemMixin.java:38-39, 45-48, 61-63, 77-85` | **Partly fixed in the P0 pass.** The stack is null-checked and cached before the hook, then re-read once after the hook. Further blacklist decisions still use the post-hook stack. |
| **P-05** | `EntityItemMixin.java:61-63` | `Item.getIdFromItem(...)` does a registry map lookup per tick. The item pointer is stable; cache the decision or compare the cached `FIConfigItem.item` directly (with a null check). |
| **P-06** | `EntityItemMixin.java:104, 110-113` | `getBlock` and `getBlockMetadata` are two world lookups at the same position. `getBlock` then `getBlockMetadata` is normal, but the position should be floored once and reused. More importantly, use `isInWater()`/`handleMaterialAcceleration` so the entity's bounding box is tested instead of one block. |
| **P-07** | `FIConfig.java:42-103` | `syncConfig` allocates arrays/lists on every reload; that is fine because reloads are rare. The expensive part is the per-tick consumption of the mutable arrays (P-01). Publish one immutable `BlacklistSnapshot` instead. |
| **P-08** | `FIUpdateHandler.java:29-45` | **Fixed in the P0 pass.** The check is asynchronous, has 5 s timeouts and is daemon-only; the two requests are still sequential inside the worker (acceptable for a background check). |
| **P-09** | `FILang.java:5-180` | `getTranslations` walks a 30-branch if-chain on every call. Calls are rare (update messages, GUI title), so this is minor, but a `Map<String, LanguageTable>` or a `switch` on `serverLang` is both faster and far shorter. |
| **P-10** | `FIConfig.java:96-102` | **Improved in the P0 pass.** Per-entry failures are logged and skipped; the outer backend failure logs the exception summary and resets defaults instead of dumping a stack trace. |

---

## 6. Refactors

| ID | Refactor | Why |
|---|---|---|
| **R-01** | Extract a pure `FloatingItemPolicy` (or `Blacklist`) from the mixin: `boolean shouldFloat(ItemStack stack, World world, int x, int y, int z)`. | The blacklist/liquid decision becomes unit-testable without a mixin-transformed `EntityItem`; the mixin becomes a thin adapter. This is the single highest-value refactor for testing. |
| **R-02** | Represent blacklist entries as registry names + optional metadata wildcard, and pre-compute sets at config sync. | Numeric IDs are unstable across mod packs; `{17}(1)` is unfriendly in 1.7.10 when `minecraft:log:1` exists. A wildcard (`*`/`-1`) is the obvious missing feature. |
| **R-03** | Split `FIConfig` into a config-holder, a parser/validator and a `BlacklistSnapshot`. Parse each entry independently and report/skip bad entries. | **Applied for P0:** parsing/validation is independent; the snapshot split remains future work. |
| **R-04** | Replace the 1,024-line `FILang` chain with a data table loaded from `assets/floatingitems/lang/*.lang` (or a `Map` constant). Keep the server-side selection mechanism. | Translators can add languages without Java; removes B-11/B-12/B-13/B-14/B-16 classes of bugs; code size drops by ~900 lines. |
| **R-05** | Wrap the update check in an async service with timeouts and a small result object; build chat components from a real translation key + siblings, not a full sentence used as a key. | Fixes the startup stall, the null message and the fragile `ChatComponentTranslation` usage. |
| **R-06** | Register the event handler on `FMLCommonHandler.instance().bus()` (or both buses) and add a test that posts the event. | **Applied in the P0 pass:** the handler is on the FML bus and the game test posts the event. |
| **R-07** | Use a mod-specific `Logger` (`LogManager.getLogger(FIReference.MOD_ID)`) instead of `System.out`, `printStackTrace` and `FMLRelaunchLog` for normal messages. | Actionable logs with the mod name. |
| **R-08** | Use `isInWater()`/`handleMaterialAcceleration` and a floored block position; optionally query the block at the entity's bounding-box centre and below. | Fixes B-08/B-17/B-21 and matches vanilla's own water detection. |
| **R-09** | Move the inject from `HEAD` to after vanilla's `onEntityItemUpdate` check (or use a `@Redirect`/`@ModifyVariable` on the hook call) so the hook is called exactly once. | Fixes B-06 without losing modded items that intentionally cancel the default update. |
| **R-10** | Remove `CoreMod` (D-01), the empty late config (D-08) and the other D-xx items. | Smaller jar, less startup work, no confusing two-coremod setup. |
| **R-11** | Add `@SideOnly(Side.CLIENT)` to `FIConfigGUI`/inner class and move GUI-only code to a client package if the package rename is acceptable. | Prevents accidental server-side class loading and makes the client boundary explicit. |
| **R-12** | Add a public API (`FloatingItemsAPI`/`IFloatingItem`) if addons are expected to customise floating. Keep the current static config accessors deprecated. | The README advertises addons; a stable API avoids addons reaching into `FIConfig` arrays. |
| **R-13** | Rename the root package from `net.coolsimulations.FloatingItems` to `net.coolsimulations.floatingitems` (or add a compatibility facade). | Java package convention; this is a breaking change for addons, so schedule it with R-12. |
| **R-14** | Add `hashCode`, use `Objects.equals`, and make `FIConfigItem` immutable. | Fixes B-05/B-24 and makes the class safe to cache. |
| **R-15** | Replace `FIConfigGUI`'s dead `createConfigGui`/`hasConfigGui` with the 1.7.10 interface only. | D-05. |

---

## 7. Features / improvements

| ID | Feature | Notes |
|---|---|---|
| **F-01** | Config entries by registry name (`minecraft:log:1`), with `*`/`-1` metadata wildcard, and a warning for unknown entries. | Complements R-02. Preserve the numeric format as a deprecated fallback. |
| **F-02** | Whitelist mode (`only these items float`) in addition to the blacklist. | Useful for servers that want floating only for a few items. |
| **F-03** | Per-dimension / per-biome / per-mod-id rules. | Nether/End water is rare but modded dimensions often have custom fluids. |
| **F-04** | Explicit liquid policy: water only vs any `Material.water` vs a configurable list; do not use `isLiquid() && != lava` as an implicit “all modded liquids” switch. | Current code floats in oil/custom hot liquids. Make it deliberate. |
| **F-05** | Tunable buoyancy: lift acceleration, horizontal damping, maximum upward speed, surface offset, and whether to apply only in source blocks. | Current values (`+0.04`, `0.95`, `+0.0005`) are magic numbers. |
| **F-06** | Match modern water currents: items should be pushed by flowing water while floating, not just damped. | Test with a flowing-water channel; compare against 1.13+ behaviour. |
| **F-07** | Surface bobbing: a tiny idle oscillation or a stable resting height, instead of the current slow +0.0005 drift. | Cosmetic/behavioural parity with modern Minecraft. |
| **F-08** | Public events: `ItemFloatCheckEvent` (cancelable) and `ItemFloatTickEvent` (post), plus `IFloatingItem` for item-specific opt-in/out. | Addon-friendly; lets other mods override the blacklist without touching `FIConfig`. |
| **F-09** | Per-player localisation for the update message; ship `.lang` resources and use the client locale where possible. | The server-side `FILang` table is a workaround; the client can translate real keys. |
| **F-10** | Update checker: opt-in, async, cached, with a connect/read timeout, semantic version comparison, and a “show once per player” flag. | **Partly applied in the P0 pass:** async, timeouts and null guards are done; caching, semantic comparison and show-once remain (B-25/B-26). |
| **F-11** | Config GUI categories/lang keys, logical property order (`setCategoryPropertyOrder`), and a “reload” button/command. | Fixes D-06/B-20; makes the config usable on a live server. |
| **F-12** | `/floatingitems status` and `/floatingitems reload` commands. | Operators can inspect the effective blacklist and reload without restarting. |
| **F-13** | Debug counters (items evaluated, items floated, cache hits) behind a config flag. | Makes P-01 measurable in production. |
| **F-14** | More game tests: partially submerged items, source vs flowing water, chunk borders, unload/reload, item NBT, modded liquid, item merge/despawn, config reload. | The current 11 checks cover the main physics and blacklist paths; these are the next highest-value cases. |
| **F-15** | Add a smoke test for a dedicated server boot without the game-test mod. | The current `gameTestServer` exits after assertions; a separate `-PsmokeTest` boot is useful for packaging/CI. |
| **F-16** | Add Modrinth/CurseForge relationships for UniMixins and document the dependency in `mcmod.info`/README. | **Partly applied in the P0 pass:** `@Mod(dependencies)` and `mcmod.info` are fixed; publishing relations remain. |
| **F-17** | Add a `data/`-style regression table for modern 1.13+ physics and a compatibility note for each supported Forge/MCP version. | Prevents future “backport” drifts. |

---

## 8. Packaging, build and compatibility

1. **UniMixins is declared now** (B-19 fixed). `FIReference.DEPENDENCIES` contains `required-after:unimixins`, and
   `mcmod.info` has `"requiredMods": ["unimixins"]`. The built jar still has `TweakClass:
   org.spongepowered.asm.launch.MixinTweaker`, `MixinConfigs: mixins.floatingitems.json` and `FMLCorePlugin:
   net.coolsimulations.FloatingItems.mixins.EarlyMixinsLoader`.
2. **Two coremods, one unused** (D-01). `gradle.properties` points at `EarlyMixinsLoader`; `core/CoreMod` is dead.
   The empty late mixin config (D-08) is also still in the manifest.
3. **The dependency string is clean** (D-13 fixed). It no longer ends with a separator.
4. **The accepted Minecraft range** is `[1.7, 1.7.10]`. FML compares the target version as a version; `1.7.10` is
   inside the range, but `[1.7.10]` would express the intent more clearly. The mod uses only 1.7.10 APIs.
5. **The `mcmod.info` description** says “A small server-side mod…”. That is accurate, but the README advertises a
   “Dev release” and addons; the public API is only `FIConfig`/`FILang`, which are not documented. If addons are a
   supported use case, define and document the API (R-12) before changing the package (R-13).
6. **The build has no test dependencies until this pass.** `dependencies.gradle` now adds JUnit 4.13.2,
   Mockito 4.11.0 and mockito-inline; `addon.gradle.kts` adds JaCoCo and the game-test source set. The GTNH reusable
   workflow already runs `check`; the new `game-test.yml` runs the dedicated server separately.
7. **Configuration cache**: `org.gradle.configuration-cache = true` is enabled. The new build script was written
   with task inputs (`@Classpath`, `@Input`) and no configuration-time resolution for the JaCoCo agent; `check`
   and `gameTestServer` were run successfully with the cache enabled.

---

## 9. Testing infrastructure added in this pass

### 9.1 Build wiring

| File | Purpose |
|---|---|
| `addon.gradle.kts` | Applies JaCoCo, configures `Test` on a Java 8 toolchain, extends the test classpaths with `compileOnly` (Minecraft/Forge), creates `build/reports/jacoco/test`, enforces a line/branch floor, creates the `gameTest` source set, and registers `prepareGameTestServer`/`gameTestServer`/`jacocoGameTestReport`. |
| `dependencies.gradle` | JUnit 4.13.2, Mockito 4.11.0, mockito-inline. Mockito 4 is the newest line that runs on Java 8; mockito-inline allows final Minecraft classes to be mocked. |
| `src/test/java/...` | 8 test classes / 65 tests (6 `@Ignore` remaining-bug markers). |
| `src/gameTest/java/...` | A non-shipped `@Mod` (`floatingitemsgametest`) plus a 5-phase assertion suite. |
| `.github/workflows/game-test.yml` | Installs Java 8 + 25, runs `./gradlew test gameTestServer`, checks the `FLOATINGITEMS_GAMETEST_OK` marker, uploads the server log and coverage. |
| `README.md` | Testing commands and the xfail policy. |

### 9.2 Unit tests

| Class | What it covers |
|---|---|
| `FILangTest` | Every supported locale × every update/config key; placeholder preservation; fallback for null/unknown language; case-insensitivity; unknown key passthrough; dead ServerStop keys; five `@Ignore` known bugs (string identity, `de_alg`, Chinese `％`, `en_ud`, Turkish locale). |
| `FIConfigTest` | Defaults, every property name, item/registry/ore-dict parsing, whitespace, optional metadata, malformed/unknown entries, deferred `resolveItems()` removal, all-invalid/backend-failure fallbacks, `syncConfig(false)`, `forceSave`, `FIConfigItem(ItemStack)`, `toString`/`getStack`, equality; one `@Ignore` remaining bug (B-05). |
| `FIReferenceTest` | Mod id/name/version injection, accepted MC range, Forge + UniMixins dependencies, config file/category names. |
| `FloatingItemsMetadataTest` | Reads the `@Mod` annotation and the shipped `mcmod.info` back, checks the GUI factory class name and the UniMixins requirement. |
| `MixinsTest` | One early mixin named `EntityItemMixin`, phase EARLY, early config path. |
| `EntityItemMixinTest` | The pure `setUnderWaterMovement` arithmetic (damping, threshold, negative motion) via reflection. |
| `FIUpdateHandlerTest` | System-property/config opt-outs, current/older/ended/null version branches, and `readFirstLine` success/empty/error against a local `HttpServer`. |
| `FloatingItemsEventTest` | Login with no/one/both update messages, disabled checker and current version; verifies the null-message guard with a mocked `EntityPlayerMP`. |
| `MinecraftTestBootstrap` | Injects a fake `minecraft:stick` into the real `RegistryNamespaced` maps without calling FML's `GameData.addPrefix`, which needs a `LaunchClassLoader`. Also initialises `FMLInjectionData.minecraftHome` so `Configuration` can be constructed headlessly. |

Run: `./gradlew test` (Java 8 toolchain). Report: `build/reports/tests/test/index.html`.

### 9.3 Game tests

`GameTestMod` is a separate `@Mod` with `dependencies = "required-after:floatingitems"`, discovered only when the
`gameTest` output is on the run classpath. It registers a `ServerTickEvent` handler and exits the JVM with the
suite's status. `FloatingItemsGameTests` builds a small arena at y=100 (water pool, lava pool, one-block
negative-coordinate column, two air columns), obtains a Forge chunk ticket so the no-player dedicated server keeps
the chunks loaded, and runs five phases:

| Phase | Assertions |
|---|---|
| 0 | item rises in still water; item falls in air; item in lava does not get water buoyancy; negative-coordinate item floats (xfail B-08); `onEntityItemUpdate` is called once per tick (xfail B-06); null-item blacklist entries are skipped |
| 1 | per-metadata blacklist suppresses the lift |
| 2 | registry blacklist suppresses the lift |
| 3 | registry blacklist is not shadowed by a per-metadata entry with a different meta (xfail B-07) |
| 4 | ore-dictionary blacklist covers all metadata of the entry (xfail B-09) |

Plus a synchronous config-bus check at start: posting `ConfigChangedEvent.OnConfigChangedEvent` to the FML bus
reloads `FIConfig` (B-10, now a normal check).

Run: `./gradlew gameTestServer`. The task writes `run/server/eula.txt` and a deterministic `server.properties`
(`gameTestWorld`, flat, offline, no watchdog), deletes the old test world, and exits non-zero on failure.
`prepareGameTestServer` is also safe to run manually.

### 9.4 Coverage

- Unit-test report: `build/reports/jacoco/test/html/index.html` and `.xml`.
- In-game report: `build/reports/jacoco/gameTest/html`, generated after `gameTestServer` via
  `jacocoGameTestReport`. The JaCoCo agent is run with `classdumpdir=build/jacoco/gameTestClasses`, so the report
  reads exactly the bytes the agent instrumented. It is not merged into the unit report because a Forge 1.7.10
  dev launch rewrites classes through `LaunchClassLoader`; the latest in-game report is 111/264 lines (42.0%)
  and 26/102 branches (25.5%).
- Regression floor: 65% line / 70% branch. The measured unit baseline is 80.2% line / 78.5% branch. The floor is a
  guard, not a target; the mixin and dead `CoreMod` remain largely uncovered, and the update handler is covered by
  its opt-out/result/HTTP branches but not by a live network check.

### 9.5 What is still hard to test headlessly

- The real mixin-transformed `EntityItem.onUpdate` path (game tests cover it).
- The asynchronous update-check thread itself. Its HTTP helper and every result branch are tested; starting the real
  thread would hit the production URLs.
- `FIConfigGUI` (client-only; headless class loading is possible, but meaningless without a Minecraft instance).
- The in-game JaCoCo report is optional and is not part of `check`; it reports only the classes the agent
  instrumented during the short game-test run, so its percentages are not comparable to the unit report.

---

## 10. Prioritized roadmap

### P0 — release blockers (done in this revision)

The P0 batch below is implemented and covered by the tests in §9. The next work starts at P1.

1. **B-02/B-03/B-01** (done): validate every blacklist entry, skip malformed strings, defer item resolution to
   postInit and drop ids that still do not resolve, and never create a null item that crashes the server.
2. **B-19**: declare UniMixins in `@Mod(dependencies)`/`mcmod.info`.
3. **B-10**: register `FloatingItems` on the FML bus (or both buses) so config reload and the login notification work.
4. **B-04/B-18**: move the update check off the preInit thread, add timeouts, guard the null `updateVersionInfo`.

### P1 — user-visible wrong behaviour

5. **B-06**: stop calling `onEntityItemUpdate` twice; inject after vanilla's hook.
6. **B-07**: replace the `shouldFloat` flag with independent blacklist checks (or a pure policy).
7. **B-08**: floor the block coordinates once and reuse them.
8. **B-17/B-21**: use the entity's water bounding box (`isInWater`/`handleMaterialAcceleration`) rather than the
   feet block and the dead eye/liquid-height comparison.

### P2 — localisation, API, config polish

9. **B-09**: decide whether ore-dictionary entries are exact-metadata or whole-entry; implement and document it.
10. **B-11/B-13/B-15/B-16**: move `FILang` to a map/resource table; fix `==`, fullwidth `%`, ore-dict sanitisation
    and `Locale.ROOT`.
11. **B-20/D-06**: apply `setCategoryPropertyOrder`; persist comments.
12. **B-23**: publish an immutable blacklist snapshot; stop exposing mutable arrays.
13. **B-05/B-24**: immutable `FIConfigItem` with `equals`/`hashCode`.

### P3 — cleanup / features

14. D-01…D-15 dead-code removal; `CoreMod`, empty late config, unused methods/keys.
15. R-01/R-02/R-04/R-05/R-09 extraction and policy tests.
16. F-01…F-17 features, in particular registry-name config, wildcard metadata, per-player locale and the public API.

---

## 11. Verification map

| Bug | Test / evidence | Status |
|---|---|---|
| B-01 | `FIConfigTest.oneMalformedEntryDoesNotDiscardTheRestOfTheConfig`, `allInvalidEntriesLeaveEmptyBlacklistsButKeepOtherProperties` | **Fixed** |
| B-02 | `FIConfigTest.unknownItemIdIsRejected...`, `resolveItemsRemovesOnlyUnresolvedEntries`, parser tests; game phase 0 null-item entry | **Fixed** |
| B-03 | `FIConfigTest.missingMetadataDefaultsToZero`, `parserAcceptsOptionalMetadataAndWhitespace` | **Fixed** |
| B-04/B-18 | `FIUpdateHandlerTest` + `FloatingItemsEventTest` | **Fixed** |
| B-05 | `FIConfigTest.equalItemsHaveEqualHashCodes` | `@Ignore` |
| B-06 | Game phase 0, `onEntityItemUpdate` count | xfail (still present) |
| B-07 | Game phase 3 | xfail (still present) |
| B-08 | Game phase 0, negative pool | xfail (still present) |
| B-09 | Game phase 4, wool meta 0 vs 1 | xfail (still present) |
| B-10 | Game start, FML config event | **Fixed** |
| B-11 | `FILangTest.constructedKeyIsTranslated` | `@Ignore` |
| B-12 | `FILangTest.germanAlgeriaAliasIsNotNeeded` | `@Ignore` |
| B-13 | `FILangTest.chineseTablesUseAsciiPlaceholders` | `@Ignore` |
| B-14 | `FILangTest.upsideDownEnglishMentionsThisMod` | `@Ignore` |
| B-15 | Static review | Not covered |
| B-16 | `FILangTest.languageCodeLowercasingIsLocaleIndependent` | `@Ignore` |
| B-17/B-21 | Game phase 0 water test (only proves the positive path) | Not isolated |
| B-19 | `FIReferenceTest`, `FloatingItemsMetadataTest` | **Fixed** |
| B-20 | `FIConfigTest.allPropertiesAreExposedToTheConfigGui` (order intentionally not asserted) | Partial |
| B-23/B-24 | Static review / equality test | Partial |

When a fix lands, remove the corresponding `@Ignore` or xfail and let the normal assertion run. If a previously
xfail check becomes XPASS, the game-test task fails on purpose; that is the signal that the entry is stale.

---

## Appendix A — vanilla evidence used for the physics findings

All paths are under `build/rfg/minecraft-src/java` (RFG-decompiled 1.7.10 sources).

- `net/minecraft/entity/item/EntityItem.java:91-100` — `onUpdate` calls
  `stack.getItem().onEntityItemUpdate(this)` before anything else. The mixin's HEAD injection runs before this
  line, so the hook is invoked twice.
- `net/minecraft/entity/item/EntityItem.java:102-120` — vanilla null-checks `getEntityItem()` and calls
  `super.onUpdate()`, then applies `motionY -= 0.03999999910593033D` and `moveEntity`.
- `net/minecraft/entity/Entity.java:1082-1084` — `handleWaterMovement` expands the bounding box downward by 0.4
  and uses `handleMaterialAcceleration`; this is the robust water test that the mixin bypasses.
- `net/minecraft/entity/Entity.java:1169-1172` — `getEyeHeight()` returns `0.0F`; `EntityItem` has no override.
- `net/minecraft/block/BlockLiquid.java:40-48` — `getLiquidHeightPercent(meta)` returns `(meta+1)/9` clamped at
  meta ≥ 8. It is a fraction of a block, not a surface height, and it is always positive.
- `net/minecraft/entity/item/EntityItem.java:45-60` — the constructor gives a freshly dropped item a random
  `motionY = 0.2`; a test that wants to verify “slow lift from rest” must set the motion explicitly.
- `net/minecraft/entity/item/EntityItem.java:139-153` — vanilla drag is `0.98` (and slipperiness on ground), so the
  mixin's extra `0.95` horizontal damping and `+0.0005` vertical drift are additive to vanilla's physics.

## Appendix B — measured results

| Measurement | Value |
|---|---|
| Unit tests | 65 run, 6 skipped (`@Ignore`), 0 failures |
| Game-test phases | 5 phases × 120 ticks, plus the synchronous config-bus check |
| Game-test assertions | 11 checks; 4 documented known bugs remain; `FLOATINGITEMS_GAMETEST_OK` written |
| Game-test run time | ~47 s on the development machine (including Forge startup) |
| JaCoCo unit line | 574/716 = 80.2% |
| JaCoCo unit branch | 421/536 = 78.5% |
| JaCoCo unit instruction | 1,871/2,292 = 81.6% |
| JaCoCo floor | 65% line / 70% branch |
| JaCoCo game-test run | 111/264 lines = 42.0%, 26/102 branches = 25.5% (short physics-only run) |
| Test JVM | Java 8 toolchain (Temurin 8.0.432) |

The game test prints per-phase Y values (`waterY`, `airY`, `lavaY`, `negativeY`) and the observed
`onEntityItemUpdate` count. Across the last two runs: `waterY` rose from 101.0 to ~101.9–104.1 (the exact position depends on how quickly
the forced chunk starts ticking), `airY ≈ 4.125` (from 110.0), `lavaY ≈ 101.4–101.5`, `negativeY ≈ 101.125`
(no float), and `onEntityItemUpdateCalls ≈ 238` for 120 ticks (evidence for B-06).
