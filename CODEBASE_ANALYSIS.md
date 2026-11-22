# FloatingItems-Legacy — Codebase Analysis, Improvement Plan & Test Baseline

Review date: 2026-02-20  
Scope: `src/main/java` (9 classes, ~1,500 LOC including the 1,024-line `FILang`), `src/main/resources`
(mixin configs, `mcmod.info`), Gradle/GTNH build, CI workflows.  
Baseline: Minecraft 1.7.10 / Forge 10.13.4.1614 / GTNH buildscript 2.0.34 / UniMixins 0.3.x.

This document records the review. The P0-P3 bug-fix, cleanup and refactor batches are applied as noted in the
tables below. The remaining work is the feature backlog in §7; B-25 is closed by design. The text below keeps the
original defect descriptions for context; each change log records what was actually changed.

The companion test harness is described in §9 and wired through `addon.gradle.kts`, `dependencies.gradle`,
`src/test/java`, `src/gameTest/java` and `.github/workflows/game-test.yml`. Known bugs use the `B-xx` ids below; the
unit and in-game suites currently have no skipped or xfail tests.

---

## 1. At a glance

| Area | Finding |
|---|---|
| Critical correctness | **Fixed for the P0/P1 batch.** The mixin redirects vanilla's single `onEntityItemUpdate` call (B-06), validates config entries individually (B-01/B-02/B-03), and skips null entries. |
| Event wiring | **Fixed (B-10).** `FloatingItems` now registers on `FMLCommonHandler.bus()`, where both `PlayerLoggedInEvent` and `ConfigChangedEvent` are posted. The game test posts the config event and observes the reload. |
| Physics | **Fixed (B-08/B-17/B-21), then refined by F-04.** Liquid detection scans the entity's contracted bounding box through `LiquidRules`, using Forge `IFluidBlock`/`Fluid` metadata with a `Material.water` fallback. Negative coordinates, the dead eye-height/liquid-metadata check and the single-feet-block mismatch remain fixed. |
| Config | **Fixed (B-01/B-02/B-03).** Entries are parsed independently, missing metadata defaults to 0, unknown IDs are retained until postInit so other mods can finish registering their items, and unresolved entries are removed by `resolveItems()`. A backend failure falls back to safe defaults. Property order and comments are applied and saved (B-20/D-06). |
| Localisation | **Fixed for P2 (B-11/B-13/B-16).** `FILang` now loads UTF-8 `.lang` resources through a map, so keys are no longer compared with `==`, Chinese uses ASCII `%s`, and lowercasing uses `Locale.ROOT`. The `en_ud` ServerStop text is fixed; B-14 is covered by a normal test. |
| Dead code | `FIConfigItem(ItemStack)`, the old `propOrder` misuse, the ServerStop keys and `System.out` were removed. `FIConfig.forceSave`, `FloatingItems.instance` and two `FIConfigGUI` methods are retained as deprecated public-API shims; the empty late mixin config (D-08) is required by the manifest. |
| Performance | **First perf pass applied.** Blacklists are one immutable snapshot with an item→metadata map and a per-snapshot `(Item, metadata)` decision cache; empty snapshots take a fast path. The mixin now does no per-tick blacklist scans, registry lookups after the first decision, or ore-dictionary queries after the first decision. |
| Packaging | **Fixed (B-19).** `@Mod(dependencies)` and `mcmod.info` now declare `unimixins`; `FIReferenceTest` and `FloatingItemsMetadataTest` guard both. |
| Testability | The redirect keeps vanilla control flow; `FloatingItemPolicy` owns the blacklist/liquid/motion rules, `LiquidRules` owns the fluid policy, and both are unit-tested with mocked `World`/`Entity`, while the same paths remain covered in-game. |

Current verification baseline:

```text
./gradlew check                     PASS  (unit tests + Spotless + Checkstyle + JaCoCo floor + gameTest compile)
./gradlew gameTestServer            PASS  23/23 checks, no known bugs remain
Unit tests                          106 executed, no skipped tests
JaCoCo (unit run)                   line 643/731 = 88.0%, branch 335/442 = 75.8%
```

The game test has no xfails and the unit suite has no skipped tests.

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
| Items that should not Float | `{itemId}(metadata)` or `namespace:name[:metadata|:*]` | Per-metadata item blacklist; `*` means any metadata |
| All Items regardless of Metadata | `itemId` | Whole-item blacklist |
| OreDict Entries that should not Float | `oreDictName` | Whole entry; all metadata |
| Default Server language | locale code | Drives the hand-written `FILang` table |
| Disable FloatingItems Update Check | boolean | Disables both the GitHub request and the message; the old `...Checker Message` key is migrated |

---

## 3. Bug catalogue

Severity: **P0** = crash/feature completely broken; **P1** = wrong behaviour users will notice; **P2** = edge case,
localisation or API contract; **P3** = polish.

