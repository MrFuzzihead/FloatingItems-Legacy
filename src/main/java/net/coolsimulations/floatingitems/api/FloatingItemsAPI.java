package net.coolsimulations.floatingitems.api;

import net.coolsimulations.floatingitems.FIConfig;
import net.minecraft.item.ItemStack;

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
     * @param stack the stack to query
     * @return true if the current blacklist blocks the stack
     */
    public static boolean isBlacklisted(ItemStack stack) {
        return getBlacklist().isBlacklisted(stack);
    }
}
