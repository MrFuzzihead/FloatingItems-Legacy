package com.mrfuzzihead.floatingitems.api;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;

/**
 * Posted before FloatingItems applies buoyancy. Cancel it to keep the item out of the floating path.
 */
@Cancelable
public class ItemFloatCheckEvent extends Event {

    private final EntityItem entityItem;
    private final ItemStack stack;
    private final World world;
    private final AxisAlignedBB boundingBox;

    @Override
    public boolean isCancelable() {
        return true;
    }

    public ItemFloatCheckEvent(EntityItem entityItem, ItemStack stack, World world, AxisAlignedBB boundingBox) {
        this.entityItem = entityItem;
        this.stack = stack;
        this.world = world;
        this.boundingBox = boundingBox;
    }

    public EntityItem getEntityItem() {
        return entityItem;
    }

    public ItemStack getStack() {
        return stack;
    }

    public World getWorld() {
        return world;
    }

    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }
}