| ID | Sev | File / line | Symptom | Verification |
|---|---|---|---|---|
| **B-01** | P0 | `FIConfig.java:58-63, 96-102` | **Fixed.** A malformed entry no longer aborts `syncConfig`; entries are parsed independently, invalid entries are logged/skipped, and a backend failure resets all fields to safe defaults. | `FIConfigTest.oneMalformedEntryDoesNotDiscardTheRestOfTheConfig`, `allInvalidEntriesLeaveEmptyBlacklistsButKeepOtherProperties`, `parserRejectsMalformedAndUnknownEntries` |
| **B-02** | P0 | `FIConfig.java:128-130`; `EntityItemMixin.java:43-46` | **Fixed.** Unknown ids are kept through preInit (so another mod can register the item later), then `resolveItems()` in postInit drops anything still unresolved. The mixin also skips null entries/null items defensively. | `FIConfigTest.unknownItemIdIsRejected...`, `resolveItemsRemovesOnlyUnresolvedEntries`, parser tests; game phase 0 null-item entry |
| **B-03** | P1 | `FIConfig.java:126-129` | **Fixed.** The metadata suffix is optional and defaults to 0; malformed suffixes are rejected without throwing. | `FIConfigTest.missingMetadataDefaultsToZero`, `parserAcceptsOptionalMetadataAndWhitespace` |
| **B-04** | P1 | `FIUpdateHandler.java:29-45, 109-128`; `FloatingItems.java:48-49` | **Fixed.** The check runs on a daemon worker instead of `preInit`, uses bounded connect/read timeouts and a single redirect request to the GitHub `releases/latest` URL, and clears state before applying a result. | `FIUpdateHandlerTest` opt-outs, `processUpdateData` branches, local-HTTP release-redirect success/error cases |
| **B-05** | P2 | `FIConfig.java:164-198` | **Fixed.** `FIConfigItem` is final-by-field for registry/metadata and now has a matching `hashCode`. | `FIConfigTest.equalsIsReflexiveSymmetricTypeSafeAndHashCompatible` |
| **B-06** | P1 | `EntityItemMixin.java:38-39` | **Fixed.** A `@Redirect` wraps vanilla's single `onEntityItemUpdate` call and applies the float rules afterward only when it returns false. | Game phase 0, `onEntityItemUpdate` count is one per tick |
| **B-07** | P1 | `EntityItemMixin.java:41-69` | **Fixed.** Each blacklist is checked independently; a per-metadata mismatch no longer suppresses the registry/ore-dictionary checks. | Game phase 3, normal check |
| **B-08** | P1 | `EntityItemMixin.java:104, 110-113` | **Fixed.** The block-coordinate lookup is gone; the bounding-box water test handles negative coordinates correctly. | Game phase 0, normal check |
| **B-09** | P2 | `EntityItemMixin.java:77-93` | **Fixed.** Ore-dictionary blacklists now match the whole entry and ignore metadata. | Game phase 4, normal check |
| **B-10** | P1 | `FloatingItems.java:33, 36-50` | **Fixed.** The mod now registers on `FMLCommonHandler.instance().bus()`, so config reload and the login notification receive their events. | Game test `a ConfigChangedEvent on the FML bus re-syncs FIConfig (B-10)` (normal check) |
| **B-11** | P2 | `FILang.java:186-1020` (all `text ==` checks) | **Fixed.** Translations are now ordinary map lookups from `.lang` resources; constructed keys work. | `FILangTest.constructedKeyIsTranslated` |
| **B-12** | P3 | `FILang.java:57` | **Fixed.** The `de_alg` alias was removed; it now falls back to English like any other unknown locale. | `FILangTest.germanAlgeriaFallsBackToEnglish` |
| **B-13** | P2 | `FILang.java:978, 988, 1003, 1013` | **Fixed.** The generated Chinese resources use ASCII `%s`. | `FILangTest.chineseTablesUseAsciiPlaceholders` |
| **B-14** | P3 | `assets/floatingitems/lang/en_ud.lang` | **Fixed.** The ServerStop text was replaced with the normal English FloatingItems messages; the placeholder check is now a normal test. | `FILangTest.enUdDoesNotUseServerStopText` |
| **B-15** | P2 | `EntityItemMixin.java:72` | **Fixed.** Ore-dictionary names are trimmed and validated, not stripped of characters. | `FIConfigTest.blacklistSnapshotIgnoresNullEntriesAndNormalizesOreNames` |
| **B-16** | P3 | `FILang.java:9` | **Fixed.** Locale normalization uses `Locale.ROOT`. | `FILangTest.languageCodeLowercasingIsLocaleIndependent` |
| **B-17** | P2 | `EntityItemMixin.java:105-114` | **Fixed.** The eye-height/liquid-metadata comparison was removed; the bounding-box test now lives in `LiquidRules` and supports the explicit F-04 fluid policy. | `LiquidRulesTest` + game water/lava checks |
| **B-18** | P1 | `FIUpdateHandler.java:109-128`; `FloatingItems.java:49` | **Fixed.** The login handler returns early when the checker is disabled/current and adds each message only when it is non-null. | `FloatingItemsEventTest` no-message, one-message, both-message, disabled and current-version cases |
| **B-19** | P1 | `FIReference.java:9`; `mcmod.info`; built manifest | **Fixed.** `@Mod(dependencies)` now contains `required-after:unimixins` (and no trailing separator), and `mcmod.info` lists `unimixins` in `requiredMods`. | `FIReferenceTest.dependenciesDeclareForgeAndUniMixins`, `FloatingItemsMetadataTest.mcmodInfoDeclaresUniMixins` |
| **B-20** | P2 | `FIConfig.java:43, 65-90` | **Fixed.** `setCategoryPropertyOrder` is applied and the config is saved so comments/order persist. | `FIConfigTest.allPropertiesAreExposedToTheConfigGuiInTheConfiguredOrder` |
| **B-21** | P2 | `EntityItemMixin.java:104-118` | **Fixed.** Liquid detection uses the contracted entity bounding box, so the feet block and surface/edge disagreement are gone. F-04 moved the scan into `LiquidRules`. | `FloatingItemPolicyTest.liquidCheckDelegatesToTheContractedBoundingBox` + game water check |
| **B-22** | P3 | `EntityItemMixin.java:38-39, 45-48, 61-63, 77-85` | **Fixed.** `applyFloating` reads the post-hook stack once and reuses it for the blacklist and water checks. | Covered by B-02/B-06/B-07 |
| **B-23** | P2 | `FIConfig.java:23-31`; `EntityItemMixin.java:41-100` | **Fixed.** `FIConfig.blacklist` is a volatile immutable snapshot; unmodifiable lists/sets replace the public arrays. | `FIConfigTest.blacklistSnapshotIsImmutable` |
| **B-24** | P3 | `FIConfig.java:197` | **Fixed.** Identity is registry + metadata only, with a matching hash. | `FIConfigTest.equalsIsReflexiveSymmetricTypeSafeAndHashCompatible` |
| **B-25** | P3 | `FloatingItems.java:43-50` | **Closed by design.** The notification is sent at most once per player per server session. Disabling the update check intentionally disables both the request and the notification, and messages deliberately use the configured server language rather than per-player locale. | `FloatingItemsEventTest.updateMessagesAreSentOncePerPlayer`; config tests cover the single opt-out |
| **B-26** | P3 | `FIUpdateHandler.java:80` | **Fixed.** Numeric core, prerelease and `+build` metadata are compared; a remote older version no longer reports an update. | `FIUpdateHandlerTest.semanticVersionOrderingHandlesCoreAndPrerelease`, `buildMetadataDoesNotTriggerAnUpdate` |
| **B-27** | P3 | `FIUpdateHandler.java:31-33, 40-42` | **Fixed in the P0 pass.** The `Scanner` text-file path was removed with the GitHub redirect rewrite; `HttpURLConnection` is disconnected in `finally`. | Static review + `FIUpdateHandlerTest` |
| **B-28** | P3 | `FIConfig.java:105-108` | **Fixed.** `getConfigElements()` returns an empty list when the config has not been initialized. | `FIConfigTest.configElementsAreEmptyBeforeInit` |
| **B-29** | P3 | `FIConfig.java:56, 71, 79, 84` | **Fixed.** Property order/comments are followed by an unconditional save, so comments persist even when no value changed. | `FIConfigTest.configCommentsArePersisted` |

