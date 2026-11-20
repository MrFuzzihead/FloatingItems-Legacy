package net.coolsimulations.FloatingItems;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;

import net.coolsimulations.FloatingItems.testutil.MinecraftTestBootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cpw.mods.fml.client.config.IConfigElement;

/**
 * Tests for the config file contract and for {@link FIConfig.FIConfigItem}.
 *
 * <p>
 * The config parser is the mod's only real input surface, and the mixin runs it on every item every tick. These
 * tests pin the on-disk format ({@code {id}(meta)}), the default values, the exact property names shown in the GUI,
 * and the failure modes that the analysis calls out.
 * </p>
 */
public class FIConfigTest {

    private static final String ITEM_BLACKLIST = "Blacklist of Items that should not Float";
    private static final String REGISTRY_BLACKLIST = "Blacklist of All Items regardless of Metadata that should not Float";
    private static final String OREDICT_BLACKLIST = "Blacklist of OreDict Entries that should not Float";
    private static final String SERVER_LANGUAGE = "Default Server language";
    private static final String DISABLE_UPDATE_CHECK = "Disable FloatingItems Update Checker Message";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Before
    public void resetConfigState() {
        FIConfig.blacklistItems = null;
        FIConfig.blacklistRegistryItems = null;
        FIConfig.blacklistOreDict = null;
        FIConfig.serverLang = null;
        FIConfig.disableUpdateCheck = false;
        FIConfig.config = null;
    }

    @After
    public void clearConfigState() {
        FIConfig.blacklistItems = null;
        FIConfig.blacklistRegistryItems = null;
        FIConfig.blacklistOreDict = null;
        FIConfig.serverLang = null;
        FIConfig.disableUpdateCheck = false;
        FIConfig.config = null;
    }

    @Test
    public void defaultConfigHasEmptyBlacklistsAndExpectedDefaults() throws Exception {
        File file = configFile();
        FIConfig.init(file);

        assertTrue(file.exists());
        assertNotNull(FIConfig.blacklistItems);
        assertEquals(0, FIConfig.blacklistItems.length);
        assertNotNull(FIConfig.blacklistRegistryItems);
        assertEquals(0, FIConfig.blacklistRegistryItems.length);
        assertNotNull(FIConfig.blacklistOreDict);
        assertEquals(0, FIConfig.blacklistOreDict.length);
        assertEquals("en_us", FIConfig.serverLang);
        assertFalse(FIConfig.disableUpdateCheck);
    }

    @Test
    public void allPropertiesAreExposedToTheConfigGui() throws Exception {
        FIConfig.init(configFile());

        List<IConfigElement> elements = FIConfig.getConfigElements();
        assertEquals(5, elements.size());

        java.util.Set<String> names = new java.util.HashSet<String>();
        for (IConfigElement element : elements) {
            names.add(element.getName());
        }
        // The order is not asserted: FIConfig builds `propOrder` but never calls setCategoryPropertyOrder, so the
        // GUI receives the Configuration's default (alphabetical) order. See CODEBASE_ANALYSIS.md D-03.
        assertTrue(names.contains(ITEM_BLACKLIST));
        assertTrue(names.contains(REGISTRY_BLACKLIST));
        assertTrue(names.contains(OREDICT_BLACKLIST));
        assertTrue(names.contains(SERVER_LANGUAGE));
        assertTrue(names.contains(DISABLE_UPDATE_CHECK));
    }

    @Test
    public void itemBlacklistParsesRegistryIdAndMetadata() throws Exception {
        writeConfig(new String[] { "{280}(0)", "{280}(3)" }, new int[0], new String[0], "en_us", false);
        FIConfig.init(configFile());

        assertEquals(2, FIConfig.blacklistItems.length);
        FIConfig.FIConfigItem first = FIConfig.blacklistItems[0];
        assertSame(MinecraftTestBootstrap.stick(), first.getItem());
        assertEquals(0, first.getMeta());
        assertEquals(280, first.getRegistry());
        assertEquals("{280}(0)", first.toString());
        assertEquals(5, first.getStack(5).stackSize);
    }

    @Test
    public void whitespaceInsideAnEntryIsIgnored() throws Exception {
        writeConfig(new String[] { "{ 280 } ( 3 )" }, new int[0], new String[0], "en_us", false);
        FIConfig.init(configFile());

        assertEquals(1, FIConfig.blacklistItems.length);
        assertEquals(280, FIConfig.blacklistItems[0].getRegistry());
        assertEquals(3, FIConfig.blacklistItems[0].getMeta());
    }

    @Test
    public void registryAndOreDictionaryBlacklistsAreParsed() throws Exception {
        writeConfig(new String[0], new int[] { 280, 4 }, new String[] { "ingotCopper", "dustTinyGold" }, "de_de", true);
        FIConfig.init(configFile());

        assertArrayEquals(new int[] { 280, 4 }, FIConfig.blacklistRegistryItems);
        assertArrayEquals(new String[] { "ingotCopper", "dustTinyGold" }, FIConfig.blacklistOreDict);
        assertEquals("de_de", FIConfig.serverLang);
        assertTrue(FIConfig.disableUpdateCheck);
    }

    @Test
    public void syncConfigFalseReadsTheInMemoryConfiguration() throws Exception {
        FIConfig.init(configFile());
        FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST, new String[] {})
            .set(new String[] { "{280}(1)" });

        FIConfig.syncConfig(false);

