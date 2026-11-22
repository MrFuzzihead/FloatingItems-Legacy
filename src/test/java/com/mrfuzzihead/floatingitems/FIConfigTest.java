package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

import cpw.mods.fml.client.config.IConfigElement;

/**
 * Tests for the config file contract, the immutable blacklist snapshot and {@link FIConfig.FIConfigItem}.
 */
public class FIConfigTest {

    private static final String ITEM_FILTER_MODE = "Item Filter Mode";
    private static final String ITEM_BLACKLIST = "Blacklist of Items that should not Float";
    private static final String REGISTRY_BLACKLIST = "Blacklist of All Items regardless of Metadata that should not Float";
    private static final String OREDICT_BLACKLIST = "Blacklist of OreDict Entries that should not Float";
    private static final String LIQUID_POLICY = "Liquid Policy";
    private static final String FLUID_ALLOWLIST = "Floating Fluid Allowlist";
    private static final String FLUID_DENYLIST = "Floating Fluid Denylist";
    private static final String BUOYANCY_LIFT = "Floating Lift Acceleration";
    private static final String HORIZONTAL_DAMPING = "Floating Horizontal Damping";
    private static final String MAX_UPWARD_SPEED = "Floating Maximum Upward Speed";
    private static final String SURFACE_OFFSET = "Floating Surface Offset";
    private static final String SERVER_LANGUAGE = "Default Server language";
    private static final String DISABLE_UPDATE_CHECK = "Disable FloatingItems Update Check";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Before
    public void resetConfigState() {
        FIConfig.blacklist = FIConfig.Blacklist.empty();
        FIConfig.filterMode = ItemFilterMode.BLACKLIST;
        FIConfig.liquidRules = LiquidRules.defaults();
        FIConfig.buoyancy = BuoyancySettings.defaults();
        FIConfig.serverLang = null;
        FIConfig.disableUpdateCheck = false;
        FIConfig.config = null;
    }

    @After
    public void clearConfigState() {
        resetConfigState();
    }