### 3.1 P0 change log (applied)

| ID | Change |
|---|---|
| B-01/B-02/B-03 | `FIConfigItem.parse(String)` validates the shape and optional metadata (default 0); item resolution is lazy so a mod that registers later in preInit is not lost. `FIConfig.syncConfig` parses each entry independently, logs/skips malformed entries, always assigns every blacklist array, and resets to safe defaults if the backend fails. `FIConfig.resolveItems()` runs in postInit (and after config reloads) to drop ids that still do not resolve. The mixin skips `null` entries and `null` items defensively. |
| B-04/B-18 | `FIUpdateHandler` runs the check on a daemon worker, resolves the latest GitHub release tag from the `releases/latest` redirect with 5 s connect/read timeouts, exposes `processUpdateData` for branch tests, and clears state before applying a result. `FloatingItems.onPlayerLogin` guards the disabled/current cases and adds each non-null message independently. |
| B-10 | `FloatingItems.preInit` registers on `FMLCommonHandler.instance().bus()` instead of `MinecraftForge.EVENT_BUS`. |
| B-19 | `FIReference.DEPENDENCIES` now declares `required-after:unimixins` (no trailing separator), and `mcmod.info` lists `unimixins` in `requiredMods`. |

The game test has no xfails. The unit suite no longer has any `@Ignore` markers (B-14 is fixed).

### 3.2 P1 change log (applied)

| ID | Change |
|---|---|
| B-06 | `EntityItemMixin` now uses `@Redirect` on vanilla's `Item.onEntityItemUpdate` call, calls it exactly once, and applies the float rules only when it returns false. |
| B-07 | `isBlacklisted` checks the item/metadata, registry and ore-dictionary lists independently; `shouldFloat` is gone. |
| B-08/B-17/B-21 | Liquid detection uses `EntityItem.boundingBox.contract(...)` and scans the overlapping blocks through `LiquidRules`. It no longer reads `(int) posX`, `getEyeHeight()` or liquid metadata, so negative coordinates and the dead eye-height check are removed. |

### 3.3 P2 change log (applied)

| ID | Change |
|---|---|
| B-09 | Ore-dictionary blacklists are whole-entry: metadata is intentionally ignored for those entries. |
| B-11/B-13/B-16 | `FILang` now loads `assets/floatingitems/lang/*.lang` through a UTF-8 map, uses `Locale.ROOT`, fixes Chinese `%s`, and aliases the regional locales to canonical files. |
| B-15 | Ore-dictionary names are trimmed, not stripped of valid characters. |
| B-20/D-06 | `setCategoryPropertyOrder` is applied and the config is saved so comments/order persist. |
| B-23 | `FIConfig.blacklist` is an immutable `Blacklist` snapshot (unmodifiable item list and sets), replaced atomically. Public mutable arrays were removed. |
| B-05/B-24 | `FIConfigItem` has final registry/metadata identity, `equals` and a matching `hashCode`. |

### 3.4 P3 change log (applied cleanup)

| ID | Change |
|---|---|
| B-12 | Removed the `de_alg` alias; it now falls back to English. |
| B-14 | Replaced the `en_ud` ServerStop text with plain English FloatingItems messages (and normal placeholder checks). |
| B-25/B-26 | Update notifications are sent once per player per session; semantic core/prerelease and `+build` comparison are implemented. B-25 is closed by design: one opt-out covers check and notification, and server-language messages are intentional. |
| B-28 | `getConfigElements()` is null-safe before initialization. |
| D-03/D-09/D-11/D-12/D-15 | Dead constructor/keys/debug/duplication removed. D-02/D-04/D-05 are retained as deprecated public API shims. |
| D-08 | Kept: the empty late mixin config is required by the manifest. |

---

## 4. Dead code

| ID | Location | Finding |
|---|---|---|
| **D-01** | `core/CoreMod.java` | **Removed.** `gradle.properties` sets `coreModClass = mixins.EarlyMixinsLoader`, the built manifest contains `FMLCorePlugin: ...EarlyMixinsLoader`, and `EarlyMixinsLoader` already implements `IEarlyMixinLoader`. `CoreMod` only called `MixinBootstrap.init()`, which UniMixins/MixinTweaker already handles. |
| **D-02** | `FIConfig.java:38-40` | **Retained as a deprecated compatibility shim.** It has no internal callers, but it was public API; it now null-checks the config. |
| **D-03** | `FIConfig.java:117-122` | **Removed in the P3 pass.** The unused `FIConfigItem(ItemStack)` constructor and helpers were deleted. |
| **D-04** | `FloatingItems.java:25-26` | **Retained for compatibility.** The `@Mod.Instance` field has no internal readers but is public FML/addon surface. |
| **D-05** | `FIConfigGUI.java:46-60` | **Retained as deprecated compatibility methods.** They are not part of 1.7.10 `IModGuiFactory` but are public API; no internal callers. |
| **D-06** | `FIConfig.java:43, 65, 73, 81, 86, 90` | **Fixed in the P2 pass.** `propOrder` is applied with `setCategoryPropertyOrder` and the config is saved. |
| **D-07** | `FIConfig.java:63` | **Removed in the P0 pass.** The always-true `blacklist != null` guard is gone; the array is assigned unconditionally. |
| **D-08** | `src/main/resources/mixins.floatingitems.json` | **Keep.** The buildscript puts `MixinConfigs: mixins.floatingitems.json` in the manifest; deleting the file would leave a missing config reference. The empty file is a required build artifact, not dead code. |
| **D-09** | `assets/floatingitems/lang/el_gr.lang`, `en_pt.lang` | **Removed in the P3 pass.** The dead ServerStop keys were deleted; tests assert they fall back to their keys. |
| **D-10** | `FIConfig.java:177-195` | **Removed in the P2 pass** with the `FIConfigItem` rewrite. |
| **D-11** | `FloatingItems.java:30` | **Removed in the P3 pass.** |
| **D-12** | `EarlyMixinsLoader.java:16-18` | **Fixed in the P3 pass.** Returns an empty array and is covered by `MixinsTest`. |
| **D-13** | `FIReference.java:9` | **Fixed in the P0 pass.** The dependency string no longer ends with a separator. |
| **D-14** | `FIConfigGUI.java:36-44` | **Fixed in the P3 pass.** The empty/no-runtime-options contract is documented; the non-interface methods are retained as deprecated shims by D-05. |
| **D-15** | `FIUpdateHandler.java:49-78, 80-130` | **Fixed in the P3 pass.** `styleUpdateMessage` centralizes the color/hover/click construction. |

