package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.junit.Test;

import com.mrfuzzihead.floatingitems.api.ItemFloatCheckEvent;
import com.mrfuzzihead.floatingitems.api.ItemFloatTickEvent;
import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

public class FloatingItemEventTest {

    @Test
    public void checkEventIsCancelableAndExposesTheItemContext() {
        EntityItem entityItem = mock(EntityItem.class);
        ItemStack stack = new ItemStack(MinecraftTestBootstrap.stick());
        World world = mock(World.class);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1);

        ItemFloatCheckEvent event = new ItemFloatCheckEvent(entityItem, stack, world, box);

        assertSame(entityItem, event.getEntityItem());
        assertSame(stack, event.getStack());
        assertSame(world, event.getWorld());
        assertSame(box, event.getBoundingBox());
        assertFalse(event.isCanceled());

        event.setCanceled(true);
        assertTrue(event.isCanceled());
    }

    @Test
    public void tickEventExposesTheItemContextAndIsNotCancelable() {
        EntityItem entityItem = mock(EntityItem.class);
        ItemStack stack = new ItemStack(MinecraftTestBootstrap.stick());
        World world = mock(World.class);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1);

        ItemFloatTickEvent event = new ItemFloatTickEvent(entityItem, stack, world, box);

        assertSame(entityItem, event.getEntityItem());
        assertSame(stack, event.getStack());
        assertSame(world, event.getWorld());
        assertSame(box, event.getBoundingBox());
        assertFalse(event.isCancelable());
    }
}
