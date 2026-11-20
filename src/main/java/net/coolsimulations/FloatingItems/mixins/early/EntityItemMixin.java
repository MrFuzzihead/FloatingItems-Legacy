package net.coolsimulations.FloatingItems.mixins.early;

import net.coolsimulations.FloatingItems.FIConfig;
import net.coolsimulations.FloatingItems.FloatingItemPolicy;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;

import org.spongepowered.asm.mixin.Mixin;
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
        if (!handledByItem && FloatingItemPolicy
            .shouldFloat(entityItem.getEntityItem(), entityItem.worldObj, entityItem.boundingBox, FIConfig.blacklist)) {
            FloatingItemPolicy.applyBuoyancy(entityItem);
        }
        return handledByItem;
    }
}