### 4.1 Dead-code audit

- **Safe internal removals:** `CoreMod` (never registered), `FIConfigItem(ItemStack)` (package-private, test-only), the commented `equals` block, the ServerStop translation keys, `System.out.println("Pre Init")`, the duplicate update-message styling, and the `de_alg` alias (config-only).
- **Not safe to assume addons never used them:** `FIConfig.forceSave`, `FloatingItems.instance` and `FIConfigGUI.createConfigGui`/`hasConfigGui` were unused inside this repository but are public surface. They are retained as deprecated compatibility shims.
- **Intentionally breaking API changes from P2:** the public mutable `FIConfig.blacklist*` arrays were replaced by the immutable `FIConfig.blacklist` snapshot, and the public `FILang.*Lang` helper methods were replaced by the resource-backed `getTranslations`. Addons must migrate to the snapshot/getter or translation key API.
- **D-08 is not dead code:** the buildscript writes `MixinConfigs: mixins.floatingitems.json` into the manifest, so the (currently empty) late config file must remain. Deleting it produced a missing-config reference in the built jar.

---

## 5. Performance

| ID | Location | Finding / fix |
|---|---|---|
| **P-01** | `EntityItemMixin.java:41-100` | **Fixed in the first perf pass.** The immutable `Blacklist` builds an item→metadata map and lazily caches the final decision per `(Item, metadata)`; a config reload creates a fresh snapshot/cache. Empty snapshots skip caching entirely. |
| **P-02** | `EntityItemMixin.java:74-77` | **Fixed.** Ore-dictionary results are folded into the per-stack decision cache; no per-tick `getOres` loop remains. |
| **P-03** | `EntityItemMixin.java:72` | **Fixed in the P2/P3 pass.** Names are trimmed once into the immutable snapshot; no regex runs per tick. |
| **P-04** | `EntityItemMixin.java:38-39, 45-48, 61-63, 77-85` | **Fixed in the P1 pass.** `applyFloating` reads the post-hook stack once and reuses it. |
| **P-05** | `EntityItemMixin.java:61-63` | **Fixed.** `Item.getIdFromItem` is only reached on the first uncached decision for an item/metadata pair; subsequent ticks hit the snapshot cache. |
| **P-06** | `EntityItemMixin.java:104, 110-113` | **Fixed in the P1 pass.** The direct `getBlock`/`getBlockMetadata` lookups were replaced by a contracted bounding-box scan, now housed in `LiquidRules`. |
| **P-07** | `FIConfig.java:42-103` | **Fixed in the P2 pass.** `syncConfig` publishes one immutable `Blacklist` snapshot atomically. |
| **P-08** | `FIUpdateHandler.java:29-45` | **Fixed in the P0 pass.** The check is asynchronous, has 5 s timeouts and is daemon-only; the two requests are still sequential inside the worker (acceptable for a background check). |
| **P-09** | `FILang.java:5-180` | **Fixed in the P2 pass.** Map lookup over cached `.lang` tables. |
| **P-10** | `FIConfig.java:96-102` | **Improved in the P0 pass.** Per-entry failures are logged and skipped; the outer backend failure logs the exception summary and resets defaults instead of dumping a stack trace. |

---

## 6. Refactors

| ID | Refactor | Why |
|---|---|---|
| **R-01** | Extract a pure `FloatingItemPolicy` (or `Blacklist`) from the mixin: `boolean shouldFloat(ItemStack stack, World world, int x, int y, int z)`. | **Applied in the refactor batch:** `FloatingItemPolicy` owns the blacklist/water/motion rules; the mixin is a thin redirect adapter. |
| **R-02** | Represent blacklist entries as registry names + optional metadata wildcard, and pre-compute sets at config sync. | **Applied in the refactor batch:** registry-name entries, `:meta`, `:*`/`:-1` wildcards, legacy `{id}(meta)` fallback and wildcard lookup are covered. |
| **R-03** | Split `FIConfig` into a config-holder, a parser/validator and a `BlacklistSnapshot`. Parse each entry independently and report/skip bad entries. | **Applied:** `FIConfigParser` reads and validates `Configuration` into an immutable `ParsedConfig`; `FIConfig` remains the lifecycle/state holder and publishes a top-level `FIConfigSnapshot`. The old nested `Blacklist`/`FIConfigItem` types remain as compatibility wrappers. Malformed item entries are skipped independently. |
| **R-04** | Replace the 1,024-line `FILang` chain with a data table loaded from `assets/floatingitems/lang/*.lang` (or a `Map` constant). Keep the server-side selection mechanism. | **Applied in the P2 pass:** UTF-8 `.lang` resources + alias map; B-11/B-13/B-16 fixed and the Java table is gone. |
| **R-05** | Wrap the update check in an async service with timeouts and a small result object; build chat components from a real translation key + siblings, not a full sentence used as a key. | **Applied in this batch:** `UpdateCheckService`/`UpdateCheckResult` isolate the async check and result; `FIUpdateHandler` builds localized components from the key format and sibling arguments. |
| **R-06** | Register the event handler on `FMLCommonHandler.instance().bus()` (or both buses) and add a test that posts the event. | **Applied in the P0 pass:** the handler is on the FML bus and the game test posts the event. |
| **R-07** | Use a mod-specific `Logger` (`LogManager.getLogger(FIReference.MOD_ID)`) instead of `System.out`, `printStackTrace` and `FMLRelaunchLog` for normal messages. | **Applied in this batch:** `FIConfig` and `FIUpdateHandler` use the mod logger; test-only markers remain in the game-test mod. |
| **R-08** | Use `isInWater()`/`handleMaterialAcceleration` and a floored block position; optionally query the block at the entity's bounding-box centre and below. | **Applied in the P1 pass, refined by F-04:** `LiquidRules` scans the contracted bounding box and evaluates Forge fluid metadata. |
| **R-09** | Move the inject from `HEAD` to after vanilla's `onEntityItemUpdate` check (or use a `@Redirect`/`@ModifyVariable` on the hook call) so the hook is called exactly once. | **Applied in the P1 pass:** a `@Redirect` wraps the single vanilla call and preserves its early return. |
| **R-10** | Remove `CoreMod` (D-01, done) and other dead items, while keeping the manifest-required empty late config (D-08) and public compatibility shims. | Smaller jar without breaking addons or the mixin manifest. |
| **R-11** | Add `@SideOnly(Side.CLIENT)` to `FIConfigGUI`/inner class and move GUI-only code to a client package if the package rename is acceptable. | **Applied:** both GUI classes are client-only; the inner class is annotated as well. |
| **R-12** | Add a public API (`FloatingItemsAPI`/`IFloatingItem`) if addons are expected to customise floating. Keep the current static config accessors deprecated. | **Applied:** added `com.mrfuzzihead.floatingitems.api` with `FloatingItemsAPI`, `BlacklistSnapshot` and `BlacklistEntry`; `apiPackage = api` and `apiJar` are enabled. The old nested types remain deprecated wrappers. |
| **R-13** | Move the root package to `com.mrfuzzihead.floatingitems` (or add a compatibility facade). | **Applied:** source, tests, mixin config, coremod path, `modGroup` and JaCoCo filters use `com.mrfuzzihead.floatingitems`. No compatibility facade is shipped because a scan of all 238 external mod jars in the reference instance found no references to the previous packages or classes. |
| **R-14** | Add `hashCode`, use `Objects.equals`, and make `FIConfigItem` immutable. | **Applied for B-05/B-24:** registry/metadata are final and `equals`/`hashCode` match. |
| **R-15** | Keep `FIConfigGUI`'s non-interface methods as deprecated shims for addons; only the 1.7.10 interface is used internally. | D-05 compatibility. |