    @Test
    public void defaultConfigHasEmptyBlacklistsAndExpectedDefaults() throws Exception {
        File file = configFile();
        FIConfig.init(file);

        assertTrue(file.exists());
        assertNotNull(FIConfig.blacklist);
        assertEquals(ItemFilterMode.BLACKLIST, FIConfig.filterMode);
        assertEquals(LiquidPolicy.WATER_LIKE, FIConfig.liquidRules.getPolicy());
        assertEquals(BuoyancySettings.DEFAULT_LIFT, FIConfig.buoyancy.getLift(), 1.0E-9D);
        assertEquals(BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING, FIConfig.buoyancy.getHorizontalDamping(), 1.0E-9D);
        assertEquals(BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED, FIConfig.buoyancy.getMaxUpwardSpeed(), 1.0E-9D);
        assertEquals(BuoyancySettings.DEFAULT_SURFACE_OFFSET, FIConfig.buoyancy.getSurfaceOffset(), 1.0E-9D);
        assertTrue(
            FIConfig.liquidRules.getAllowlist()
                .isEmpty());
        assertTrue(
            FIConfig.liquidRules.getDenylist()
                .isEmpty());
        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(
            0,
            FIConfig.blacklist.getRegistryItems()
                .size());
        assertEquals(
            0,
            FIConfig.blacklist.getOreDict()
                .size());
        assertEquals("en_us", FIConfig.serverLang);
        assertFalse(FIConfig.disableUpdateCheck);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedForceSaveIsNullSafe() {
        FIConfig.config = null;
        FIConfig.forceSave();
    }

    @Test
    public void configElementsAreEmptyBeforeInit() {
        FIConfig.config = null;
        assertTrue(
            FIConfig.getConfigElements()
                .isEmpty());
    }

    @Test
    public void allPropertiesAreExposedToTheConfigGuiInTheConfiguredOrder() throws Exception {
        FIConfig.init(configFile());

        List<IConfigElement> elements = FIConfig.getConfigElements();
        assertEquals(13, elements.size());

        List<String> names = new java.util.ArrayList<String>();
        for (IConfigElement element : elements) {
            names.add(element.getName());
        }
        assertEquals(
            Arrays.asList(
                ITEM_FILTER_MODE,
                ITEM_BLACKLIST,
                REGISTRY_BLACKLIST,
                OREDICT_BLACKLIST,
                LIQUID_POLICY,
                FLUID_ALLOWLIST,
                FLUID_DENYLIST,
                BUOYANCY_LIFT,
                HORIZONTAL_DAMPING,
                MAX_UPWARD_SPEED,
                SURFACE_OFFSET,
                SERVER_LANGUAGE,
                DISABLE_UPDATE_CHECK),
            names);
    }

    @Test
    public void configCommentsArePersisted() throws Exception {
        File file = configFile();
        FIConfig.init(file);

        String contents = new String(
            java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(contents.contains("Metadata defaults to 0"));
        assertTrue(contents.contains("All metadata of the entry is included"));
        assertTrue(contents.contains("water_like"));
        assertTrue(contents.contains("denylist wins"));
        assertTrue(contents.contains("whitelist"));
        assertTrue(contents.contains("Maximum upward speed"));
    }

    @Test
    public void itemBlacklistParsesRegistryIdAndMetadata() throws Exception {
        writeConfig(new String[] { "{280}(0)", "{280}(3)" }, new int[0], new String[0], "en_us", false);
        FIConfig.init(configFile());

        assertEquals(
            2,
            FIConfig.blacklist.getItems()
                .size());
        FIConfig.FIConfigItem first = FIConfig.blacklist.getItems()
            .get(0);
        assertSame(MinecraftTestBootstrap.stick(), first.getItem());
        assertEquals(0, first.getMeta());
        assertEquals(280, first.getRegistry());
        assertEquals("{280}(0)", first.toString());
        assertEquals(5, first.getStack(5).stackSize);
    }

    @Test
    public void registryNameEntriesResolveAndSupportWildcardMetadata() throws Exception {
        writeConfig(new String[] { "minecraft:stick", "minecraft:stick:*" }, new int[0], new String[0], "en_us", false);
        FIConfig.init(configFile());

        assertEquals(
            2,
            FIConfig.blacklist.getItems()
                .size());
        FIConfig.FIConfigItem exact = FIConfig.blacklist.getItems()
            .get(0);
        FIConfig.FIConfigItem wildcard = FIConfig.blacklist.getItems()
            .get(1);
        assertSame(MinecraftTestBootstrap.stick(), exact.getItem());
        assertEquals(0, exact.getMeta());
        assertEquals(280, exact.getRegistry());
        assertEquals("minecraft:stick:0", exact.toString());
        assertTrue(wildcard.hasWildcardMeta());
        assertEquals(-1, wildcard.getMeta());
        assertEquals("minecraft:stick:*", wildcard.toString());

        assertTrue(FIConfig.blacklist.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 7)));
    }

    @Test
    public void whitespaceInsideAnEntryIsIgnored() throws Exception {
        writeConfig(new String[] { "{ 280 } ( 3 )" }, new int[0], new String[0], "en_us", false);
        FIConfig.init(configFile());

        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(
            280,
            FIConfig.blacklist.getItems()
                .get(0)
                .getRegistry());
        assertEquals(
            3,
            FIConfig.blacklist.getItems()
                .get(0)
                .getMeta());
    }

    @Test
    public void registryAndOreDictionaryBlacklistsAreParsed() throws Exception {
        writeConfig(new String[0], new int[] { 280, 4 }, new String[] { "ingotCopper", "dustTinyGold" }, "de_de", true);
        FIConfig.init(configFile());

        assertEquals(new HashSet<Integer>(Arrays.asList(280, 4)), FIConfig.blacklist.getRegistryItems());
        assertEquals(
            new HashSet<String>(Arrays.asList("ingotCopper", "dustTinyGold")),
            FIConfig.blacklist.getOreDict());
        assertEquals("de_de", FIConfig.serverLang);
        assertTrue(FIConfig.disableUpdateCheck);
    }

    @Test
    public void syncConfigFalseReadsTheInMemoryConfiguration() throws Exception {
        FIConfig.init(configFile());
        FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST, new String[] {})
            .set(new String[] { "{280}(1)" });

