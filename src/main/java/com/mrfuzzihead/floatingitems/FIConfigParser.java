package com.mrfuzzihead.floatingitems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrfuzzihead.floatingitems.api.BlacklistEntry;

/**
 * Reads and validates the on-disk config values. This class deliberately does not publish any global state; the
 * {@link FIConfig} holder applies a successfully parsed result atomically.
 */
final class FIConfigParser {

    private static final Logger LOGGER = LogManager.getLogger(FIReference.MOD_ID);

    static final String ITEM_BLACKLIST_KEY = "Blacklist of Items that should not Float";
    static final String REGISTRY_BLACKLIST_KEY = "Blacklist of All Items regardless of Metadata that should not Float";
    static final String OREDICT_BLACKLIST_KEY = "Blacklist of OreDict Entries that should not Float";
    static final String LIQUID_POLICY_KEY = "Liquid Policy";
    static final String FLUID_ALLOWLIST_KEY = "Floating Fluid Allowlist";
    static final String FLUID_DENYLIST_KEY = "Floating Fluid Denylist";
    static final String SERVER_LANGUAGE_KEY = "Default Server language";
    static final String UPDATE_CHECK_KEY = "Disable FloatingItems Update Check";
    static final String LEGACY_UPDATE_CHECK_KEY = "Disable FloatingItems Update Checker Message";

    private FIConfigParser() {}

    /**
     * Parses every property independently. Invalid item entries are logged and skipped; a malformed config backend
     * still propagates to {@link FIConfig}, which resets to safe defaults.
     *
     * @param config the Forge configuration to read
     * @param load   whether to reload the file before reading
     * @return an immutable parsed result
     */
    static ParsedConfig parse(Configuration config, boolean load) {
        List<String> propertyOrder = new ArrayList<String>();

        if (!config.isChild) {
            if (load) {
                config.load();
            }
        }

        Property prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST_KEY, new String[] {});
        prop.comment = "Format: {itemId}(metadata) or namespace:name[:metadata|:*]. Metadata defaults to 0; * means any metadata. Examples: {17}(1), minecraft:log:1, minecraft:stick:*";

        List<BlacklistEntry> itemBlacklist = new ArrayList<BlacklistEntry>();
        for (String entry : prop.getStringList()) {
            BlacklistEntry parsed = BlacklistEntry.parse(entry);
            if (parsed == null) {
                LOGGER.warn("Ignoring invalid item blacklist entry: {}", entry);
            } else {
                itemBlacklist.add(parsed);
            }
        }
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, REGISTRY_BLACKLIST_KEY, new int[] {});
        prop.comment = "Format: Item ID. Example of Cobblestone: 4";
        Set<Integer> registryBlacklist = new HashSet<Integer>();
        for (int id : prop.getIntList()) {
            registryBlacklist.add(id);
        }
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, OREDICT_BLACKLIST_KEY, new String[] {});
        prop.comment = "Format: OreDict entry. All metadata of the entry is blacklisted. Example: ingotCopper";
        List<String> oreBlacklist = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, LIQUID_POLICY_KEY, LiquidPolicy.WATER_LIKE.getId());
        prop.comment = "Liquid policy: water_like (default: water plus water-named Forge fluids with density 800-1200, temperature 250-400 and viscosity 500-1500), water (vanilla water only), or material_water (legacy Material.water check).";
        LiquidPolicy liquidPolicy = LiquidPolicy.parse(prop.getString());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLUID_ALLOWLIST_KEY, new String[] {});
        prop.comment = "Forge fluid names that always float, even when the liquid policy would reject them. Example: salt_water";
        List<String> fluidAllowlist = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLUID_DENYLIST_KEY, new String[] {});
        prop.comment = "Forge fluid names that never float. The denylist wins over the allowlist. Example: oil";
        List<String> fluidDenylist = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, SERVER_LANGUAGE_KEY, "en_us");
        prop.comment = "Server language codes are based off the Minecraft .lang files. See https://minecraft.gamepedia.com/Language for list.";
        String serverLang = prop.getString();
        propertyOrder.add(prop.getName());

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
        updateCheckDisabled = prop.getBoolean(updateCheckDisabled);
        propertyOrder.add(prop.getName());

        return new ParsedConfig(
            itemBlacklist,
            registryBlacklist,
            oreBlacklist,
            new LiquidRules(liquidPolicy, fluidAllowlist, fluidDenylist),
            serverLang,
            updateCheckDisabled,
            propertyOrder);
    }

    static final class ParsedConfig {

        private final List<BlacklistEntry> items;
        private final Set<Integer> registryItems;
        private final List<String> oreDict;
        private final LiquidRules liquidRules;
        private final String serverLang;
        private final boolean updateCheckDisabled;
        private final List<String> propertyOrder;

        ParsedConfig(List<BlacklistEntry> items, Set<Integer> registryItems, List<String> oreDict,
            LiquidRules liquidRules, String serverLang, boolean updateCheckDisabled, List<String> propertyOrder) {
            this.items = Collections.unmodifiableList(new ArrayList<BlacklistEntry>(items));
            this.registryItems = Collections.unmodifiableSet(new HashSet<Integer>(registryItems));
            this.oreDict = Collections.unmodifiableList(new ArrayList<String>(oreDict));
            this.liquidRules = liquidRules == null ? LiquidRules.defaults() : liquidRules;
            this.serverLang = serverLang;
            this.updateCheckDisabled = updateCheckDisabled;
            this.propertyOrder = Collections.unmodifiableList(new ArrayList<String>(propertyOrder));
        }

        List<BlacklistEntry> getItems() {
            return items;
        }

        Set<Integer> getRegistryItems() {
            return registryItems;
        }

        List<String> getOreDict() {
            return oreDict;
        }

        LiquidRules getLiquidRules() {
            return liquidRules;
        }

        String getServerLang() {
            return serverLang;
        }

        boolean isUpdateCheckDisabled() {
            return updateCheckDisabled;
        }

        List<String> getPropertyOrder() {
            return propertyOrder;
        }
    }
}