---

## 7. Features / improvements

| ID | Feature | Notes |
|---|---|---|
| **F-01** | Config entries by registry name (`minecraft:log:1`), with `*`/`-1` metadata wildcard, and a warning for unknown entries. | **Applied:** registry names and wildcards are supported; unknown names are logged/removed at postInit. Legacy numeric entries remain. |
| **F-02** | Whitelist mode (`only these items float`) in addition to the blacklist. | Useful for servers that want floating only for a few items. |
| **F-03** | Per-dimension / per-biome / per-mod-id rules. | Nether/End water is rare but modded dimensions often have custom fluids. |
| **F-04** | Explicit liquid policy: water only vs any `Material.water` vs a configurable list; do not use `isLiquid() && != lava` as an implicit “all modded liquids” switch. | **Applied:** default `water_like` accepts vanilla water and water-named Forge fluids with water-like density/temperature/viscosity; `water` restricts to vanilla water, `material_water` preserves the legacy check. Forge `IFluidBlock` metadata is used before the material fallback, and the fluid allow/deny lists override the policy. An oil-like fluid with `Material.water` is rejected by default. |
| **F-05** | Tunable buoyancy: lift acceleration, horizontal damping, maximum upward speed and surface offset. | Current values (`+0.04`, `0.95`, `+0.0005`) are magic numbers. A “source blocks only” switch is a deliberate deviation from modern Java, not parity. |
| **F-06** | Match modern water currents: items should be pushed by flowing water while floating, not just damped. | **Mostly present:** 1.7.10 `World.handleMaterialAcceleration` already applies the water current (`0.014`); the mod cancels gravity and adds only a slow lift. Downward flow therefore carries items down, matching modern Java. Add tests; change code only if a gap is found. |
| **F-07** | Surface bobbing: a tiny idle oscillation or a stable resting height, instead of the current slow +0.0005 drift. | Cosmetic/behavioural parity with modern Minecraft. |
| **F-08** | Public events: `ItemFloatCheckEvent` (cancelable) and `ItemFloatTickEvent` (post), plus `IFloatingItem` for item-specific opt-in/out. | Addon-friendly; lets other mods override the blacklist without touching `FIConfig`. |
| **F-09** | Per-player localisation for the update message; ship `.lang` resources and use the client locale where possible. | **Not planned:** the mod is server-side and the configured server language is the supported behaviour; B-25 is closed by design. |
| **F-10** | Update checker: opt-in, async, cached, with a connect/read timeout, semantic version comparison, and a “show once per player” flag. | **Mostly applied:** async/timeouts, semver, build metadata, result object and show-once are done; result caching remains. |
| **F-11** | Config GUI categories/lang keys, logical property order (`setCategoryPropertyOrder`), and a “reload” button/command. | Fixes D-06/B-20; makes the config usable on a live server. |
| **F-12** | `/floatingitems status` and `/floatingitems reload` commands. | Operators can inspect the effective blacklist and reload without restarting. |
| **F-13** | Debug counters (items evaluated, items floated, cache hits) behind a config flag. | Makes P-01 measurable in production. |
| **F-14** | More game tests: partially submerged items, source vs flowing water (especially downward flow), chunk borders, unload/reload, item NBT, modded liquid, item merge/despawn, config reload. | **Applied:** 23 in-game checks now cover all of these except a live chunk unload/reload; NBT save/load covers persistence, while a real unload/reload is left as a follow-up because the forced chunk ticket and dedicated-server unload ordering make it flaky. |
| **F-15** | Add a smoke test for a dedicated server boot without the game-test mod. | The current `gameTestServer` exits after assertions; a separate `-PsmokeTest` boot is useful for packaging/CI. |
| **F-16** | Add Modrinth/CurseForge relationships for UniMixins and document the dependency in `mcmod.info`/README. | **Partly applied in the P0 pass:** `@Mod(dependencies)` and `mcmod.info` are fixed; publishing relations remain. |
| **F-17** | Add a `data/`-style regression table for modern 1.13+ physics and a compatibility note for each supported Forge/MCP version. | Prevents future “backport” drifts. |

### 7.1 Priority order

Ranked by user value, regression risk and dependencies. This is the order to work in; it is not a promise to implement every item.

