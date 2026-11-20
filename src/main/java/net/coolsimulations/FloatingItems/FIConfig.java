package net.coolsimulations.FloatingItems;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.logging.log4j.Level;

import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.FMLRelaunchLog;

public class FIConfig {

    static Configuration config;

    public static FIConfigItem[] blacklistItems;

    public static int[] blacklistRegistryItems;

    public static String[] blacklistOreDict;

    public static String serverLang;

    public static boolean disableUpdateCheck;

    private static final String UPDATE_CHECK_KEY = "Disable FloatingItems Update Check";
    private static final String LEGACY_UPDATE_CHECK_KEY = "Disable FloatingItems Update Checker Message";

    public static void init(File file) {
        config = new Configuration(file);
        syncConfig(true);
    }

    public static void forceSave() {
        config.save();
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
            prop.comment = "Format: {Item ID}(Metadata). Metadata is optional and defaults to 0. Example of Spruce Log: {17}(1)";

            List<FIConfigItem> blacklist = new ArrayList<FIConfigItem>();
            for (String entry : prop.getStringList()) {
                FIConfigItem parsed = FIConfigItem.parse(entry);
                if (parsed == null) {
                    FMLRelaunchLog
                        .log(FIReference.MOD_NAME, Level.WARN, "Ignoring invalid item blacklist entry: " + entry);
                } else {
                    blacklist.add(parsed);
                }
            }

            blacklistItems = blacklist.toArray(new FIConfigItem[blacklist.size()]);

            propOrder.add(prop.getName());

            prop = config.get(
                FIReference.CONFIG_CATEGORY_UTIL,
                "Blacklist of All Items regardless of Metadata that should not Float",
                new int[] {});
            prop.comment = "Format: Item ID. Example of Cobblestone: 4";
            blacklistRegistryItems = prop.getIntList();
            propOrder.add(prop.getName());

            prop = config.get(
                FIReference.CONFIG_CATEGORY_UTIL,
                "Blacklist of OreDict Entries that should not Float",
                new String[] {});
            prop.comment = "Format: OreDict. Example of Copper Ingot: ingotCopper";
            blacklistOreDict = prop.getStringList();
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

            if (config.hasChanged()) {
                config.save();
            }

        } catch (final Exception e) {
            FMLRelaunchLog.log(
                FIReference.MOD_NAME,
                Level.ERROR,
                ("Problem loading FloatingItems config (\"FloatingItems.conf\")"));
            FMLRelaunchLog.log(FIReference.MOD_NAME, Level.ERROR, e.toString());
            // Do not leave half-updated static state behind. If the config backend itself fails, run with safe
            // defaults until the next successful sync.
            blacklistItems = new FIConfigItem[0];
            blacklistRegistryItems = new int[0];
            blacklistOreDict = new String[0];
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
        if (blacklistItems == null) {
            return;
        }

        List<FIConfigItem> resolved = new ArrayList<FIConfigItem>();
        for (FIConfigItem item : blacklistItems) {
            if (item == null) {
                continue;
            }
            if (item.getItem() == null) {
                FMLRelaunchLog.log(
                    FIReference.MOD_NAME,
                    Level.WARN,
                    "Ignoring blacklist entry for unregistered item id: " + item.getRegistry());
            } else {
                resolved.add(item);
            }
        }
        blacklistItems = resolved.toArray(new FIConfigItem[resolved.size()]);
    }

    public static List<IConfigElement> getConfigElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        list.addAll(new ConfigElement(config.getCategory(FIReference.CONFIG_CATEGORY_UTIL)).getChildElements());
        return list;
    }

    public static class FIConfigItem {

        private int registry;
        private int meta;
        private Item item;

        private FIConfigItem(Item item, int registry, int meta) {

            this.item = item;
            this.registry = registry;
            this.meta = meta;
        }

        FIConfigItem(ItemStack stack) {

            this(itemOf(stack), registryOf(stack), metaOf(stack));
        }

        private static Item itemOf(ItemStack stack) {

            return stack == null ? null : stack.getItem();
        }

        private static int registryOf(ItemStack stack) {

            Item item = itemOf(stack);
            return item == null ? 0 : Item.getIdFromItem(item);
        }

        private static int metaOf(ItemStack stack) {

            Item item = itemOf(stack);
            return item == null ? 0 : stack.getItemDamage();
        }

        FIConfigItem(String entry) {

            FIConfigItem parsed = parse(entry);
            if (parsed == null) {
                throw new IllegalArgumentException("Invalid FloatingItems blacklist entry: " + entry);
            }

            this.item = parsed.item;
            this.registry = parsed.registry;
            this.meta = parsed.meta;
        }

        /**
         * Parses one {@code {itemId}(metadata)} entry. The metadata suffix is optional and defaults to 0. Malformed
         * strings return {@code null} so the caller can skip the entry instead of aborting the whole config load.
         * The item itself is resolved lazily because another mod may not have registered its items yet.
         *
         * @param entry the raw config entry
         * @return a parsed item, or {@code null} if the entry is invalid
         */
        static FIConfigItem parse(String entry) {

            if (entry == null) {
                return null;
            }

            String normalized = entry.trim()
                .replaceAll("\\s+", "");
            if (normalized.isEmpty() || normalized.charAt(0) != '{') {
                return null;
            }

            int closeBrace = normalized.indexOf('}');
            if (closeBrace < 0) {
                return null;
            }

            String registryText = normalized.substring(1, closeBrace);
            if (registryText.isEmpty()) {
                return null;
            }

            int registry;
            try {
                registry = Integer.parseInt(registryText);
            } catch (NumberFormatException e) {
                return null;
            }

            int meta = 0;
            if (closeBrace + 1 < normalized.length()) {
                String suffix = normalized.substring(closeBrace + 1);
                if (suffix.length() < 2 || suffix.charAt(0) != '(' || suffix.charAt(suffix.length() - 1) != ')') {
                    return null;
                }

                String metaText = suffix.substring(1, suffix.length() - 1);
                if (metaText.isEmpty()) {
                    return null;
                }

                try {
                    meta = Integer.parseInt(metaText);
                } catch (NumberFormatException e) {
                    return null;
                }

                if (meta < 0) {
                    return null;
                }
            }

            // The registry object may not exist yet: other mods register items during their own preInit, after
            // this config is loaded. Resolution is deferred to resolveItems()/getItem().
            return new FIConfigItem(null, registry, meta);
        }

        public Item getItem() {

            if (this.item == null && this.registry > 0) {
                Object registered = Item.itemRegistry.getObjectById(this.registry);
                if (registered instanceof Item) {
                    this.item = (Item) registered;
                }
            }
            return this.item;
        }

        public int getMeta() {

            return this.meta;
        }

        public ItemStack getStack() {

            return getStack(1);
        }

        public ItemStack getStack(int count) {

            return new ItemStack(getItem(), count, this.meta);
        }

        public int getRegistry() {

            return this.registry;
        }

        @Override
        public String toString() {

            return "{" + this.registry + "}(" + this.meta + ")";
        }

        @Override
        public boolean equals(Object obj) {

            if (obj == this) {
                return true;
            }

            if (!(obj instanceof FIConfigItem)) {
                return false;
            }

            FIConfigItem other = (FIConfigItem) obj;

            /**
             * if(this.registry == other.registry) {
             *
             * if(this.meta == other.meta) {
             *
             * if(this.item == other.item) {
             * return true;
             * } else {
             * return false;
             * }
             *
             * } else {
             * return false;
             * }
             *
             * } else {
             * return false;
             * }
             **/

            return this.registry == other.registry && this.meta == other.meta && this.item == other.item;
        }
    }

}
