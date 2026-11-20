package net.coolsimulations.floatingitems.api;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * One immutable blacklist rule.
 *
 * <p>
 * Supports the legacy numeric form and registry-name form. Metadata may be exact or wildcarded.
 * </p>
 */
public class BlacklistEntry {

    public static final int WILDCARD_META = -1;

    private final String registryName;
    private final int registry;
    private final int meta;
    private Item item;

    protected BlacklistEntry(BlacklistEntry other) {
        this(other.item, other.registryName, other.registry, other.meta);
    }

    protected BlacklistEntry(Item item, String registryName, int registry, int meta) {
        this.item = item;
        this.registryName = registryName;
        this.registry = registry;
        this.meta = meta;
    }

    /**
     * Parses either the legacy {@code {itemId}(metadata)} form or a registry name:
     * {@code minecraft:log}, {@code minecraft:log:1}, {@code minecraft:log:*}. Malformed strings return
     * {@code null}.
     *
     * @param entry the raw config entry
     * @return the parsed entry, or null
     */
    public static BlacklistEntry parse(String entry) {
        if (entry == null) {
            return null;
        }

        String normalized = entry.trim()
            .replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.charAt(0) == '{') {
            return parseLegacy(normalized);
        }
        return parseRegistryName(normalized);
    }

    private static BlacklistEntry parseLegacy(String normalized) {
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
            if ("*".equals(metaText)) {
                meta = WILDCARD_META;
            } else {
                try {
                    meta = Integer.parseInt(metaText);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (meta < 0) {
                    return null;
                }
            }
        }

        return new BlacklistEntry(null, null, registry, meta);
    }

    private static BlacklistEntry parseRegistryName(String normalized) {
        int firstColon = normalized.indexOf(':');
        if (firstColon <= 0 || firstColon == normalized.length() - 1) {
            return null;
        }

        int lastColon = normalized.lastIndexOf(':');
        String registryName;
        int meta = 0;
        if (firstColon == lastColon) {
            registryName = normalized;
        } else {
            registryName = normalized.substring(0, lastColon);
            if (registryName.indexOf(':') != registryName.lastIndexOf(':')) {
                return null;
            }
            String metaText = normalized.substring(lastColon + 1);
            if ("*".equals(metaText) || "-1".equals(metaText)) {
                meta = WILDCARD_META;
            } else {
                try {
                    meta = Integer.parseInt(metaText);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (meta < 0) {
                    return null;
                }
            }
        }

        if (registryName.isEmpty() || registryName.charAt(0) == ':'
            || registryName.charAt(registryName.length() - 1) == ':') {
            return null;
        }
        return new BlacklistEntry(null, registryName, -1, meta);
    }

    public Item getItem() {
        if (this.item == null) {
            Object registered;
            if (this.registryName != null) {
                registered = Item.itemRegistry.getObject(this.registryName);
            } else if (this.registry > 0) {
                registered = Item.itemRegistry.getObjectById(this.registry);
            } else {
                registered = null;
            }
            if (registered instanceof Item) {
                this.item = (Item) registered;
            }
        }
        return this.item;
    }

    public int getMeta() {
        return this.meta;
    }

    public boolean hasWildcardMeta() {
        return this.meta == WILDCARD_META;
    }

    public ItemStack getStack() {
        return getStack(1);
    }

    public ItemStack getStack(int count) {
        return new ItemStack(getItem(), count, this.meta == WILDCARD_META ? 0 : this.meta);
    }

    public int getRegistry() {
        if (this.registry >= 0) {
            return this.registry;
        }
        Item resolved = getItem();
        return resolved == null ? -1 : Item.getIdFromItem(resolved);
    }

    public String getRegistryName() {
        return this.registryName;
    }

    @Override
    public String toString() {
        if (this.registryName != null) {
            return this.registryName + (hasWildcardMeta() ? ":*" : ":" + this.meta);
        }
        return "{" + this.registry + "}(" + (hasWildcardMeta() ? "*" : this.meta) + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BlacklistEntry)) {
            return false;
        }

        BlacklistEntry other = (BlacklistEntry) obj;
        if (this.registryName == null || other.registryName == null) {
            return this.registryName == null && other.registryName == null
                && this.registry == other.registry
                && this.meta == other.meta;
        }
        return this.registryName.equals(other.registryName) && this.meta == other.meta;
    }

    @Override
    public int hashCode() {
        int base = this.registryName == null ? this.registry : this.registryName.hashCode();
        return 31 * base + this.meta;
    }
}