        assertEquals(1, FIConfig.blacklistItems.length);
        assertEquals(1, FIConfig.blacklistItems[0].getMeta());
    }

    @Test
    public void forceSaveWritesPendingValues() throws Exception {
        File file = configFile();
        FIConfig.init(file);
        FIConfig.config.get(FIReference.CONFIG_CATEGORY_UTIL, SERVER_LANGUAGE, "en_us")
            .set("fr_fr");

        FIConfig.forceSave();

        String contents = read(file);
        assertTrue(contents.contains("fr_fr"));
    }

    @Test
    public void itemStackConstructorKeepsTheItemAndMetadata() {
        ItemStack stack = mock(ItemStack.class);
        Item item = MinecraftTestBootstrap.stick();
        when(stack.getItem()).thenReturn(item);
        when(stack.getItemDamage()).thenReturn(3);

        FIConfig.FIConfigItem configItem = new FIConfig.FIConfigItem(stack);

        assertSame(item, configItem.getItem());
        assertEquals(3, configItem.getMeta());
        assertEquals(MinecraftTestBootstrap.STICK_ID, configItem.getRegistry());
        assertSame(
            item,
            configItem.getStack(2)
                .getItem());
        assertEquals(2, configItem.getStack(2).stackSize);
    }

    @Test
    public void equalsIsReflexiveSymmetricAndTypeSafe() {
        FIConfig.FIConfigItem stick0 = new FIConfig.FIConfigItem("{280}(0)");
        FIConfig.FIConfigItem stick0Again = new FIConfig.FIConfigItem("{280}(0)");
        FIConfig.FIConfigItem stick1 = new FIConfig.FIConfigItem("{280}(1)");

        assertEquals(stick0, stick0);
        assertEquals(stick0, stick0Again);
        assertEquals(stick0Again, stick0);
        assertFalse(stick0.equals(stick1));
        assertFalse(stick0.equals(null));
        assertFalse(stick0.equals("not a config item"));
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-05: FIConfigItem overrides equals but not hashCode")
    public void equalItemsHaveEqualHashCodes() {
        FIConfig.FIConfigItem first = new FIConfig.FIConfigItem("{280}(0)");
        FIConfig.FIConfigItem second = new FIConfig.FIConfigItem("{280}(0)");
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-01: one malformed entry aborts the whole config load")
    public void oneMalformedEntryDoesNotDiscardTheRestOfTheConfig() throws Exception {
        writeConfig(
            new String[] { "{280}(0)", "this is not a valid entry" },
            new int[] { 280 },
            new String[] { "ingotCopper" },
            "de_de",
            true);

        FIConfig.init(configFile());

        assertEquals("de_de", FIConfig.serverLang);
        assertTrue(FIConfig.disableUpdateCheck);
        assertEquals(1, FIConfig.blacklistItems.length);
        assertArrayEquals(new int[] { 280 }, FIConfig.blacklistRegistryItems);
    }

    @Test
    public void malformedEntryCurrentlyAbortsTheWholeConfig() throws Exception {
        // Characterization of B-01: the entry parser throws inside syncConfig and the catch block abandons every
        // later property. This test passes today and is expected to fail once the parser is made fault-tolerant.
        writeConfig(
            new String[] { "{280}(0)", "this is not a valid entry" },
            new int[] { 280 },
            new String[] { "ingotCopper" },
            "de_de",
            true);

        FIConfig.init(configFile());

        assertNull(FIConfig.blacklistRegistryItems);
        assertNull(FIConfig.blacklistOreDict);
        assertNull(FIConfig.serverLang);
        assertFalse(FIConfig.disableUpdateCheck);
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-02: an unknown item id creates a FIConfigItem whose item is null")
    public void unknownItemIdIsRejectedInsteadOfCreatingANullItemEntry() throws Exception {
        writeConfig(new String[] { "{99999}(0)" }, new int[0], new String[0], "en_us", false);

        FIConfig.init(configFile());

        assertEquals(0, FIConfig.blacklistItems.length);
    }

    @Test
    @Ignore("Known bug CODEBASE_ANALYSIS.md B-03: an entry without (meta) throws NumberFormatException and aborts the config")
    public void missingMetadataIsRejectedWithAUsableDefault() throws Exception {
        writeConfig(new String[] { "{280}" }, new int[0], new String[0], "en_us", false);

        FIConfig.init(configFile());

        assertEquals(1, FIConfig.blacklistItems.length);
        assertEquals(0, FIConfig.blacklistItems[0].getMeta());
    }

    @Test
    public void unknownItemIdCurrentlyProducesANullItem() throws Exception {
        // Characterization test: this is the precondition for the NPE described in B-02. It is deliberately not
        // marked @Ignore so the suite records that the parser accepts the entry today.
        writeConfig(new String[] { "{99999}(0)" }, new int[0], new String[0], "en_us", false);
        FIConfig.init(configFile());

        assertEquals(1, FIConfig.blacklistItems.length);
        assertNull(FIConfig.blacklistItems[0].getItem());
    }

    private File configFile() throws Exception {
        return new File(temporaryFolder.getRoot(), FIReference.FLOATINGITEMS_CONFIG_FILE);
    }

    private void writeConfig(String[] items, int[] registry, String[] oreDict, String language, boolean disableUpdate)
        throws Exception {
        File file = configFile();
        Configuration config = new Configuration(file);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST, new String[] {})
            .set(items);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, REGISTRY_BLACKLIST, new int[] {})
            .set(registry);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, OREDICT_BLACKLIST, new String[] {})
            .set(oreDict);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, SERVER_LANGUAGE, "en_us")
            .set(language);
        config.get(FIReference.CONFIG_CATEGORY_UTIL, DISABLE_UPDATE_CHECK, false)
            .set(disableUpdate);
        config.save();
        assertTrue(file.exists());
    }

    private static String read(File file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

}
