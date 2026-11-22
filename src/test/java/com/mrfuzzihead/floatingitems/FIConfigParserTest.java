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
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.LIQUID_POLICY_KEY, "water")
            .set("water_like");
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLUID_ALLOWLIST_KEY, new String[] {})
            .set(new String[] { "salt_water" });
        config.get(FIReference.CONFIG_CATEGORY_UTIL, FIConfigParser.FLUID_DENYLIST_KEY, new String[] {})
            .set(new String[] { "oil" });
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
            Arrays.asList(
                FIConfigParser.ITEM_FILTER_MODE_KEY,
                FIConfigParser.ITEM_BLACKLIST_KEY,
                FIConfigParser.REGISTRY_BLACKLIST_KEY,
                FIConfigParser.OREDICT_BLACKLIST_KEY,
                FIConfigParser.LIQUID_POLICY_KEY,
                FIConfigParser.FLUID_ALLOWLIST_KEY,
                FIConfigParser.FLUID_DENYLIST_KEY,
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
