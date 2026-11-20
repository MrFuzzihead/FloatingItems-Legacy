package com.mrfuzzihead.floatingitems;

import net.minecraft.block.material.Material;
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
 * delegates the blacklist/water decision here, and applies the motion helper when the decision is positive.
 * </p>
 */
public final class FloatingItemPolicy {

    static final double BUOYANCY_LIFT = 0.03999999910593033D;
    static final double HORIZONTAL_DAMPING = 0.95D;
    static final double SLOW_LIFT = 5.0E-4D;
    static final double SLOW_LIFT_THRESHOLD = 0.06D;

    private FloatingItemPolicy() {}

    /**
     * @param stack     the post-hook item stack
     * @param world     the entity world
     * @param box       the entity bounding box
     * @param blacklist the immutable blacklist snapshot, or {@code null}
     * @return true when the item is not blacklisted and is touching water
     */
    public static boolean shouldFloat(ItemStack stack, World world, AxisAlignedBB box, BlacklistSnapshot<?> blacklist) {

        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (blacklist != null && blacklist.isBlacklisted(stack)) {
            return false;
        }
        return isInWater(world, box);
    }

    /**
     * @param world the entity world
     * @param box   the entity bounding box
     * @return true when the box overlaps a water block
     */
    public static boolean isInWater(World world, AxisAlignedBB box) {

        if (world == null || box == null) {
            return false;
        }
        return world.isMaterialInBB(box.contract(0.001D, 0.001D, 0.001D), Material.water);
    }

    /**
     * Applies the historical FloatingItems motion profile.
     *
     * @param entity the item entity
     */
    public static void applyBuoyancy(Entity entity) {

        entity.motionX *= HORIZONTAL_DAMPING;
        entity.motionY += entity.motionY < SLOW_LIFT_THRESHOLD ? SLOW_LIFT : 0.0D;
        entity.motionZ *= HORIZONTAL_DAMPING;
        entity.motionY += BUOYANCY_LIFT;
    }
}
