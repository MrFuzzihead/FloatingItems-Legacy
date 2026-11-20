package net.coolsimulations.floatingitems;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.coolsimulations.floatingitems.api.BlacklistEntry;
import net.coolsimulations.floatingitems.api.BlacklistSnapshot;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.client.config.IConfigElement;

public class FIConfig {

    private static final Logger LOGGER = LogManager.getLogger(FIReference.MOD_ID);

    static Configuration config;

    /**
     * Immutable view of the active blacklists. Replaced atomically by {@link #syncConfig(boolean)} and
     * {@link #resolveItems()}, so the item tick never observes a half-updated set of fields.
     */
    public static volatile Blacklist blacklist = Blacklist.empty();

    public static String serverLang;

    public static boolean disableUpdateCheck;

    private static final String UPDATE_CHECK_KEY = "Disable FloatingItems Update Check";
    private static final String LEGACY_UPDATE_CHECK_KEY = "Disable FloatingItems Update Checker Message";

    public static void init(File file) {
        config = new Configuration(file);
        syncConfig(true);
    }

    /**
     * @deprecated config sync saves itself; retained for addon source/binary compatibility
     */
    @Deprecated
    public static void forceSave() {
        if (config != null) {
            config.save();
        }
    }

    public static void syncConfig(boolean load) {
        List<String> propOrder = new ArrayList<String>();

        try {
            Property prop;

            if (!config.isChild) {
                if (load) {
                    config.load();
                }
            }

            prop = config
                .get(FIReference.CONFIG_CATEGORY_UTIL, "Blacklist of Items that should not Float", new String[] {});
            prop.comment = "Format: {itemId}(metadata) or namespace:name[:metadata|:*]. Metadata defaults to 0; * means any metadata. Examples: {17}(1), minecraft:log:1, minecraft:stick:*";

            List<FIConfigItem> itemBlacklist = new ArrayList<FIConfigItem>();
            for (String entry : prop.getStringList()) {
                FIConfigItem parsed = FIConfigItem.parse(entry);
                if (parsed == null) {
                    LOGGER.warn("Ignoring invalid item blacklist entry: {}", entry);
                } else {
                    itemBlacklist.add(parsed);
                }
            }
            propOrder.add(prop.getName());

            prop = config.get(
                FIReference.CONFIG_CATEGORY_UTIL,
                "Blacklist of All Items regardless of Metadata that should not Float",
                new int[] {});
            prop.comment = "Format: Item ID. Example of Cobblestone: 4";
            Set<Integer> registryBlacklist = new HashSet<Integer>();
            for (int id : prop.getIntList()) {
                registryBlacklist.add(id);
            }
            propOrder.add(prop.getName());

            prop = config.get(
                FIReference.CONFIG_CATEGORY_UTIL,
                "Blacklist of OreDict Entries that should not Float",
                new String[] {});
            prop.comment = "Format: OreDict entry. All metadata of the entry is blacklisted. Example: ingotCopper";
            List<String> oreBlacklist = Arrays.asList(prop.getStringList());
            propOrder.add(prop.getName());

            prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, "Default Server language", "en_us");
            prop.comment = "Server language codes are based off the Minecraft .lang files. See https://minecraft.gamepedia.com/Language for list.";
            serverLang = prop.getString();
            propOrder.add(prop.getName());

            boolean updateCheckDisabled = false;
            if (config.hasKey(FIReference.CONFIG_CATEGORY_UTIL, LEGACY_UPDATE_CHECK_KEY)) {
                if (!config.hasKey(FIReference.CONFIG_CATEGORY_UTIL, UPDATE_CHECK_KEY)) {
                    updateCheckDisabled = config.get(FIReference.CONFIG_CATEGORY_UTIL, LEGACY_UPDATE_CHECK_KEY, false)
                        .getBoolean(false);
                }
                // Remove the old "hide the message" key so the GUI does not show two update-check options and
                // existing users keep their preference.
                config.getCategory(FIReference.CONFIG_CATEGORY_UTIL)
                    .remove(LEGACY_UPDATE_CHECK_KEY);
            }

            prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, UPDATE_CHECK_KEY, updateCheckDisabled);
            prop.comment = "Disable the FloatingItems update check entirely (no network request and no message).";
            disableUpdateCheck = prop.getBoolean(updateCheckDisabled);
            propOrder.add(prop.getName());

            blacklist = new Blacklist(itemBlacklist, registryBlacklist, oreBlacklist);
            config.setCategoryPropertyOrder(FIReference.CONFIG_CATEGORY_UTIL, propOrder);
            // Saving unconditionally also persists comments/order changes, which Configuration does not mark dirty.
            config.save();

        } catch (final Exception e) {
            LOGGER.error("Problem loading FloatingItems config (FloatingItems.conf)", e);
            // Do not leave half-updated static state behind. If the config backend itself fails, run with safe
            // defaults until the next successful sync.
            blacklist = Blacklist.empty();
            serverLang = "en_us";
            disableUpdateCheck = false;
        }
    }

    /**
     * Resolves blacklist entries against the item registry once all mods have registered their items. This runs in
     * postInit, not during the preInit config load, so a blacklist entry for another mod's item is not discarded
     * just because that mod's preInit has not run yet. Unknown ids are logged and removed here.
     */
    public static void resolveItems() {

        Blacklist current = blacklist;
        if (current == null) {
            blacklist = Blacklist.empty();
            return;
        }

        List<FIConfigItem> resolved = new ArrayList<FIConfigItem>();
        for (FIConfigItem item : current.getItems()) {
            if (item == null) {
                continue;
            }
            if (item.getItem() == null) {
                LOGGER.warn("Ignoring blacklist entry for unregistered item: {}", item);
            } else {
                resolved.add(item);
            }
        }

        blacklist = new Blacklist(resolved, current.getRegistryItems(), current.getOreDict());
    }

    public static List<IConfigElement> getConfigElements() {
        if (config == null) {
            return Collections.emptyList();
        }
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        list.addAll(new ConfigElement(config.getCategory(FIReference.CONFIG_CATEGORY_UTIL)).getChildElements());
        return list;
    }

    public static final class Blacklist extends BlacklistSnapshot<FIConfigItem> {

        private static final Blacklist EMPTY = new Blacklist(
            Collections.<FIConfigItem>emptyList(),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        public Blacklist(Collection<FIConfigItem> items, Collection<Integer> registryItems,
            Collection<String> oreDict) {
            super(items, registryItems, oreDict);
        }

        public static Blacklist empty() {
            return EMPTY;
        }
    }

    /**
     * Backwards-compatible concrete entry type. New addon code should use {@link BlacklistEntry}.
     */
    public static final class FIConfigItem extends BlacklistEntry {

        FIConfigItem(String entry) {
            super(parseOrThrow(entry));
        }

        private FIConfigItem(BlacklistEntry entry) {
            super(entry);
        }

        private static BlacklistEntry parseOrThrow(String entry) {
            BlacklistEntry parsed = BlacklistEntry.parse(entry);
            if (parsed == null) {
                throw new IllegalArgumentException("Invalid FloatingItems blacklist entry: " + entry);
            }
            return parsed;
        }

        public static FIConfigItem parse(String entry) {
            BlacklistEntry parsed = BlacklistEntry.parse(entry);
            return parsed == null ? null : new FIConfigItem(parsed);
        }
    }
}
