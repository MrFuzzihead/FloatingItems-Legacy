package net.coolsimulations.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import net.coolsimulations.floatingitems.testutil.MinecraftTestBootstrap;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class FloatingItemPolicyTest {

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Test
    public void blacklistedStacksDoNotFloat() {
        World world = mock(World.class);
        when(world.isMaterialInBB(any(AxisAlignedBB.class), eq(Material.water))).thenReturn(true);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);
        ItemStack stack = new ItemStack(MinecraftTestBootstrap.stick(), 1, 0);
        FIConfig.Blacklist blacklist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        assertFalse(FloatingItemPolicy.shouldFloat(stack, world, box, blacklist));
    }

    @Test
    public void clearStacksInWaterFloat() {
        World world = mock(World.class);
        when(world.isMaterialInBB(any(AxisAlignedBB.class), eq(Material.water))).thenReturn(true);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertTrue(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 0),
                world,
                box,
                FIConfig.Blacklist.empty()));
    }

    @Test
    public void nullStacksAndNullItemsDoNotFloat() {
        World world = mock(World.class);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertFalse(FloatingItemPolicy.shouldFloat(null, world, box, FIConfig.Blacklist.empty()));
        assertFalse(FloatingItemPolicy.shouldFloat(new ItemStack((Item) null), world, box, FIConfig.Blacklist.empty()));
    }

    @Test
    public void waterCheckUsesTheContractedBoundingBox() {
        World world = mock(World.class);
        when(world.isMaterialInBB(any(AxisAlignedBB.class), eq(Material.water))).thenReturn(true);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertTrue(FloatingItemPolicy.isInWater(world, box));

        ArgumentCaptor<AxisAlignedBB> captor = ArgumentCaptor.forClass(AxisAlignedBB.class);
        verify(world).isMaterialInBB(captor.capture(), eq(Material.water));
        AxisAlignedBB checked = captor.getValue();
        assertEquals(box.minY + 0.001D, checked.minY, 1.0E-9D);
        assertEquals(box.maxY - 0.001D, checked.maxY, 1.0E-9D);
    }

    @Test
    public void nullWorldOrBoxIsNotInWater() {
        assertFalse(FloatingItemPolicy.isInWater(null, AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1)));
        assertFalse(FloatingItemPolicy.isInWater(mock(World.class), null));
    }

    @Test
    public void buoyancyAppliesDampingAndLift() {
        Entity entity = mock(Entity.class);
        entity.motionX = 1.0D;
        entity.motionY = 0.05D;
        entity.motionZ = -2.0D;

        FloatingItemPolicy.applyBuoyancy(entity);

        assertEquals(0.95D, entity.motionX, 1.0E-12);
        assertEquals(0.0905D, entity.motionY, 1.0E-9D);
        assertEquals(-1.9D, entity.motionZ, 1.0E-12);
    }

    @Test
    public void buoyancyDoesNotApplySlowLiftAtOrAboveThreshold() {
        Entity entity = mock(Entity.class);
        entity.motionY = 0.06D;

        FloatingItemPolicy.applyBuoyancy(entity);

        assertEquals(0.1D, entity.motionY, 1.0E-9D);
    }

    @Test
    public void negativeVerticalMotionGetsTheSlowLiftAndBuoyancy() {
        Entity entity = mock(Entity.class);
        entity.motionY = -0.5D;

        FloatingItemPolicy.applyBuoyancy(entity);

        assertEquals(-0.4595D, entity.motionY, 1.0E-9D);
    }
}
