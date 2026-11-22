package com.mrfuzzihead.floatingitems;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.floatingitems.api.BlacklistEntry;

import cpw.mods.fml.client.config.IConfigElement;

public class FIConfig {

    private static final Logger LOGGER = LogManager.getLogger(FIReference.MOD_ID);

    static Configuration config;

    /**
     * Immutable view of the active blacklists. Replaced atomically by {@link #syncConfig(boolean)} and
     * {@link #resolveItems()}, so the item tick never observes a half-updated set of fields.
     */
    public static volatile FIConfigSnapshot blacklist = FIConfigSnapshot.empty();

    /** Whether {@link #blacklist} is an exclusion list or an allow-only list. */
    public static volatile ItemFilterMode filterMode = ItemFilterMode.BLACKLIST;

    /** Immutable fluid policy published alongside the blacklist snapshot. */
    public static volatile LiquidRules liquidRules = LiquidRules.defaults();

    public static String serverLang;

    public static boolean disableUpdateCheck;

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
        try {
            FIConfigParser.ParsedConfig parsed = FIConfigParser.parse(config, load);

            List<FIConfigItem> items = new ArrayList<FIConfigItem>();
            for (BlacklistEntry entry : parsed.getItems()) {
                items.add(new FIConfigItem(entry));
            }
            FIConfigSnapshot snapshot = new FIConfigSnapshot(items, parsed.getRegistryItems(), parsed.getOreDict());

            config.setCategoryPropertyOrder(FIReference.CONFIG_CATEGORY_UTIL, parsed.getPropertyOrder());
            // Saving unconditionally also persists comments/order changes, which Configuration does not mark dirty.
            config.save();

            blacklist = snapshot;
            filterMode = parsed.getItemFilterMode();
            liquidRules = parsed.getLiquidRules();
            serverLang = parsed.getServerLang();
            disableUpdateCheck = parsed.isUpdateCheckDisabled();
        } catch (final Exception e) {
            LOGGER.error("Problem loading FloatingItems config (FloatingItems.conf)", e);
            // Do not leave half-updated static state behind. If the config backend itself fails, run with safe
            // defaults until the next successful sync.
            resetToDefaults();
        }
    }

    private static void resetToDefaults() {
        blacklist = FIConfigSnapshot.empty();
        filterMode = ItemFilterMode.BLACKLIST;
        liquidRules = LiquidRules.defaults();
        serverLang = "en_us";
        disableUpdateCheck = false;
    }

    /**
     * Resolves blacklist entries against the item registry once all mods have registered their items. This runs in
     * postInit, not during the preInit config load, so a blacklist entry for another mod's item is not discarded
     * just because that mod's preInit has not run yet. Unknown ids are logged and removed here.
     */
    public static void resolveItems() {

        FIConfigSnapshot current = blacklist;
        if (current == null) {
            blacklist = FIConfigSnapshot.empty();
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

        blacklist = new FIConfigSnapshot(resolved, current.getRegistryItems(), current.getOreDict());
    }

    public static List<IConfigElement> getConfigElements() {
        if (config == null) {
            return Collections.emptyList();
        }
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        list.addAll(new ConfigElement(config.getCategory(FIReference.CONFIG_CATEGORY_UTIL)).getChildElements());
        return list;
    }

    /**
     * Backwards-compatible wrapper around {@link FIConfigSnapshot}. New code should use the snapshot type or the
     * read-only API.
     */
    public static final class Blacklist extends FIConfigSnapshot {

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
