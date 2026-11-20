package net.coolsimulations.FloatingItems.mixins.early;

import net.coolsimulations.FloatingItems.FIConfig;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityItem.class)
public abstract class EntityItemMixin {

    /**
     * Calls the item's own entity-update hook exactly once, then applies the floating rules only if the hook
     * returned false. Vanilla already owns the hook call and its early return; a HEAD injection called the hook a
     * second time (B-06).
     */
    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;onEntityItemUpdate(Lnet/minecraft/entity/item/EntityItem;)Z"))
    private boolean floatingItems$afterEntityItemUpdate(Item item, EntityItem entityItem) {

        boolean handledByItem = item.onEntityItemUpdate(entityItem);
        if (!handledByItem) {
            applyFloating(entityItem);
        }
        return handledByItem;
    }

    @Unique
    private void applyFloating(EntityItem entityItem) {

        ItemStack stack = entityItem.getEntityItem();
        if (stack == null || stack.getItem() == null) {
            return;
        }
        if (FIConfig.blacklist != null && FIConfig.blacklist.isBlacklisted(stack)) {
            return;
        }
        if (!isInWater(entityItem)) {
            return;
        }

        setUnderWaterMovement(entityItem);
        entityItem.motionY += 0.03999999910593033D;
    }

    /**
     * Uses the entity's bounding box, matching {@link Entity#handleWaterMovement()}, instead of a single block at
     * the item's feet. This also avoids the negative-coordinate truncation bug (B-08) and the dead eye-height /
     * liquid-metadata check (B-17/B-21).
     */
    @Unique
    private static boolean isInWater(EntityItem entityItem) {

        return entityItem.worldObj
            .isMaterialInBB(entityItem.boundingBox.contract(0.001D, 0.001D, 0.001D), Material.water);
    }

    @Unique
    private static void setUnderWaterMovement(Entity entity) {

        entity.motionX *= 0.95D;
        entity.motionY += entity.motionY < 0.06D ? 5.0E-4D : 0.0D;
        entity.motionZ *= 0.95D;
    }
}
