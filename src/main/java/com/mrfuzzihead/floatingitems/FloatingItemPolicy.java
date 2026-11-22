package com.mrfuzzihead.floatingitems;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.mrfuzzihead.floatingitems.api.BlacklistSnapshot;

/**
 * Pure(ish) floating rules shared by the mixin and the unit tests.
 *
 * <p>
 * This class deliberately has no dependency on a mixin-transformed entity. The mixin only reads the post-hook stack,
 * delegates the blacklist/liquid decision here, and applies the motion helper when the decision is positive.
 * </p>
 */
public final class FloatingItemPolicy {

    static final double SLOW_LIFT = 5.0E-4D;
    static final double SLOW_LIFT_THRESHOLD = 0.06D;

    private FloatingItemPolicy() {}

    /**
     * @param stack     the post-hook item stack
     * @param world     the entity world
     * @param box       the entity bounding box
     * @param blacklist the immutable blacklist snapshot, or {@code null}
     * @return true when the item is not blacklisted and is touching an allowed liquid
     */
    public static boolean shouldFloat(ItemStack stack, World world, AxisAlignedBB box, BlacklistSnapshot<?> blacklist) {

        return shouldFloat(
            stack,
            world,
            box,
            blacklist,
            LiquidRules.defaults(),
            ItemFilterMode.BLACKLIST,
            BuoyancySettings.defaults());
    }

    /**
     * @param stack       the post-hook item stack
     * @param world       the entity world
     * @param box         the entity bounding box
     * @param blacklist   the immutable item filter snapshot, or {@code null}
     * @param liquidRules the immutable liquid policy, or {@code null} for the safe default
     * @return true when the item passes the blacklist filter and is touching an allowed liquid
     */
    public static boolean shouldFloat(ItemStack stack, World world, AxisAlignedBB box, BlacklistSnapshot<?> blacklist,
        LiquidRules liquidRules) {

        return shouldFloat(
            stack,
            world,
            box,
            blacklist,
            liquidRules,
            ItemFilterMode.BLACKLIST,
            BuoyancySettings.defaults());
    }

    /**
     * @param stack       the post-hook item stack
     * @param world       the entity world
     * @param box         the entity bounding box
     * @param itemFilter  the immutable item filter snapshot, or {@code null}
     * @param liquidRules the immutable liquid policy, or {@code null} for the safe default
     * @param filterMode  the item filter mode, or {@code null} for blacklist mode
     * @return true when the item passes the item filter and is touching an allowed liquid
     */
    public static boolean shouldFloat(ItemStack stack, World world, AxisAlignedBB box, BlacklistSnapshot<?> itemFilter,
        LiquidRules liquidRules, ItemFilterMode filterMode) {

        return shouldFloat(stack, world, box, itemFilter, liquidRules, filterMode, BuoyancySettings.defaults());
    }

    /**
     * @param stack       the post-hook item stack
     * @param world       the entity world
     * @param box         the entity bounding box
     * @param itemFilter  the immutable item filter snapshot, or {@code null}
     * @param liquidRules the immutable liquid policy, or {@code null} for the safe default
     * @param filterMode  the item filter mode, or {@code null} for blacklist mode
     * @param buoyancy    the buoyancy settings, or {@code null} for the safe default
     * @return true when the item passes the item filter and is touching an allowed liquid
     */
    public static boolean shouldFloat(ItemStack stack, World world, AxisAlignedBB box, BlacklistSnapshot<?> itemFilter,
        LiquidRules liquidRules, ItemFilterMode filterMode, BuoyancySettings buoyancy) {

        if (stack == null || stack.getItem() == null) {
            return false;
        }

        ItemFilterMode mode = filterMode == null ? ItemFilterMode.BLACKLIST : filterMode;
        boolean listed = itemFilter != null && itemFilter.isBlacklisted(stack);
        if (mode == ItemFilterMode.WHITELIST ? !listed : listed) {
            return false;
        }

        LiquidRules rules = liquidRules == null ? LiquidRules.defaults() : liquidRules;
        BuoyancySettings settings = buoyancy == null ? BuoyancySettings.defaults() : buoyancy;
        return isInFloatingLiquid(world, box, rules, settings);
    }

    /**
     * @param world the entity world
     * @param box   the entity bounding box
     * @return true when the box overlaps a liquid allowed by the default policy
     * @deprecated use {@link #isInFloatingLiquid(World, AxisAlignedBB, LiquidRules)}
     */
    @Deprecated
    public static boolean isInWater(World world, AxisAlignedBB box) {

        return isInFloatingLiquid(world, box, LiquidRules.defaults());
    }

    /**
     * @param world       the entity world
     * @param box         the entity bounding box
     * @param liquidRules the immutable liquid policy, or {@code null} for the safe default
     * @return true when the box overlaps a liquid allowed by the policy
     */
    public static boolean isInFloatingLiquid(World world, AxisAlignedBB box, LiquidRules liquidRules) {

        return isInFloatingLiquid(world, box, liquidRules, BuoyancySettings.defaults());
    }

    /**
     * @param world       the entity world
     * @param box         the entity bounding box
     * @param liquidRules the immutable liquid policy, or {@code null} for the safe default
     * @param buoyancy    the buoyancy settings, or {@code null} for the safe default
     * @return true when the box overlaps a liquid allowed by the policy, after applying the surface offset
     */
    public static boolean isInFloatingLiquid(World world, AxisAlignedBB box, LiquidRules liquidRules,
        BuoyancySettings buoyancy) {

        LiquidRules rules = liquidRules == null ? LiquidRules.defaults() : liquidRules;
        BuoyancySettings settings = buoyancy == null ? BuoyancySettings.defaults() : buoyancy;
        return rules.isInFloatingLiquid(world, applySurfaceOffset(box, settings.getSurfaceOffset()));
    }

    /**
     * Applies the default FloatingItems motion profile.
     *
     * @param entity the item entity
     */
    public static void applyBuoyancy(Entity entity) {

        applyBuoyancy(entity, BuoyancySettings.defaults());
    }

    /**
     * Applies the configured FloatingItems motion profile.
     *
     * @param entity   the item entity
     * @param buoyancy the buoyancy settings, or {@code null} for the safe default
     */
    public static void applyBuoyancy(Entity entity, BuoyancySettings buoyancy) {

        BuoyancySettings settings = buoyancy == null ? BuoyancySettings.defaults() : buoyancy;
        entity.motionX *= settings.getHorizontalDamping();
        entity.motionY += entity.motionY < SLOW_LIFT_THRESHOLD ? SLOW_LIFT : 0.0D;
        entity.motionZ *= settings.getHorizontalDamping();
        entity.motionY += settings.getLift();
        if (entity.motionY > settings.getMaxUpwardSpeed()) {
            entity.motionY = settings.getMaxUpwardSpeed();
        }
    }

    private static AxisAlignedBB applySurfaceOffset(AxisAlignedBB box, double surfaceOffset) {
        if (box == null || surfaceOffset == 0.0D) {
            return box;
        }
        double minY = box.minY - surfaceOffset;
        if (minY >= box.maxY) {
            minY = box.maxY - 1.0E-4D;
        }
        return AxisAlignedBB.getBoundingBox(box.minX, minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
