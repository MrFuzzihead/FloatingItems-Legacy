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

    static final String ITEM_FILTER_MODE_KEY = "Item Filter Mode";
    static final String ITEM_BLACKLIST_KEY = "Blacklist of Items that should not Float";
    static final String REGISTRY_BLACKLIST_KEY = "Blacklist of All Items regardless of Metadata that should not Float";
    static final String OREDICT_BLACKLIST_KEY = "Blacklist of OreDict Entries that should not Float";
    static final String DIMENSION_FILTER_MODE_KEY = "Dimension Filter Mode";
    static final String FLOATING_DIMENSIONS_KEY = "Floating Dimensions";
    static final String BIOME_FILTER_MODE_KEY = "Biome Filter Mode";
    static final String FLOATING_BIOMES_KEY = "Floating Biomes";
    static final String MOD_FILTER_MODE_KEY = "Mod ID Filter Mode";
    static final String FLOATING_MOD_IDS_KEY = "Floating Mod IDs";
    static final String LIQUID_POLICY_KEY = "Liquid Policy";
    static final String FLUID_ALLOWLIST_KEY = "Floating Fluid Allowlist";
    static final String FLUID_DENYLIST_KEY = "Floating Fluid Denylist";
    static final String BUOYANCY_LIFT_KEY = "Floating Lift Acceleration";
    static final String HORIZONTAL_DAMPING_KEY = "Floating Horizontal Damping";
    static final String MAX_UPWARD_SPEED_KEY = "Floating Maximum Upward Speed";
    static final String SURFACE_OFFSET_KEY = "Floating Surface Offset";
    static final String DEBUG_COUNTERS_KEY = "Enable Debug Counters";
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
        config.getCategory(FIReference.CONFIG_CATEGORY_UTIL)
            .setLanguageKey("fi.config.category.util");

        Property prop = config
            .get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_FILTER_MODE_KEY, ItemFilterMode.BLACKLIST.getId());
        prop.setLanguageKey("fi.config.itemFilterMode");
        prop.comment = "Item filter mode: blacklist (listed items do not float, default) or whitelist (only listed items float).";
        ItemFilterMode itemFilterMode = ItemFilterMode.parse(prop.getString());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, ITEM_BLACKLIST_KEY, new String[] {});
        prop.setLanguageKey("fi.config.itemFilter");
        prop.comment = "Format: {itemId}(metadata) or namespace:name[:metadata|:*]. Metadata defaults to 0; * means any metadata. Examples: {17}(1), minecraft:log:1, minecraft:stick:*. "
            + (itemFilterMode == ItemFilterMode.WHITELIST ? "In whitelist mode these are the only items that float."
                : "In blacklist mode these items do not float.");

        List<BlacklistEntry> itemBlacklist = new ArrayList<BlacklistEntry>();
        for (String entry : prop.getStringList()) {
            BlacklistEntry parsed = BlacklistEntry.parse(entry);
            if (parsed == null) {
                LOGGER.warn("Ignoring invalid item filter entry: {}", entry);
            } else {
                itemBlacklist.add(parsed);
            }
        }
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, REGISTRY_BLACKLIST_KEY, new int[] {});
        prop.setLanguageKey("fi.config.registryFilter");
        prop.comment = "Format: Item ID. Example of Cobblestone: 4. "
            + (itemFilterMode == ItemFilterMode.WHITELIST ? "In whitelist mode these are allowed by item ID."
                : "In blacklist mode these are blocked by item ID.");
        Set<Integer> registryBlacklist = new HashSet<Integer>();
        for (int id : prop.getIntList()) {
            registryBlacklist.add(id);
        }
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, OREDICT_BLACKLIST_KEY, new String[] {});
        prop.setLanguageKey("fi.config.oreDictFilter");
        prop.comment = "Format: OreDict entry. All metadata of the entry is included. Example: ingotCopper. "
            + (itemFilterMode == ItemFilterMode.WHITELIST ? "In whitelist mode these are allowed by ore name."
                : "In blacklist mode these are blocked by ore name.");
        List<String> oreBlacklist = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, DIMENSION_FILTER_MODE_KEY, ScopeMode.ALL.getId());
        prop.setLanguageKey("fi.config.dimensionFilterMode");
        prop.comment = "Dimension scope: all, whitelist or blacklist. Example: whitelist with dimensions 0, -1.";
        ScopeMode dimensionMode = ScopeMode.parse(prop.getString());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLOATING_DIMENSIONS_KEY, new int[] {});
        prop.setLanguageKey("fi.config.floatingDimensions");
        prop.comment = "Dimension IDs used by the dimension scope. Example: 0, -1, 1.";
        Set<Integer> dimensions = new HashSet<Integer>();
        for (int id : prop.getIntList()) {
            dimensions.add(id);
        }
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, BIOME_FILTER_MODE_KEY, ScopeMode.ALL.getId());
        prop.setLanguageKey("fi.config.biomeFilterMode");
        prop.comment = "Biome scope: all, whitelist or blacklist. Uses biome names such as Plains or Ocean.";
        ScopeMode biomeMode = ScopeMode.parse(prop.getString());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLOATING_BIOMES_KEY, new String[] {});
        prop.setLanguageKey("fi.config.floatingBiomes");
        prop.comment = "Biome names used by the biome scope. Example: Plains, Ocean.";
        List<String> biomes = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, MOD_FILTER_MODE_KEY, ScopeMode.ALL.getId());
        prop.setLanguageKey("fi.config.modFilterMode");
        prop.comment = "Mod-ID scope: all, whitelist or blacklist. Uses the item registry namespace.";
        ScopeMode modMode = ScopeMode.parse(prop.getString());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLOATING_MOD_IDS_KEY, new String[] {});
        prop.setLanguageKey("fi.config.floatingModIds");
        prop.comment = "Mod IDs used by the mod-ID scope. Example: minecraft, thermalexpansion.";
        List<String> modIds = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, LIQUID_POLICY_KEY, LiquidPolicy.WATER_LIKE.getId());
        prop.setLanguageKey("fi.config.liquidPolicy");
        prop.comment = "Liquid policy: water_like (default: water plus water-named Forge fluids with density 800-1200, temperature 250-400 and viscosity 500-1500), water (vanilla water only), or material_water (legacy Material.water check).";
        LiquidPolicy liquidPolicy = LiquidPolicy.parse(prop.getString());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLUID_ALLOWLIST_KEY, new String[] {});
        prop.setLanguageKey("fi.config.fluidAllowlist");
        prop.comment = "Forge fluid names that always float, even when the liquid policy would reject them. Example: salt_water";
        List<String> fluidAllowlist = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, FLUID_DENYLIST_KEY, new String[] {});
        prop.setLanguageKey("fi.config.fluidDenylist");
        prop.comment = "Forge fluid names that never float. The denylist wins over the allowlist. Example: oil";
        List<String> fluidDenylist = Arrays.asList(prop.getStringList());
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, BUOYANCY_LIFT_KEY, BuoyancySettings.DEFAULT_LIFT);
        prop.setLanguageKey("fi.config.buoyancyLift");
        prop.comment = "Upward acceleration added while in an allowed liquid. Range 0.0-1.0; default 0.04.";
        double buoyancyLift = prop.getDouble(BuoyancySettings.DEFAULT_LIFT);
        propertyOrder.add(prop.getName());

        prop = config
            .get(FIReference.CONFIG_CATEGORY_UTIL, HORIZONTAL_DAMPING_KEY, BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING);
        prop.setLanguageKey("fi.config.horizontalDamping");
        prop.comment = "Horizontal motion multiplier while floating. Range 0.0-1.0; default 0.95.";
        double horizontalDamping = prop.getDouble(BuoyancySettings.DEFAULT_HORIZONTAL_DAMPING);
        propertyOrder.add(prop.getName());

        prop = config
            .get(FIReference.CONFIG_CATEGORY_UTIL, MAX_UPWARD_SPEED_KEY, BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED);
        prop.setLanguageKey("fi.config.maxUpwardSpeed");
        prop.comment = "Maximum upward speed while floating. Range 0.0-10.0; default 0.4.";
        double maxUpwardSpeed = prop.getDouble(BuoyancySettings.DEFAULT_MAX_UPWARD_SPEED);
        propertyOrder.add(prop.getName());

        prop = config
            .get(FIReference.CONFIG_CATEGORY_UTIL, SURFACE_OFFSET_KEY, BuoyancySettings.DEFAULT_SURFACE_OFFSET);
        prop.setLanguageKey("fi.config.surfaceOffset");
        prop.comment = "Extra height above the liquid surface where buoyancy still applies. Range -0.5-0.5; default 0.0.";
        double surfaceOffset = prop.getDouble(BuoyancySettings.DEFAULT_SURFACE_OFFSET);
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, DEBUG_COUNTERS_KEY, false);
        prop.setLanguageKey("fi.config.debugCounters");
        prop.comment = "Enable process-wide item evaluation, float and cache counters. Default: false.";
        boolean debugCounters = prop.getBoolean(false);
        propertyOrder.add(prop.getName());

        prop = config.get(FIReference.CONFIG_CATEGORY_UTIL, SERVER_LANGUAGE_KEY, "en_us");
        prop.setLanguageKey("fi.config.serverLanguage");
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
        prop.setLanguageKey("fi.config.disableUpdateCheck");
        prop.comment = "Disable the FloatingItems update check entirely (no network request and no message).";
        updateCheckDisabled = prop.getBoolean(updateCheckDisabled);
        propertyOrder.add(prop.getName());

        return new ParsedConfig(
            itemBlacklist,
            registryBlacklist,
            oreBlacklist,
            itemFilterMode,
            new ScopeRules(dimensionMode, dimensions, biomeMode, biomes, modMode, modIds),
            new LiquidRules(liquidPolicy, fluidAllowlist, fluidDenylist),
            new BuoyancySettings(buoyancyLift, horizontalDamping, maxUpwardSpeed, surfaceOffset),
            debugCounters,
            serverLang,
            updateCheckDisabled,
            propertyOrder);
    }

    static final class ParsedConfig {

        private final List<BlacklistEntry> items;
        private final Set<Integer> registryItems;
        private final List<String> oreDict;
        private final ItemFilterMode itemFilterMode;
        private final ScopeRules scopeRules;
        private final LiquidRules liquidRules;
        private final BuoyancySettings buoyancy;
        private final boolean debugCounters;
        private final String serverLang;
        private final boolean updateCheckDisabled;
        private final List<String> propertyOrder;

        ParsedConfig(List<BlacklistEntry> items, Set<Integer> registryItems, List<String> oreDict,
            ItemFilterMode itemFilterMode, ScopeRules scopeRules, LiquidRules liquidRules, BuoyancySettings buoyancy,
            boolean debugCounters, String serverLang, boolean updateCheckDisabled, List<String> propertyOrder) {
            this.items = Collections.unmodifiableList(new ArrayList<BlacklistEntry>(items));
            this.registryItems = Collections.unmodifiableSet(new HashSet<Integer>(registryItems));
            this.oreDict = Collections.unmodifiableList(new ArrayList<String>(oreDict));
            this.itemFilterMode = itemFilterMode == null ? ItemFilterMode.BLACKLIST : itemFilterMode;
            this.scopeRules = scopeRules == null ? ScopeRules.defaults() : scopeRules;
            this.liquidRules = liquidRules == null ? LiquidRules.defaults() : liquidRules;
            this.buoyancy = buoyancy == null ? BuoyancySettings.defaults() : buoyancy;
            this.debugCounters = debugCounters;
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

        ItemFilterMode getItemFilterMode() {
            return itemFilterMode;
        }

        ScopeRules getScopeRules() {
            return scopeRules;
        }

        LiquidRules getLiquidRules() {
            return liquidRules;
        }

        BuoyancySettings getBuoyancy() {
            return buoyancy;
        }

        boolean hasDebugCounters() {
            return debugCounters;
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