        FIConfig.syncConfig(false);

        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .get(0)
                .getMeta());
    }

    @Test
    public void equalsIsReflexiveSymmetricTypeSafeAndHashCompatible() {
        FIConfig.FIConfigItem stick0 = new FIConfig.FIConfigItem("{280}(0)");
        FIConfig.FIConfigItem stick0Again = new FIConfig.FIConfigItem("{280}(0)");
        FIConfig.FIConfigItem stick1 = new FIConfig.FIConfigItem("{280}(1)");

        assertEquals(stick0, stick0);
        assertEquals(stick0, stick0Again);
        assertEquals(stick0Again, stick0);
        assertEquals(stick0.hashCode(), stick0Again.hashCode());
        assertFalse(stick0.equals(stick1));
        assertFalse(stick0.equals(null));
        assertFalse(stick0.equals("not a config item"));
    }

    @Test
    public void oneMalformedEntryDoesNotDiscardTheRestOfTheConfig() throws Exception {
        writeConfig(
            new String[] { "this is not a valid entry", "{280}(0)", "{280}(2)" },
            new int[] { 280 },
            new String[] { "ingotCopper" },
            "de_de",
            true);

        FIConfig.init(configFile());

        assertEquals("de_de", FIConfig.serverLang);
        assertTrue(FIConfig.disableUpdateCheck);
        assertEquals(
            2,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .get(0)
                .getMeta());
        assertEquals(
            2,
            FIConfig.blacklist.getItems()
                .get(1)
                .getMeta());
        assertEquals(Collections.singleton(280), FIConfig.blacklist.getRegistryItems());
        assertEquals(Collections.singleton("ingotCopper"), FIConfig.blacklist.getOreDict());
    }

    @Test
    public void unknownItemIdIsRemovedAfterResolution() throws Exception {
        writeConfig(new String[] { "{99999}(0)" }, new int[0], new String[0], "en_us", false);

        FIConfig.init(configFile());
        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .size());

        FIConfig.resolveItems();

        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .size());
    }

    @Test
    public void missingMetadataDefaultsToZero() throws Exception {
        writeConfig(new String[] { "{280}" }, new int[0], new String[0], "en_us", false);

        FIConfig.init(configFile());

        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .get(0)
                .getMeta());
    }

    @Test
    public void allInvalidEntriesLeaveEmptyItemBlacklistsButKeepOtherProperties() throws Exception {
        writeConfig(
            new String[] { "garbage", "{99999}(0)", "{280}(" },
            new int[] { 280 },
            new String[] { "ingotCopper" },
            "de_de",
            true);

        FIConfig.init(configFile());
        FIConfig.resolveItems();

        assertNotNull(FIConfig.blacklist);
        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(Collections.singleton(280), FIConfig.blacklist.getRegistryItems());
        assertEquals(Collections.singleton("ingotCopper"), FIConfig.blacklist.getOreDict());
        assertEquals("de_de", FIConfig.serverLang);
        assertTrue(FIConfig.disableUpdateCheck);
    }

    @Test
    public void legacyUpdateCheckMessageKeyIsMigrated() throws Exception {
        File file = configFile();
        Configuration config = new Configuration(file);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, "Disable FloatingItems Update Checker Message", false)
            .set(true);
        config.save();

        FIConfig.init(file);

        assertTrue(FIConfig.disableUpdateCheck);
        assertTrue(
            FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, DISABLE_UPDATE_CHECK, false)
                .getBoolean(false));
        assertFalse(
            FIConfig.config.hasKey(FIReference.CONFIG_CATEGORY_UTIL, "Disable FloatingItems Update Checker Message"));
    }

    @Test
    public void backendFailureResetsToSafeDefaults() {
        FIConfig.blacklist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.singleton(280),
            Collections.singletonList("ingotCopper"));
        FIConfig.filterMode = ItemFilterMode.WHITELIST;
        FIConfig.liquidRules = new LiquidRules(
            LiquidPolicy.WATER_LIKE,
            Collections.singletonList("salt_water"),
            Collections.singletonList("oil"));
        FIConfig.buoyancy = new BuoyancySettings(0.2D, 0.5D, 0.3D, 0.1D);
        FIConfig.serverLang = "de_de";
        FIConfig.disableUpdateCheck = true;
        FIConfig.config = mock(Configuration.class);
        doThrow(new RuntimeException("backend failure")).when(FIConfig.config)
            .get(anyString(), anyString(), any(String[].class));

        FIConfig.syncConfig(false);

        assertNotNull(FIConfig.blacklist);
        assertEquals(
            0,
            FIConfig.blacklist.getItems()
                .size());
        assertEquals(
            0,
            FIConfig.blacklist.getRegistryItems()
                .size());
        assertEquals(
            0,
            FIConfig.blacklist.getOreDict()
                .size());
        assertEquals("en_us", FIConfig.serverLang);
        assertFalse(FIConfig.disableUpdateCheck);
        assertEquals(ItemFilterMode.BLACKLIST, FIConfig.filterMode);
        assertEquals(LiquidPolicy.WATER_LIKE, FIConfig.liquidRules.getPolicy());
        assertEquals(BuoyancySettings.DEFAULT_LIFT, FIConfig.buoyancy.getLift(), 1.0E-9D);
        assertTrue(
            FIConfig.liquidRules.getAllowlist()
                .isEmpty());
        assertTrue(
            FIConfig.liquidRules.getDenylist()
                .isEmpty());
    }

    @Test
    public void buoyancySettingsAreLoadedAndClamped() throws Exception {
        FIConfig.init(configFile());
        FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, BUOYANCY_LIFT, BuoyancySettings.DEFAULT_LIFT)
            .set(0.2D);
        FIConfig.config
            .get(FIReference.CONFIG_CATEGORY_UTIL, HORIZONTAL_DAMPING, BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING)
            .set(0.5D);
        FIConfig.config
            .get(FIReference.CONFIG_CATEGORY_UTIL, MAX_UPWARD_SPEED, BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED)
            .set(0.3D);
        FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, SURFACE_OFFSET, BuoyancySettings.DEFAULT_SURFACE_OFFSET)
            .set(0.1D);

        FIConfig.syncConfig(false);

        assertEquals(0.2D, FIConfig.buoyancy.getLift(), 1.0E-9D);
        assertEquals(0.5D, FIConfig.buoyancy.getHorizontalDamping(), 1.0E-9D);
        assertEquals(0.3D, FIConfig.buoyancy.getMaxUpwardSpeed(), 1.0E-9D);
        assertEquals(0.1D, FIConfig.buoyancy.getSurfaceOffset(), 1.0E-9D);
    }

    @Test
    public void whitelistModeIsLoaded() throws Exception {
        writeConfig(
            new String[] { "{280}(0)" },
            new int[0],
            new String[0],
            "whitelist",
            "water_like",
            new String[0],
            new String[0],
            "en_us",
            false);

        FIConfig.init(configFile());

        assertEquals(ItemFilterMode.WHITELIST, FIConfig.filterMode);
        assertTrue(FIConfig.blacklist.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 0)));
    }

    @Test
    public void liquidPolicyAndFluidListsAreLoaded() throws Exception {
        writeConfig(
            new String[0],
            new int[0],
            new String[0],
            "water_like",
            new String[] { " Salt_Water ", "" },
            new String[] { "OIL" },
            "en_us",
            false);

        FIConfig.init(configFile());

        assertEquals(LiquidPolicy.WATER_LIKE, FIConfig.liquidRules.getPolicy());
        assertEquals(Collections.singleton("salt_water"), FIConfig.liquidRules.getAllowlist());
        assertEquals(Collections.singleton("oil"), FIConfig.liquidRules.getDenylist());
    }

    @Test
    public void parserAcceptsOptionalMetadataAndWhitespace() {
        assertEquals(
            0,
            FIConfig.FIConfigItem.parse("{280}")
                .getMeta());
        assertEquals(
            3,
            FIConfig.FIConfigItem.parse("{ 280 } ( 3 )")
                .getMeta());
        assertEquals(
            280,
            FIConfig.FIConfigItem.parse("{280}")
                .getRegistry());

        FIConfig.FIConfigItem named = FIConfig.FIConfigItem.parse("minecraft:stick");
        assertSame(MinecraftTestBootstrap.stick(), named.getItem());
        assertEquals(0, named.getMeta());
        assertEquals("minecraft:stick:0", named.toString());
        assertEquals(280, named.getRegistry());
        assertEquals(
            3,
            FIConfig.FIConfigItem.parse("minecraft:stick:3")
                .getMeta());
        assertTrue(
            FIConfig.FIConfigItem.parse("minecraft:stick:*")
                .hasWildcardMeta());
        assertTrue(
            FIConfig.FIConfigItem.parse("minecraft:stick:-1")
                .hasWildcardMeta());
        assertTrue(
            FIConfig.FIConfigItem.parse("{280}(*)")
                .hasWildcardMeta());
    }

    @Test
    public void parserRejectsMalformedEntries() {
        assertNull(FIConfig.FIConfigItem.parse(null));
        assertNull(FIConfig.FIConfigItem.parse(""));
        assertNull(FIConfig.FIConfigItem.parse("   "));
        assertNull(FIConfig.FIConfigItem.parse("garbage"));
        assertNull(FIConfig.FIConfigItem.parse("{280"));
        assertNull(FIConfig.FIConfigItem.parse("{280}("));
        assertNull(FIConfig.FIConfigItem.parse("{280}()"));
        assertNull(FIConfig.FIConfigItem.parse("{280}(-1)"));
        assertNull(FIConfig.FIConfigItem.parse("{280}(abc)"));
        assertNull(FIConfig.FIConfigItem.parse("{280}(0)junk"));
        assertNull(FIConfig.FIConfigItem.parse("{abc}(0)"));
        assertNull(FIConfig.FIConfigItem.parse("minecraft"));
        assertNull(FIConfig.FIConfigItem.parse(":stick"));
        assertNull(FIConfig.FIConfigItem.parse("minecraft:"));
        assertNull(FIConfig.FIConfigItem.parse("minecraft:stick:"));
        assertNull(FIConfig.FIConfigItem.parse("minecraft:stick:abc"));
        assertNull(FIConfig.FIConfigItem.parse("minecraft:stick:0:1"));
    }

    @Test
    public void parserKeepsUnknownIdsForLateResolution() {
        FIConfig.FIConfigItem unresolved = FIConfig.FIConfigItem.parse("{99999}(0)");

        assertNotNull(unresolved);
        assertNull(unresolved.getItem());
        assertNull(
            unresolved.getStack()
                .getItem());
    }

    @Test
    public void resolveItemsRemovesOnlyUnresolvedEntries() throws Exception {
        writeConfig(new String[] { "{280}(0)", "{99999}(0)", "garbage" }, new int[0], new String[0], "en_us", false);

        FIConfig.init(configFile());
        assertEquals(
            2,
            FIConfig.blacklist.getItems()
                .size());

        FIConfig.resolveItems();

        assertEquals(
            1,
            FIConfig.blacklist.getItems()
                .size());
        assertSame(
            MinecraftTestBootstrap.stick(),
            FIConfig.blacklist.getItems()
                .get(0)
                .getItem());
    }

    @Test
    public void blacklistSnapshotIgnoresNullEntriesAndNormalizesOreNames() {
        FIConfig.Blacklist snapshot = new FIConfig.Blacklist(
            Arrays.asList(null, new FIConfig.FIConfigItem("{280}(0)")),
            null,
            Arrays.asList(" ingot_Copper-2 ", "", null));

        assertEquals(
            1,
            snapshot.getItems()
                .size());
        assertEquals(Collections.singleton("ingot_Copper-2"), snapshot.getOreDict());
        assertTrue(
            snapshot.getRegistryItems()
                .isEmpty());
    }

    @Test
    public void blacklistMatchesItemMetadataAndRegistryIndependently() {
        FIConfig.FIConfigItem stick0 = new FIConfig.FIConfigItem("{280}(0)");
        FIConfig.Blacklist itemOnly = new FIConfig.Blacklist(
            Collections.singletonList(stick0),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());
        assertTrue(itemOnly.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 0)));
        assertFalse(itemOnly.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 1)));

        FIConfig.Blacklist registryOnly = new FIConfig.Blacklist(
            Collections.<FIConfig.FIConfigItem>emptyList(),
            Collections.singleton(280),
            Collections.<String>emptyList());
        assertTrue(registryOnly.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 1)));
    }

    @Test
    public void blacklistDecisionCacheIsScopedToTheSnapshot() {
        FIConfig.Blacklist first = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());
        ItemStack stick0 = new ItemStack(MinecraftTestBootstrap.stick(), 1, 0);

        assertTrue(first.isBlacklisted(stick0));
        assertTrue(first.isBlacklisted(stick0));
        assertEquals(1, first.getCachedDecisionCount());

        assertFalse(first.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 1)));
        assertEquals(2, first.getCachedDecisionCount());

        FIConfig.Blacklist registryOnly = new FIConfig.Blacklist(
            Collections.<FIConfig.FIConfigItem>emptyList(),
            Collections.singleton(280),
            Collections.<String>emptyList());
        assertTrue(registryOnly.isBlacklisted(stick0));
        assertTrue(registryOnly.isBlacklisted(stick0));
        assertEquals(1, registryOnly.getCachedDecisionCount());

        FIConfig.Blacklist second = FIConfig.Blacklist.empty();
        assertFalse(second.isBlacklisted(stick0));
        assertEquals("empty snapshots should not grow a cache", 0, second.getCachedDecisionCount());
        assertTrue(first.isBlacklisted(stick0));
    }

    @Test
    public void topLevelSnapshotCarriesTheConfigDecision() {
        FIConfigSnapshot snapshot = new FIConfigSnapshot(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        assertTrue(snapshot.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 0)));
        assertFalse(snapshot.isBlacklisted(new ItemStack(MinecraftTestBootstrap.stick(), 1, 1)));
    }

    @Test
    public void blacklistSnapshotIsImmutable() {
        FIConfig.Blacklist snapshot = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.singleton(280),
            Collections.singletonList("ingotCopper"));

        try {
            snapshot.getItems()
                .clear();
            throw new AssertionError("items list is mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        try {
            snapshot.getRegistryItems()
                .clear();
            throw new AssertionError("registry set is mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        try {
            snapshot.getOreDict()
                .clear();
            throw new AssertionError("ore dictionary set is mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void malformedOrNullStacksAreNotBlacklisted() {
        FIConfig.Blacklist snapshot = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            null,
            null);

        assertFalse(snapshot.isBlacklisted(null));
        assertFalse(snapshot.isBlacklisted(new ItemStack((Item) null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsInvalidEntry() {
        new FIConfig.FIConfigItem("garbage");
    }

    private File configFile() throws Exception {
        return new File(temporaryFolder.getRoot(), FIReference.FLOATINGITEMS_CONFIG_FILE);
    }

    private void writeConfig(String[] items, int[] registry, String[] oreDict, String language, boolean disableUpdate)
        throws Exception {
        writeConfig(items, registry, oreDict, "water", new String[0], new String[0], language, disableUpdate);
    }

    private void writeConfig(String[] items, int[] registry, String[] oreDict, String liquidPolicy,
        String[] fluidAllowlist, String[] fluidDenylist, String language, boolean disableUpdate) throws Exception {
        writeConfig(
            items,
            registry,
            oreDict,
            "blacklist",
            liquidPolicy,
            fluidAllowlist,
            fluidDenylist,
            language,
            disableUpdate);
    }

    private void writeConfig(String[] items, int[] registry, String[] oreDict, String itemFilterMode,
        String liquidPolicy, String[] fluidAllowlist, String[] fluidDenylist, String language, boolean disableUpdate)
        throws Exception {
        File file = configFile();
        Configuration config = new Configuration(file);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_FILTER_MODE, "blacklist")
            .set(itemFilterMode);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST, new String[] {})
            .set(items);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, REGISTRY_BLACKLIST, new int[] {})
            .set(registry);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, OREDICT_BLACKLIST, new String[] {})
            .set(oreDict);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, LIQUID_POLICY, "water")
            .set(liquidPolicy);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FLUID_ALLOWLIST, new String[] {})
            .set(fluidAllowlist);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FLUID_DENYLIST, new String[] {})
            .set(fluidDenylist);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, SERVER_LANGUAGE, "en_us")
            .set(language);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, DISABLE_UPDATE_CHECK, false)
            .set(disableUpdate);
        config.save();
        assertTrue(file.exists());
    }

}
