package net.coolsimulations.FloatingItems.mixins.early;

import java.util.List;

import net.coolsimulations.FloatingItems.FIConfig;
import net.coolsimulations.FloatingItems.FIConfig.FIConfigItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityItem.class)
public abstract class EntityItemMixin extends Entity {

    public EntityItemMixin(World worldIn) {
        super(worldIn);
    }

    @Shadow
    public abstract ItemStack getEntityItem();

    @Inject(at = @At("HEAD"), method = "onUpdate", cancellable = true)
    public void onUpdate(CallbackInfo info) {

        ItemStack stack = this.getEntityItem();
        if (stack == null || stack.getItem() == null) {
            return;
        }

        if (!stack.getItem()
            .onEntityItemUpdate(((EntityItem) (Object) this))) {

            // The hook is allowed to change or clear the stack, so re-read it before the blacklist checks.
            stack = this.getEntityItem();
            if (stack == null || stack.getItem() == null) {
                return;
            }

            Item item = stack.getItem();
            int meta = stack.getItemDamage();
            boolean shouldFloat = false;

            if (FIConfig.blacklistItems != null && FIConfig.blacklistItems.length >= 1) {
                for (FIConfigItem blacklistItem : FIConfig.blacklistItems) {
                    if (blacklistItem == null || blacklistItem.getItem() == null) {
                        continue;
                    }

                    if (blacklistItem.getItem()
                        .equals(item)) {

                        if (blacklistItem.getMeta() == meta) {
                            return;
                        } else {
                            shouldFloat = true;
                        }
                    }
                }
            }

            if (!shouldFloat && FIConfig.blacklistRegistryItems != null
                && FIConfig.blacklistRegistryItems.length >= 1) {
                for (int blacklistItem : FIConfig.blacklistRegistryItems) {
                    if (blacklistItem == Item.getIdFromItem(item)) {
                        return;
                    }
                }
            }

            if (!shouldFloat && FIConfig.blacklistOreDict != null && FIConfig.blacklistOreDict.length >= 1) {
                for (String oreDict : FIConfig.blacklistOreDict) {
                    if (oreDict == null) {
                        shouldFloat = true;
                        continue;
                    }

                    String oreDictEntry = oreDict.replaceAll("[^A-Za-z0-9 ]", "");
                    List<ItemStack> ores = OreDictionary.getOres(oreDictEntry);

                    if (!ores.isEmpty()) {
                        for (ItemStack oreStack : ores) {
                            if (oreStack == null || oreStack.getItem() == null) {
                                shouldFloat = true;
                                continue;
                            }

                            if (oreStack.getItem()
                                .equals(item)) {

                                if (oreStack.getItemDamage() == meta) {
                                    return;
                                } else {
                                    shouldFloat = true;
                                }
                            } else {
                                shouldFloat = true;
                            }
                        }
                    } else {
                        shouldFloat = true;
                    }
                }
            } else {
                shouldFloat = true;
            }

            if (shouldFloat) {

                Block state = this.worldObj.getBlock((int) this.posX, (int) this.posY, (int) this.posZ);
                float eye = this.getEyeHeight() - 0.11111111F;

                if ((state.getMaterial()
                    .isLiquid() && state.getMaterial() != Material.lava)
                    && BlockLiquid.getLiquidHeightPercent(
                        this.worldObj.getBlockMetadata(
                            MathHelper.floor_double(this.posX),
                            MathHelper.floor_double(this.posY),
                            MathHelper.floor_double(this.posZ)))
                        > eye) {
                    setUnderWaterMovement(this);
                    this.motionY += 0.03999999910593033D;
                }
            }
        }
    }

    @Unique
    private static void setUnderWaterMovement(Entity entity) {

        entity.motionX *= 0.95D;
        entity.motionY += entity.motionY < 0.06D ? 5.0E-4D : 0.0D;
        entity.motionZ *= 0.95D;
    }
}
