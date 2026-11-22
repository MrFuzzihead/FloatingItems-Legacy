package com.mrfuzzihead.floatingitems.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Immutable blacklist snapshot exposed to addons.
 *
 * @param <T> the concrete entry type
 */
public class BlacklistSnapshot<T extends BlacklistEntry> {

    private final List<T> items;
    private final Map<Item, Set<Integer>> itemBlacklist;
    private final Set<Integer> registryItems;
    private final Set<String> oreDict;
    private final Map<Item, Map<Integer, Boolean>> decisionCache = new ConcurrentHashMap<Item, Map<Integer, Boolean>>();

    public BlacklistSnapshot(Collection<T> items, Collection<Integer> registryItems, Collection<String> oreDict) {
        List<T> itemCopy = new ArrayList<T>();
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    itemCopy.add(item);
                }
            }
        }
        this.items = Collections.unmodifiableList(itemCopy);

        Map<Item, Set<Integer>> blacklistedMetas = new HashMap<Item, Set<Integer>>();
        for (T entry : itemCopy) {
            Item item = entry.getItem();
            if (item == null) {
                continue;
            }
            Set<Integer> metas = blacklistedMetas.get(item);
            if (metas == null) {
                metas = new HashSet<Integer>();
                blacklistedMetas.put(item, metas);
            }
            metas.add(entry.getMeta());
        }
        Map<Item, Set<Integer>> immutableItemBlacklist = new HashMap<Item, Set<Integer>>();
        for (Map.Entry<Item, Set<Integer>> entry : blacklistedMetas.entrySet()) {
            immutableItemBlacklist.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        this.itemBlacklist = Collections.unmodifiableMap(immutableItemBlacklist);

        this.registryItems = Collections.unmodifiableSet(
            new HashSet<Integer>(registryItems == null ? Collections.<Integer>emptySet() : registryItems));

        Set<String> oreNames = new HashSet<String>();
        if (oreDict != null) {
            for (String name : oreDict) {
                if (name != null && !name.trim()
                    .isEmpty()) {
                    oreNames.add(name.trim());
                }
            }
        }
        this.oreDict = Collections.unmodifiableSet(oreNames);
    }

    public List<T> getItems() {
        return items;
    }

    public Set<Integer> getRegistryItems() {
        return registryItems;
    }

    public Set<String> getOreDict() {
        return oreDict;
    }

    public boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        Item item = stack.getItem();
        int meta = stack.getItemDamage();

        if (itemBlacklist.isEmpty() && registryItems.isEmpty() && oreDict.isEmpty()) {
            return false;
        }

        Map<Integer, Boolean> itemCache = decisionCache.get(item);
        if (itemCache == null) {
            Map<Integer, Boolean> newCache = new ConcurrentHashMap<Integer, Boolean>();
            Map<Integer, Boolean> previous = decisionCache.putIfAbsent(item, newCache);
            itemCache = previous == null ? newCache : previous;
        }

        Boolean cached = itemCache.get(meta);
        if (cached != null) {
            DebugCounters.cacheHit();
            return cached;
        }

        DebugCounters.cacheMiss();
        boolean result = computeBlacklisted(item, meta);
        Boolean previous = itemCache.putIfAbsent(meta, result);
        return previous == null ? result : previous;
    }

    private boolean computeBlacklisted(Item item, int meta) {
        Set<Integer> metas = itemBlacklist.get(item);
        if (metas != null && (metas.contains(meta) || metas.contains(BlacklistEntry.WILDCARD_META))) {
            return true;
        }

        if (registryItems.contains(Item.getIdFromItem(item))) {
            return true;
        }

        for (String oreName : oreDict) {
            for (ItemStack oreStack : OreDictionary.getOres(oreName)) {
                if (oreStack == null || oreStack.getItem() == null) {
                    continue;
                }
                // An ore-dictionary entry is a whole-entry blacklist; metadata is intentionally ignored (B-09).
                if (oreStack.getItem() == item) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Diagnostic count used by the regression tests.
     *
     * @return number of cached decisions
     */
    public int getCachedDecisionCount() {
        int count = 0;
        for (Map<Integer, Boolean> itemCache : decisionCache.values()) {
            count += itemCache.size();
        }
        return count;
    }
}