**F-14 is applied:** the in-game suite now has 23 checks, including the source-vs-downward-flowing-water control,
partial submersion, chunk borders, NBT, merge/despawn, config reload and an oil-like Forge fluid. A live chunk
unload/reload remains a follow-up because it is flaky on the forced-chunk dedicated server; the NBT round trip covers
the persistence data.

**F-04 is applied:** the default `water_like` policy accepts vanilla water and water-named Forge fluids with water-like
properties, while `water` restricts to vanilla water and `material_water` preserves the old behaviour. The fluid
allowlist and denylist override the policy, and an oil-like fluid with `Material.water` is rejected by default.

**P1 — next**

1. **F-02** — whitelist mode. High server value; build it on the same policy/config shape as F-04 so the two do not grow separate parsers or reload paths.
2. **F-05** — tunable buoyancy. Expose a small set only: lift, horizontal damping, maximum upward speed and surface offset. Validate/clamp every value. Do not add a “source blocks only” switch as a parity feature; it would deliberately diverge from modern Java.
3. **F-12** — `/floatingitems status` and `/floatingitems reload`. Small operational win, and the reload path is reused by F-11. `status` should show the effective blacklist/whitelist, liquid policy and buoyancy settings.

**P2 — after P1**

4. **F-11** — config GUI categories/lang keys and a reload button. Client-only and larger; share the F-12 reload service rather than adding a second reload path.
5. **F-08** — public float events and `IFloatingItem`. Higher addon value, but the event contract should be designed after F-02/F-04/F-05 stabilise the policy semantics.
6. **F-06** — verify modern current behaviour and add the flowing-water test. The source-vs-downward-flow control is now in F-14; the remaining work is comparison against 1.13+ and any gap it exposes.
7. **F-03** — per-dimension / per-biome / per-mod-id rules. High complexity and niche value; start with mod-id rules if it is pursued, then dimensions, then biomes.
8. **F-13** — debug counters behind a config flag. Useful for production measurement but not worth adding before the policy work is stable.
9. **F-15** — dedicated-server smoke boot without the game-test mod. Release/CI safety rather than gameplay value.

**P3 — defer / release hygiene**

10. **F-07** — surface bobbing. Cosmetic; fold into F-05’s surface offset if it is ever done.
11. **F-10** — update-check result caching. The check runs once at startup, so the benefit is small; optional.
12. **F-16** — Modrinth/CurseForge relationships and dependency documentation. Low effort, but release metadata rather than code.
13. **F-17** — modern-physics regression table and Forge/MCP compatibility notes. Documentation; do it as the physics work settles.

**Not planned:** F-09 (per-player update-message localisation).

---

## 8. Packaging, build and compatibility

1. **UniMixins is declared now** (B-19 fixed). `FIReference.DEPENDENCIES` contains `required-after:unimixins`, and
   `mcmod.info` has `"requiredMods": ["unimixins"]`. The built jar still has `TweakClass:
   org.spongepowered.asm.launch.MixinTweaker`, `MixinConfigs: mixins.floatingitems.json` and `FMLCorePlugin:
   com.mrfuzzihead.floatingitems.mixins.EarlyMixinsLoader`.
2. **One coremod** (D-01 fixed). `EarlyMixinsLoader` is the registered coremod and implements `IEarlyMixinLoader`; the unused `core/CoreMod` was removed. The empty late mixin config (D-08) is intentionally kept because the manifest references it.
3. **The dependency string is clean** (D-13 fixed). It no longer ends with a separator.
4. **The accepted Minecraft range** is `[1.7, 1.7.10]`. FML compares the target version as a version; `1.7.10` is
   inside the range, but `[1.7.10]` would express the intent more clearly. The mod uses only 1.7.10 APIs.
5. **The `mcmod.info` description** says “A small server-side mod…”. That is accurate. The README no longer
   advertises addons; the read-only API (R-12) is the supported surface and ships in the `-api` classifier. The root
   package was renamed under R-13.
6. **The build has no test dependencies until this pass.** `dependencies.gradle` now adds JUnit 4.13.2,
   Mockito 4.11.0 and mockito-inline; `addon.gradle.kts` adds JaCoCo and the game-test source set. The GTNH reusable
   workflow already runs `check`; the new `game-test.yml` runs the dedicated server separately.
7. **API artifact**: `apiPackage = api` is set and `./gradlew apiJar` produces the `-api` classifier containing only `com.mrfuzzihead.floatingitems.api`. The API is intentionally read-only. The root package move (R-13) is applied; the previous package is not retained as a facade because no external mod in the reference instance referenced it.
8. **Configuration cache**: `org.gradle.configuration-cache = true` is enabled. The new build script was written
   with task inputs (`@Classpath`, `@Input`) and no configuration-time resolution for the JaCoCo agent; `check`
   and `gameTestServer` were run successfully with the cache enabled.

---

## 9. Testing infrastructure added in this pass

### 9.1 Build wiring

| File | Purpose |
|---|---|
| `addon.gradle.kts` | Applies JaCoCo, configures `Test` on a Java 8 toolchain, extends the test classpaths with `compileOnly` (Minecraft/Forge), creates `build/reports/jacoco/test`, enforces a line/branch floor, creates the `gameTest` source set, and registers `prepareGameTestServer`/`gameTestServer`/`jacocoGameTestReport`. |
| `dependencies.gradle` | JUnit 4.13.2, Mockito 4.11.0, mockito-inline. Mockito 4 is the newest line that runs on Java 8; mockito-inline allows final Minecraft classes to be mocked. |
| `src/test/java/...` | 11 test classes / 106 tests (no `@Ignore` markers). |
| `src/gameTest/java/...` | A non-shipped `@Mod` (`floatingitemsgametest`) plus a 13-phase assertion suite. |
| `.github/workflows/game-test.yml` | Installs Java 8 + 25, runs `./gradlew test gameTestServer`, checks the `FLOATINGITEMS_GAMETEST_OK` marker, uploads the server log and coverage. |
| `README.md` | Testing commands and the remaining P3 marker policy. |

### 9.2 Unit tests

