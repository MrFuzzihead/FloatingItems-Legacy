package com.mrfuzzihead.floatingitems.api;

import net.minecraft.item.ItemStack;

import com.mrfuzzihead.floatingitems.FIConfig;
import com.mrfuzzihead.floatingitems.ItemFilterMode;

/**
 * Small read-only API for querying FloatingItems.
 *
 * <p>
 * The API deliberately exposes immutable snapshots instead of the mutable config arrays that older versions used.
 * </p>
 */
public final class FloatingItemsAPI {

    public static final String MOD_ID = "floatingitems";

    private FloatingItemsAPI() {}

    /**
     * @return the current immutable blacklist snapshot
     */
    public static BlacklistSnapshot<?> getBlacklist() {
        return FIConfig.blacklist;
    }

    /**
     * @return true when the item lists are interpreted as a whitelist
     */
    public static boolean isWhitelistMode() {
        return FIConfig.filterMode == ItemFilterMode.WHITELIST;
    }

    /**
     * @param stack the stack to query
     * @return true if the current filter list matches the stack; in whitelist mode that means the stack is allowed
     */
    public static boolean isBlacklisted(ItemStack stack) {
        return getBlacklist().isBlacklisted(stack);
    }
}
