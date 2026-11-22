package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mrfuzzihead.floatingitems.api.BlacklistEntry;
import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

/**
 * Direct tests for the config parser/validator split out of {@link FIConfig}.
 */
public class FIConfigParserTest {

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesValidEntriesAndSkipsMalformedOnesIndependently() throws Exception {
        Configuration config = configuration();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.ITEM_FILTER_MODE_KEY, "blacklist")
            .set("whitelist");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.ITEM_BLACKLIST_KEY, new String[] {})
            .set(new String[] { "garbage", "{280}(0)", "minecraft:stick:3", "{280}(" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.REGISTRY_BLACKLIST_KEY, new int[] {})
            .set(new int[] { 280, 4 });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.OREDICT_BLACKLIST_KEY, new String[] {})
            .set(new String[] { "ingotCopper", "dustTinyGold" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.DIMENSION_FILTER_MODE_KEY, "all")
            .set("whitelist");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLOATING_DIMENSIONS_KEY, new int[] {})
            .set(new int[] { 0, -1 });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.BIOME_FILTER_MODE_KEY, "all")
            .set("blacklist");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLOATING_BIOMES_KEY, new String[] {})
            .set(new String[] { "Plains", "Ocean" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.MOD_FILTER_MODE_KEY, "all")
            .set("whitelist");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLOATING_MOD_IDS_KEY, new String[] {})
            .set(new String[] { "minecraft", "thermalexpansion" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.LIQUID_POLICY_KEY, "water")
            .set("water_like");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLUID_ALLOWLIST_KEY, new String[] {})
            .set(new String[] { "salt_water" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLUID_DENYLIST_KEY, new String[] {})
            .set(new String[] { "oil" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.BUOYANCY_LIFT_KEY, BuoyancySettings.DEFAULT_LIFT)
            .set(0.2D);
        config
            .get(
                FIReference.CONFIG_CATEGORY_UTIL,
                FIConfigParser.HORIZONTAL_DAMPING_KEY,
                BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING)
            .set(0.5D);
        config
            .get(
                FIReference.CONFIG_CATEGORY_UTIL,
                FIConfigParser.MAX_UPWARD_SPEED_KEY,
                BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED)
            .set(0.3D);
        config
            .get(
                FIReference.CONFIG_CATEGORY_UTIL,
                FIConfigParser.SURFACE_OFFSET_KEY,
                BuoyancySettings.DEFAULT_SURFACE_OFFSET)
            .set(0.1D);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.SERVER_LANGUAGE_KEY, "en_us")
            .set("de_de");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.UPDATE_CHECK_KEY, false)
            .set(true);
        config.save();

        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, true);

        assertEquals(
            2,
            parsed.getItems()
                .size());
        assertEquals(
            0,
            parsed.getItems()
                .get(0)
                .getMeta());
        assertEquals(
            3,
            parsed.getItems()
                .get(1)
                .getMeta());
        assertEquals(ItemFilterMode.WHITELIST, parsed.getItemFilterMode());
        assertTrue(
            parsed.getScopeRules()
                .allows(0, "Desert", "minecraft"));
        assertFalse(
            parsed.getScopeRules()
                .allows(2, "Desert", "minecraft"));
        assertFalse(
            parsed.getScopeRules()
                .allows(0, "Plains", "minecraft"));
        assertTrue(
            parsed.getScopeRules()
                .allows(0, "Desert", "minecraft"));
        assertEquals("de_de", parsed.getServerLang());
        assertTrue(parsed.isUpdateCheckDisabled());
        assertEquals(new java.util.HashSet<Integer>(Arrays.asList(280, 4)), parsed.getRegistryItems());
        assertEquals(Arrays.asList("ingotCopper", "dustTinyGold"), parsed.getOreDict());
        assertEquals(
            LiquidPolicy.WATER_LIKE,
            parsed.getLiquidRules()
                .getPolicy());
        assertEquals(
            java.util.Collections.singleton("salt_water"),
            parsed.getLiquidRules()
                .getAllowlist());
        assertEquals(
            java.util.Collections.singleton("oil"),
            parsed.getLiquidRules()
                .getDenylist());
        assertEquals(
            0.2D,
            parsed.getBuoyancy()
                .getLift(),
            1.0E-9D);
        assertEquals(
            0.5D,
            parsed.getBuoyancy()
                .getHorizontalDamping(),
            1.0E-9D);
        assertEquals(
            0.3D,
            parsed.getBuoyancy()
                .getMaxUpwardSpeed(),
            1.0E-9D);
        assertEquals(
            0.1D,
            parsed.getBuoyancy()
                .getSurfaceOffset(),
            1.0E-9D);
        assertEquals(
            Arrays.asList(
                FIConfigParser.ITEM_FILTER_MODE_KEY,
                FIConfigParser.ITEM_BLACKLIST_KEY,
                FIConfigParser.REGISTRY_BLACKLIST_KEY,
                FIConfigParser.OREDICT_BLACKLIST_KEY,
                FIConfigParser.DIMENSION_FILTER_MODE_KEY,
                FIConfigParser.FLOATING_DIMENSIONS_KEY,
                FIConfigParser.BIOME_FILTER_MODE_KEY,
                FIConfigParser.FLOATING_BIOMES_KEY,
                FIConfigParser.MOD_FILTER_MODE_KEY,
                FIConfigParser.FLOATING_MOD_IDS_KEY,
                FIConfigParser.LIQUID_POLICY_KEY,
                FIConfigParser.FLUID_ALLOWLIST_KEY,
                FIConfigParser.FLUID_DENYLIST_KEY,
                FIConfigParser.BUOYANCY_LIFT_KEY,
                FIConfigParser.HORIZONTAL_DAMPING_KEY,
                FIConfigParser.MAX_UPWARD_SPEED_KEY,
                FIConfigParser.SURFACE_OFFSET_KEY,
                FIConfigParser.SERVER_LANGUAGE_KEY,
                FIConfigParser.UPDATE_CHECK_KEY),
            parsed.getPropertyOrder());
    }

    @Test
    public void usesSafeDefaultsForAnEmptyConfig() throws Exception {
        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(configuration(), true);

        assertTrue(
            parsed.getItems()
                .isEmpty());
        assertTrue(
            parsed.getRegistryItems()
                .isEmpty());
        assertTrue(
            parsed.getOreDict()
                .isEmpty());
        assertEquals(ItemFilterMode.BLACKLIST, parsed.getItemFilterMode());
        assertTrue(
            parsed.getScopeRules()
                .allows(0, "Plains", "minecraft"));
        assertEquals("en_us", parsed.getServerLang());
        assertFalse(parsed.isUpdateCheckDisabled());
        assertEquals(
            LiquidPolicy.WATER_LIKE,
            parsed.getLiquidRules()
                .getPolicy());
        assertTrue(
            parsed.getLiquidRules()
                .getAllowlist()
                .isEmpty());
        assertTrue(
            parsed.getLiquidRules()
                .getDenylist()
                .isEmpty());
        assertEquals(
            BuoyancySettings.DEFAULT_LIFT,
            parsed.getBuoyancy()
                .getLift(),
            1.0E-9D);
        assertEquals(
            BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING,
            parsed.getBuoyancy()
                .getHorizontalDamping(),
            1.0E-9D);
        assertEquals(
            BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED,
            parsed.getBuoyancy()
                .getMaxUpwardSpeed(),
            1.0E-9D);
        assertEquals(
            BuoyancySettings.DEFAULT_SURFACE_OFFSET,
            parsed.getBuoyancy()
                .getSurfaceOffset(),
            1.0E-9D);
    }

    @Test
    public void invalidItemFilterModeFallsBackToBlacklist() throws Exception {
        Configuration config = configuration();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.ITEM_FILTER_MODE_KEY, "blacklist")
            .set("not_a_mode");
        config.save();

        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, true);

        assertEquals(ItemFilterMode.BLACKLIST, parsed.getItemFilterMode());
    }

    @Test
    public void invalidScopeModeFallsBackToAll() throws Exception {
        Configuration config = configuration();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.DIMENSION_FILTER_MODE_KEY, "all")
            .set("not_a_mode");
        config.save();

        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, true);

        assertEquals(
            ScopeMode.ALL,
            parsed.getScopeRules()
                .getDimensionMode());
    }

    @Test
    public void invalidLiquidPolicyFallsBackToWaterLike() throws Exception {
        Configuration config = configuration();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.LIQUID_POLICY_KEY, "water")
            .set("not_a_policy");
        config.save();

        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, true);

        assertEquals(
            LiquidPolicy.WATER_LIKE,
            parsed.getLiquidRules()
                .getPolicy());
    }

    @Test
    public void keepsUnknownEntriesForLateResolution() throws Exception {
        Configuration config = configuration();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.ITEM_BLACKLIST_KEY, new String[] {})
            .set(new String[] { "{99999}(0)" });
        config.save();

        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, true);

        assertEquals(
            1,
            parsed.getItems()
                .size());
        BlacklistEntry entry = parsed.getItems()
            .get(0);
        assertNotNull(entry);
        assertNull(entry.getItem());
    }

    @Test
    public void migratesTheLegacyUpdateCheckKeyAndPersistsTheNewOne() throws Exception {
        Configuration config = configuration();
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.LEGACY_UPDATE_CHECK_KEY, false)
            .set(true);
        config.save();

        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, true);

        assertTrue(parsed.isUpdateCheckDisabled());
        assertTrue(
            config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.UPDATE_CHECK_KEY, false)
                .getBoolean(false));
        assertFalse(config.hasKey(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.LEGACY_UPDATE_CHECK_KEY));
    }

    @Test
    public void parsedResultIsImmutable() throws Exception {
        FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(configuration(), true);

        assertImmutable(parsed.getItems());
        assertImmutable(parsed.getRegistryItems());
        assertImmutable(parsed.getOreDict());
        assertImmutable(
            parsed.getLiquidRules()
                .getAllowlist());
        assertImmutable(
            parsed.getLiquidRules()
                .getDenylist());
        assertImmutable(parsed.getPropertyOrder());
    }

    private Configuration configuration() throws Exception {
        File file = new File(temporaryFolder.getRoot(), FIReference.FLOATINGITEMS_CONFIG_FILE);
        return new Configuration(file);
    }

    private static void assertImmutable(java.util.Collection<?> collection) {
        try {
            collection.clear();
            throw new AssertionError("collection is mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
