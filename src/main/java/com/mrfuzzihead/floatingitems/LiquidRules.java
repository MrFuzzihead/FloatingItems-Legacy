package com.mrfuzzihead.floatingitems;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.IFluidBlock;

/**
 * Immutable liquid policy used by the item tick.
 *
 * <p>
 * The default policy is {@link LiquidPolicy#WATER_LIKE}: vanilla water plus water-named Forge fluids whose density,
 * temperature and viscosity are close to water. Forge fluid blocks are identified through {@link IFluidBlock}. The
 * legacy {@link LiquidPolicy#MATERIAL_WATER} mode is available for packs that rely on the old material-only check.
 * </p>
 */
public final class LiquidRules {

    private static final LiquidRules DEFAULTS = new LiquidRules(
        LiquidPolicy.WATER_LIKE,
        Collections.<String>emptyList(),
        Collections.<String>emptyList());

    private static final int WATER_LIKE_MIN_DENSITY = 800;
    private static final int WATER_LIKE_MAX_DENSITY = 1200;
    private static final int WATER_LIKE_MIN_TEMPERATURE = 250;
    private static final int WATER_LIKE_MAX_TEMPERATURE = 400;
    private static final int WATER_LIKE_MIN_VISCOSITY = 500;
    private static final int WATER_LIKE_MAX_VISCOSITY = 1500;

    private final LiquidPolicy policy;
    private final Set<String> allowlist;
    private final Set<String> denylist;

    public LiquidRules(LiquidPolicy policy, Collection<String> allowlist, Collection<String> denylist) {
        this.policy = policy == null ? LiquidPolicy.WATER_LIKE : policy;
        this.allowlist = normalize(allowlist);
        this.denylist = normalize(denylist);
    }

    public static LiquidRules defaults() {
        return DEFAULTS;
    }

    public LiquidPolicy getPolicy() {
        return policy;
    }

    public Set<String> getAllowlist() {
        return allowlist;
    }

    public Set<String> getDenylist() {
        return denylist;
    }

    /**
     * @param world the entity world
     * @param box   the entity bounding box
     * @return true when an allowed liquid block overlaps the box
     */
    public boolean isInFloatingLiquid(World world, AxisAlignedBB box) {
        if (world == null || box == null) {
            return false;
        }

        AxisAlignedBB checked = box.contract(0.001D, 0.001D, 0.001D);
        int minX = MathHelper.floor_double(checked.minX);
        int maxX = MathHelper.floor_double(checked.maxX);
        int minY = MathHelper.floor_double(checked.minY);
        int maxY = MathHelper.floor_double(checked.maxY);
        int minZ = MathHelper.floor_double(checked.minZ);
        int maxZ = MathHelper.floor_double(checked.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (allowsBlock(world.getBlock(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean allowsBlock(Block block) {
        if (block == null) {
            return false;
        }

        if (block instanceof IFluidBlock) {
            Fluid fluid = ((IFluidBlock) block).getFluid();
            if (fluid != null) {
                return allowsFluid(fluid, block);
            }
        }

        // Vanilla water and simple modded water blocks do not implement IFluidBlock.
        return block.getMaterial() == Material.water;
    }

    boolean allowsFluid(Fluid fluid, Block block) {
        if (fluid == null) {
            return false;
        }

        String name = normalize(fluid.getName());
        if (denylist.contains(name)) {
            return false;
        }
        if (allowlist.contains(name) || "water".equals(name)) {
            return true;
        }

        switch (policy) {
            case WATER:
                return false;
            case MATERIAL_WATER:
                return block != null && block.getMaterial() == Material.water;
            case WATER_LIKE:
                return isWaterLike(name, fluid);
            default:
                return false;
        }
    }

    private static boolean isWaterLike(String name, Fluid fluid) {
        if (fluid.isGaseous() || !name.contains("water")) {
            return false;
        }

        int density = fluid.getDensity();
        int temperature = fluid.getTemperature();
        int viscosity = fluid.getViscosity();
        return density >= WATER_LIKE_MIN_DENSITY && density <= WATER_LIKE_MAX_DENSITY
            && temperature >= WATER_LIKE_MIN_TEMPERATURE
            && temperature <= WATER_LIKE_MAX_TEMPERATURE
            && viscosity >= WATER_LIKE_MIN_VISCOSITY
            && viscosity <= WATER_LIKE_MAX_VISCOSITY;
    }

    private static Set<String> normalize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.unmodifiableSet(new HashSet<String>());
        }
        Set<String> normalized = new HashSet<String>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String name = normalize(value);
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalize(String value) {
        return value == null ? ""
            : value.trim()
                .toLowerCase(Locale.ROOT);
    }
}
