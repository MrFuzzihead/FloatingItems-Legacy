package com.mrfuzzihead.floatingitems.api;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * Posted after FloatingItems has applied buoyancy to an item.
 */
public class ItemFloatTickEvent extends Event {

    private final EntityItem entityItem;
    private final ItemStack stack;
    private final World world;
    private final AxisAlignedBB boundingBox;

    public ItemFloatTickEvent(EntityItem entityItem, ItemStack stack, World world, AxisAlignedBB boundingBox) {
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
