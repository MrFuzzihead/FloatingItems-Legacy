package com.mrfuzzihead.floatingitems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import org.junit.Test;

public class LiquidRulesTest {

    @Test
    public void defaultPolicyIsWaterLike() {
        LiquidRules rules = LiquidRules.defaults();

        assertEquals(LiquidPolicy.WATER_LIKE, rules.getPolicy());
        assertTrue(
            rules.getAllowlist()
                .isEmpty());
        assertTrue(
            rules.getDenylist()
                .isEmpty());
        assertTrue(rules.allowsFluid(fluid("water", 1000, 300, 1000), waterBlock()));
        assertTrue(rules.allowsFluid(fluid("salt_water", 1000, 300, 1000), waterBlock()));
        assertFalse(rules.allowsFluid(fluid("oil", 900, 300, 1200), waterBlock()));
    }

    @Test
    public void waterPolicyRejectsOilEvenWhenItsBlockMaterialIsWater() {
        LiquidRules rules = new LiquidRules(
            LiquidPolicy.WATER,
            Collections.<String>emptyList(),
            Collections.<String>emptyList());
        TestFluidBlock oilyWaterBlock = new TestFluidBlock(fluid("oil", 900, 300, 1200), Material.water);

        assertFalse(rules.allowsBlock(oilyWaterBlock));
    }

    @Test
    public void materialWaterPolicyKeepsLegacyOilButRejectsLava() {
        LiquidRules rules = new LiquidRules(
            LiquidPolicy.MATERIAL_WATER,
            Collections.<String>emptyList(),
            Collections.<String>emptyList());

        assertTrue(rules.allowsBlock(new TestFluidBlock(fluid("oil", 900, 300, 1200), Material.water)));
        assertFalse(rules.allowsBlock(new TestFluidBlock(fluid("lava", 3000, 1300, 6000), Material.lava)));
    }

    @Test
    public void waterLikePolicyAcceptsWaterNamedFluidsWithinBounds() {
        LiquidRules rules = new LiquidRules(
            LiquidPolicy.WATER_LIKE,
            Collections.<String>emptyList(),
            Collections.<String>emptyList());

        assertTrue(rules.allowsFluid(fluid("salt_water", 1000, 300, 1000), waterBlock()));
        assertTrue(rules.allowsFluid(fluid("purified_water", 900, 350, 1400), waterBlock()));
    }

    @Test
    public void waterLikePolicyRejectsOilHotHeavyGaseousAndThickFluids() {
        LiquidRules rules = new LiquidRules(
            LiquidPolicy.WATER_LIKE,
            Collections.<String>emptyList(),
            Collections.<String>emptyList());

        assertFalse(rules.allowsFluid(fluid("oil", 1000, 300, 1000), waterBlock()));
        assertFalse(rules.allowsFluid(fluid("hot_water", 1000, 1300, 1000), waterBlock()));
        assertFalse(rules.allowsFluid(fluid("heavy_water", 2000, 300, 1000), waterBlock()));
        assertFalse(rules.allowsFluid(fluid("thick_water", 1000, 300, 2000), waterBlock()));
        assertFalse(rules.allowsFluid(fluid("gaseous_water", 1000, 300, 1000).setGaseous(true), waterBlock()));
    }

    @Test
    public void allowlistOverridesThePolicyAndDenylistWins() {
        LiquidRules allowed = new LiquidRules(
            LiquidPolicy.WATER,
            Arrays.asList(" Oil ", "OIL"),
            Collections.<String>emptyList());
        assertTrue(allowed.allowsFluid(fluid("oil", 900, 300, 1200), waterBlock()));
        assertEquals(Collections.singleton("oil"), allowed.getAllowlist());

        LiquidRules denied = new LiquidRules(
            LiquidPolicy.WATER,
            Collections.singletonList("oil"),
            Collections.singletonList("OIL"));
        assertFalse(denied.allowsFluid(fluid("oil", 900, 300, 1200), waterBlock()));
    }

    @Test
    public void simpleMaterialWaterBlocksStillFloat() {
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        Block lava = mock(Block.class);
        when(lava.getMaterial()).thenReturn(Material.lava);

        LiquidRules rules = LiquidRules.defaults();
        assertTrue(rules.allowsBlock(water));
        assertFalse(rules.allowsBlock(lava));
    }

    @Test
    public void worldScanFindsAnAllowedFluid() {
        World world = mock(World.class);
        TestFluidBlock block = new TestFluidBlock(fluid("water", 1000, 300, 1000), Material.water);
        when(world.getBlock(0, 0, 0)).thenReturn(block);

        assertTrue(
            LiquidRules.defaults()
                .isInFloatingLiquid(world, AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 0.25D, 0.25D, 0.25D)));
    }

    @Test
    public void contractedBoxDoesNotTouchTheBlockAboveTheSurface() {
        World world = mock(World.class);
        Block water = mock(Block.class);
        when(water.getMaterial()).thenReturn(Material.water);
        when(world.getBlock(0, 1, 0)).thenReturn(water);

        assertTrue(
            LiquidRules.defaults()
                .isInFloatingLiquid(world, AxisAlignedBB.getBoundingBox(0.0D, 0.999D, 0.0D, 0.25D, 1.5D, 0.25D)));
        verify(world, never()).getBlock(0, 0, 0);
    }

    @Test
    public void nullWorldOrBoxIsNotInALiquid() {
        assertFalse(
            LiquidRules.defaults()
                .isInFloatingLiquid(null, AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1)));
        assertFalse(
            LiquidRules.defaults()
                .isInFloatingLiquid(mock(World.class), null));
    }

    private static Block waterBlock() {
        return new TestFluidBlock(fluid("water", 1000, 300, 1000), Material.water);
    }

    private static Fluid fluid(String name, int density, int temperature, int viscosity) {
        return new Fluid(name).setDensity(density)
            .setTemperature(temperature)
            .setViscosity(viscosity);
    }

    private static final class TestFluidBlock extends Block implements IFluidBlock {

        private final Fluid fluid;

        TestFluidBlock(Fluid fluid, Material material) {
            super(material);
            this.fluid = fluid;
        }

        @Override
        public Fluid getFluid() {
            return fluid;
        }

        @Override
        public FluidStack drain(World world, int x, int y, int z, boolean doDrain) {
            return null;
        }

        @Override
        public boolean canDrain(World world, int x, int y, int z) {
            return false;
        }

        @Override
        public float getFilledPercentage(World world, int x, int y, int z) {
            return 1.0F;
        }
    }
}
