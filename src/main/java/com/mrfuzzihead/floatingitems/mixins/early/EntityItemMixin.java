package com.mrfuzzihead.floatingitems.mixins.early;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mrfuzzihead.floatingitems.FIConfig;
import com.mrfuzzihead.floatingitems.FloatingItemPolicy;
import com.mrfuzzihead.floatingitems.api.DebugCounters;
import com.mrfuzzihead.floatingitems.api.ItemFloatCheckEvent;
import com.mrfuzzihead.floatingitems.api.ItemFloatTickEvent;

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
        if (handledByItem) {
            return true;
        }

        DebugCounters.itemEvaluated();
        ItemStack stack = entityItem.getEntityItem();
        ItemFloatCheckEvent checkEvent = new ItemFloatCheckEvent(
            entityItem,
            stack,
            entityItem.worldObj,
            entityItem.boundingBox);
        MinecraftForge.EVENT_BUS.post(checkEvent);
        if (!checkEvent.isCanceled() && FloatingItemPolicy.shouldFloat(
            stack,
            entityItem.worldObj,
            entityItem.boundingBox,
            FIConfig.blacklist,
            FIConfig.liquidRules,
            FIConfig.filterMode,
            FIConfig.buoyancy,
            FIConfig.scopeRules)) {
            DebugCounters.itemFloated();
            FloatingItemPolicy.applyBuoyancy(entityItem, FIConfig.buoyancy);
            MinecraftForge.EVENT_BUS
                .post(new ItemFloatTickEvent(entityItem, stack, entityItem.worldObj, entityItem.boundingBox));
        }
        return false;
    }
}
