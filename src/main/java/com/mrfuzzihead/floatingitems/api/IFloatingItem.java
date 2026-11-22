package com.mrfuzzihead.floatingitems.api;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Optional item-level override for the FloatingItems item filter.
 *
 * <p>
 * Return {@link Boolean#TRUE} to opt the item into floating even when the configured item filter would reject it,
 * {@link Boolean#FALSE} to force it to sink, or {@code null} to use the normal configuration.
 * </p>
 */
public interface IFloatingItem {

    /**
     * @param stack the dropped stack
     * @param world the item's world
     * @return a tri-state float override, or {@code null} to use the configured filter
     */
    Boolean shouldFloat(ItemStack stack, World world);
}