| Class | What it covers |
|---|---|
| `FILangTest` | Every supported locale × every update/config key; placeholder preservation; aliases; fallback for null/unknown language; case-insensitivity; constructed keys; Chinese ASCII placeholders; Turkish-locale safety; no skipped tests. |
| `FIConfigParserTest` | Independent config parsing/validation: mixed valid/malformed entries, defaults, unknown-item retention, legacy update-key migration, liquid policy and fluid-list parsing, property order and immutable parser results. |
| `FIConfigTest` | Defaults, logical property order, liquid policy/fluid lists, legacy and registry-name parsing, wildcard metadata, malformed/unknown entries, deferred resolution, all-invalid/backend-failure fallbacks, immutable snapshot, per-snapshot decision cache/empty fast path, equality/hash. |
| `FIReferenceTest` | Mod id/name/version injection, accepted MC range, Forge + UniMixins dependencies, config file/category names. |
| `FloatingItemsMetadataTest` | Reads the `@Mod` annotation and the shipped `mcmod.info` back, checks the GUI factory class name, its `@SideOnly(CLIENT)` marker and the UniMixins requirement. |
| `MixinsTest` | One early mixin named `EntityItemMixin`, phase EARLY, early config path. |
| `FloatingItemPolicyTest` | Blacklist short-circuit, null stack/item, contracted liquid-bounding-box lookup, null world/box, and buoyancy arithmetic directly through `FloatingItemPolicy`. |
| `LiquidRulesTest` | Default `water_like` policy, vanilla water, oil-like and hot/heavy/thick fluids, allow/deny precedence, legacy `material_water`, Forge `IFluidBlock` detection and contracted world scanning. |
| `FIUpdateHandlerTest` | System-property/config opt-outs, current/older/ended/null version branches, result-object state handling, localized component contents, service skip path, and GitHub `releases/latest` redirect parsing against a local `HttpServer`. |
| `FloatingItemsEventTest` | Login with no/one/both update messages, disabled checker and current version; verifies the null-message guard with a mocked `EntityPlayerMP`. |
| `FloatingItemsAPITest` | API snapshot exposure, read-only blacklist queries, registry-name/wildcard parsing and API identity. |
| `MinecraftTestBootstrap` | Injects a fake `minecraft:stick` into the real `RegistryNamespaced` maps without calling FML's `GameData.addPrefix`, which needs a `LaunchClassLoader`. Also initialises `FMLInjectionData.minecraftHome` so `Configuration` can be constructed headlessly. |

Run: `./gradlew test` (Java 8 toolchain). Report: `build/reports/tests/test/index.html`.

### 9.3 Game tests

`GameTestMod` is a separate `@Mod` with `dependencies = "required-after:floatingitems"`, discovered only when the
`gameTest` output is on the run classpath. It registers a `ServerTickEvent` handler and exits the JVM with the
suite's status. `FloatingItemsGameTests` builds a small arena at y=100 (water pool, lava pool, one-block
negative-coordinate column, chunk-border pool, downward waterfall, shallow pool, test-only oil-like Forge fluid, and two
air columns), obtains a Forge chunk ticket so the no-player dedicated server keeps the chunks loaded, and runs
thirteen phases:

| Phase | Assertions |
|---|---|
| 0 | item rises in still water; item falls in air; item in lava does not get water buoyancy; negative-coordinate item floats (B-08); `onEntityItemUpdate` is called once per tick (B-06); null-item blacklist entries are skipped |
| 1 | per-metadata blacklist suppresses the lift |
| 2 | registry blacklist suppresses the lift |
| 3 | registry blacklist is not shadowed by a per-metadata entry with a different meta (B-07) |
| 4 | ore-dictionary blacklist covers all metadata of the entry (B-09) |
| 5 | a real `ConfigChangedEvent` reload applies the item blacklist |
| 6 | still source water rises; the control item in downward-flowing water does not rise |
| 7 | an item with only its lower half in water still floats |
| 8 | an item whose bounding box straddles the x=16/z=16 chunk border floats |
| 9 | item NBT survives a save/load round trip and is preserved while floating |
| 10 | two nearby items merge into one stack |
| 11 | an item with a short lifespan despawns |
| 12 | a test-only oil-like Forge fluid with `Material.water` does not receive water buoyancy |

Plus a synchronous config-bus check at start: posting `ConfigChangedEvent.OnConfigChangedEvent` to the FML bus
reloads `FIConfig` (B-10, now a normal check). A live chunk unload/reload is deliberately not part of the suite: the
forced chunk ticket and dedicated-server unload ordering make it nondeterministic. Phase 9 tests the same persistence
data through an entity NBT round trip instead.

Run: `./gradlew gameTestServer`. The task writes `run/server/eula.txt` and a deterministic `server.properties`
(`gameTestWorld`, flat, offline, no watchdog), deletes the old test world, and exits non-zero on failure.
`prepareGameTestServer` is also safe to run manually.

### 9.4 Coverage

- Unit-test report: `build/reports/jacoco/test/html/index.html` and `.xml`.
- In-game report: `build/reports/jacoco/gameTest/html`, generated after `gameTestServer` via
  `jacocoGameTestReport`. The JaCoCo agent is run with `classdumpdir=build/jacoco/gameTestClasses`, so the report
  reads exactly the bytes the agent instrumented. It is not merged into the unit report because a Forge 1.7.10
  dev launch rewrites classes through `LaunchClassLoader`; the latest in-game report is 321/664 lines (48.3%)
  and 120/414 branches (29.0%)
- Regression floor: 65% line / 70% branch. The measured unit baseline is 87.1% line / 76.1% branch. The floor is a
  guard, not a target; the mixin remains largely uncovered, and the update handler is covered by
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

The P0 batch below is implemented and covered by the tests in §9; P1 and P2 are also complete, and the P3 cleanup/refactors are applied. The remaining work is the feature backlog in §7.

1. **B-02/B-03/B-01** (done): validate every blacklist entry, skip malformed strings, defer item resolution to
   postInit and drop ids that still do not resolve, and never create a null item that crashes the server.
2. **B-19**: declare UniMixins in `@Mod(dependencies)`/`mcmod.info`.
3. **B-10**: register `FloatingItems` on the FML bus (or both buses) so config reload and the login notification work.
4. **B-04/B-18**: move the update check off the preInit thread, add timeouts, guard the null `updateVersionInfo`.

### P1 — user-visible wrong behaviour (done in this revision)

5. **B-06** (done): the hook is now called once through a redirect.
6. **B-07** (done): blacklists are checked independently.
7. **B-08** (done): the block-coordinate lookup was removed with the bounding-box rewrite.
8. **B-17/B-21** (done): liquid detection now uses the contracted entity bounding box and `LiquidRules`.

