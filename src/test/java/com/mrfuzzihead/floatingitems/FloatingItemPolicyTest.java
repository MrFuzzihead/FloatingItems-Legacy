package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mrfuzzihead.floatingitems.api.IFloatingItem;
import com.mrfuzzihead.floatingitems.testutil.MinecraftTestBootstrap;

public class FloatingItemPolicyTest {

    @BeforeClass
    public static void installFakeRegistry() {
        MinecraftTestBootstrap.installFakeRegistry();
    }

    @Test
    public void blacklistedStacksDoNotFloat() {
        World world = mock(World.class);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);
        ItemStack stack = new ItemStack(MinecraftTestBootstrap.stick(), 1, 0);
        FIConfig.Blacklist blacklist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        assertFalse(FloatingItemPolicy.shouldFloat(stack, world, box, blacklist));
    }

    @Test
    public void clearStacksInAllowedWaterFloat() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertTrue(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 0),
                world,
                box,
                FIConfig.Blacklist.empty()));
    }

    @Test
    public void whitelistModeOnlyFloatsListedStacks() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);
        FIConfig.Blacklist whitelist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        assertTrue(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 0),
                world,
                box,
                whitelist,
                LiquidRules.defaults(),
                ItemFilterMode.WHITELIST));
        assertFalse(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 1),
                world,
                box,
                whitelist,
                LiquidRules.defaults(),
                ItemFilterMode.WHITELIST));
    }

    @Test
    public void whitelistModeWithAnEmptyListFloatsNothing() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertFalse(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 0),
                world,
                box,
                FIConfig.Blacklist.empty(),
                LiquidRules.defaults(),
                ItemFilterMode.WHITELIST));
    }

    @Test
    public void blacklistModeStillFloatsUnlistedStacks() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);
        FIConfig.Blacklist blacklist = new FIConfig.Blacklist(
            Collections.singletonList(new FIConfig.FIConfigItem("{280}(0)")),
            Collections.<Integer>emptySet(),
            Collections.<String>emptyList());

        assertTrue(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 1),
                world,
                box,
                blacklist,
                LiquidRules.defaults(),
                ItemFilterMode.BLACKLIST));
        assertFalse(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(MinecraftTestBootstrap.stick(), 1, 0),
                world,
                box,
                blacklist,
                LiquidRules.defaults(),
                ItemFilterMode.BLACKLIST));
    }

    @Test
    public void itemOverrideCanOptIntoFloatingPastTheBlacklist() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);
        Item item = new TestFloatingItem(Boolean.TRUE);
        FIConfig.Blacklist blacklist = new FIConfig.Blacklist(
            Collections.<FIConfig.FIConfigItem>emptyList(),
            Collections.singleton(Item.getIdFromItem(item)),
            Collections.<String>emptyList());

        assertTrue(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(item),
                world,
                box,
                blacklist,
                LiquidRules.defaults(),
                ItemFilterMode.BLACKLIST));
    }

    @Test
    public void itemOverrideCanForceSinking() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertFalse(
            FloatingItemPolicy.shouldFloat(
                new ItemStack(new TestFloatingItem(Boolean.FALSE)),
                world,
                box,
                FIConfig.Blacklist.empty(),
                LiquidRules.defaults(),
                ItemFilterMode.BLACKLIST));
    }

    @Test
    public void nullStacksAndNullItemsDoNotFloat() {
        World world = mock(World.class);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D);

        assertFalse(FloatingItemPolicy.shouldFloat(null, world, box, FIConfig.Blacklist.empty()));
        assertFalse(FloatingItemPolicy.shouldFloat(new ItemStack((Item) null), world, box, FIConfig.Blacklist.empty()));
    }

    @Test
    public void liquidCheckDelegatesToTheContractedBoundingBox() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 1, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 0.999D, 0.0D, 0.25D, 1.5D, 0.25D);

        assertTrue(FloatingItemPolicy.isInFloatingLiquid(world, box, LiquidRules.defaults()));
        verify(world, never()).getBlock(0, 0, 0);
    }

    @Test
    public void nullWorldOrBoxIsNotInLiquid() {
        assertFalse(FloatingItemPolicy.isInFloatingLiquid(null, AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1), null));
        assertFalse(FloatingItemPolicy.isInFloatingLiquid(mock(World.class), null, null));
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
    public void customBuoyancyUsesDampingLiftAndMaximumSpeed() {
        Entity entity = mock(Entity.class);
        entity.motionX = 1.0D;
        entity.motionY = 0.05D;
        entity.motionZ = -2.0D;

        FloatingItemPolicy.applyBuoyancy(entity, new BuoyancySettings(0.2D, 0.5D, 0.1D, 0.0D));

        assertEquals(0.5D, entity.motionX, 1.0E-12);
        assertEquals(0.1D, entity.motionY, 1.0E-9);
        assertEquals(-1.0D, entity.motionZ, 1.0E-12);
    }

    @Test
    public void positiveSurfaceOffsetKeepsDetectingLiquidBelowTheItem() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(water);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(0.0D, 1.1D, 0.0D, 0.25D, 1.35D, 0.25D);

        assertFalse(
            FloatingItemPolicy.isInFloatingLiquid(world, box, LiquidRules.defaults(), BuoyancySettings.defaults()));
        assertTrue(
            FloatingItemPolicy.isInFloatingLiquid(
                world,
                box,
                LiquidRules.defaults(),
                new BuoyancySettings(0.04D, 0.95D, 0.4D, 0.2D)));
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

    private static final class TestFloatingItem extends Item implements IFloatingItem {

        private final Boolean override;

        TestFloatingItem(Boolean override) {
            this.override = override;
        }

        @Override
        public Boolean shouldFloat(ItemStack stack, World world) {
            return override;
        }
    }
}