### P2 — localisation, API, config polish (done in this revision)

9. **B-09** (done): ore-dictionary entries are whole-entry; metadata is intentionally ignored.
10. **B-11/B-13/B-15/B-16** (done): map/resource-table FILang, ASCII placeholders, trimmed ore names and `Locale.ROOT`.
11. **B-20/D-06** (done): apply `setCategoryPropertyOrder`; persist comments.
12. **B-23** (done): publish an immutable blacklist snapshot; mutable arrays removed.
13. **B-05/B-24** (done): stable `FIConfigItem` identity with `equals`/`hashCode`.

### P3 — cleanup / features (cleanup done; feature backlog remains)

14. D-03/D-09/D-11/D-12/D-15 cleanup done; D-08 intentionally kept; D-02/D-04/D-05 retained as deprecated public API shims.
15. R-01/R-02/R-03/R-04/R-05/R-06/R-07/R-08/R-09/R-11/R-12/R-13/R-14/R-15 are applied.
16. F-01, F-04, F-14 and the API part of F-08 are applied. F-02…F-17 remain; the ordered backlog is in §7.1 (F-02 → F-05 → F-12, then the P2/P3 items). F-09 is not planned.

---

## 11. Verification map

| Bug | Test / evidence | Status |
|---|---|---|
| B-01 | `FIConfigTest.oneMalformedEntryDoesNotDiscardTheRestOfTheConfig`, `allInvalidEntriesLeaveEmptyBlacklistsButKeepOtherProperties` | **Fixed** |
| B-02 | `FIConfigTest.unknownItemIdIsRejected...`, `resolveItemsRemovesOnlyUnresolvedEntries`, parser tests; game phase 0 null-item entry | **Fixed** |
| B-03 | `FIConfigTest.missingMetadataDefaultsToZero`, `parserAcceptsOptionalMetadataAndWhitespace` | **Fixed** |
| B-04/B-18 | `FIUpdateHandlerTest` + `FloatingItemsEventTest` | **Fixed** |
| B-05 | `FIConfigTest.equalsIsReflexiveSymmetricTypeSafeAndHashCompatible` | **Fixed** |
| B-06 | Game phase 0, `onEntityItemUpdate` count | **Fixed** |
| B-07 | Game phase 3 | **Fixed** |
| B-08 | Game phase 0, negative pool | **Fixed** |
| B-09 | Game phase 4, wool meta 0 vs 1 | **Fixed** |
| B-10 | Game start, FML config event | **Fixed** |
| B-11 | `FILangTest.constructedKeyIsTranslated` | **Fixed** |
| B-12 | `FILangTest.germanAlgeriaFallsBackToEnglish` | **Fixed** |
| B-13 | `FILangTest.chineseTablesUseAsciiPlaceholders` | **Fixed** |
| B-14 | `FILangTest.enUdDoesNotUseServerStopText` | **Fixed** |
| B-15 | `FIConfigTest.blacklistSnapshotIgnoresNullEntriesAndNormalizesOreNames` | **Fixed** |
| B-16 | `FILangTest.languageCodeLowercasingIsLocaleIndependent` | **Fixed** |
| B-17/B-21 | `FloatingItemPolicyTest.liquidCheckDelegatesToTheContractedBoundingBox`, `LiquidRulesTest` + game water check | **Fixed** |
| B-19 | `FIReferenceTest`, `FloatingItemsMetadataTest` | **Fixed** |
| B-20 | `FIConfigTest.allPropertiesAreExposedToTheConfigGuiInTheConfiguredOrder` | **Fixed** |
| B-23/B-24 | `FIConfigTest.blacklistSnapshotIsImmutable`, equality/hash test | **Fixed** |
| B-25 | `FloatingItemsEventTest.updateMessagesAreSentOncePerPlayer` | Closed by design: once per player; one opt-out and server-language messages are intentional |
| B-26 | `FIUpdateHandlerTest.semanticVersionOrderingHandlesCoreAndPrerelease`, `buildMetadataDoesNotTriggerAnUpdate` | **Fixed** |
| B-29 | `FIConfigTest.configCommentsArePersisted` | **Fixed** |
| B-28 | `FIConfigTest.configElementsAreEmptyBeforeInit` | **Fixed** |

No unit tests are skipped and the game test has no xfail entries.

---

## Appendix A — vanilla evidence used for the physics findings

All paths are under `build/rfg/minecraft-src/java` (RFG-decompiled 1.7.10 sources).

- `net/minecraft/entity/item/EntityItem.java:91-100` — `onUpdate` calls
  `stack.getItem().onEntityItemUpdate(this)` before anything else. The P1 mixin redirects that call instead of
  adding a second HEAD call.
- `net/minecraft/entity/item/EntityItem.java:102-120` — vanilla null-checks `getEntityItem()` and calls
  `super.onUpdate()`, then applies `motionY -= 0.03999999910593033D` and `moveEntity`.
- `net/minecraft/entity/Entity.java:1082-1084` — `handleWaterMovement` uses the entity bounding box with
  `handleMaterialAcceleration`; the mixin scans the same box through `LiquidRules` without applying fluid drag.
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
| Unit tests | 106 run, 0 skipped, 0 failures |
| Game-test phases | 13 phases × 120 ticks, plus the synchronous config-bus check |
| Game-test assertions | 23 checks; no known bugs remain; `FLOATINGITEMS_GAMETEST_OK` written |
| Game-test run time | ~90 s on the development machine (including Forge startup) |
| JaCoCo unit line | 643/731 = 88.0% |
| JaCoCo unit branch | 335/442 = 75.8% |
| JaCoCo unit instruction | 2,653/2,958 = 89.7% |
| JaCoCo floor | 65% line / 70% branch |
| JaCoCo game-test run | 321/664 lines = 48.3%, 120/414 branches = 29.0% (short physics-only run) |
| Test JVM | Java 8 toolchain (Temurin 8.0.432) |

The game test prints per-phase Y values and the observed `onEntityItemUpdate` count. In the final run:
`waterY = 101.84`, `airY = 4.125` (from 110.0), `lavaY = 101.51`, `negativeY = 102.06`, `sourceY = 101.85`,
`flowingY = 101.125`, and `onEntityItemUpdateCalls = 119` for 120 ticks (one per tick; evidence for B-06 being fixed).
