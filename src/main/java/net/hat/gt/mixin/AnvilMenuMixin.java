package net.hat.gt.mixin;


import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Map;

import static com.jab125.thonkutil.util.Util.isOverflowInstalled;


/**
 * Fixes combining tools in an anvil reducing enchantment level to it's max level when the
 * level of the enchantment is higher than it's max level. For example, combining level five
 * efficiency pickaxe with a level six efficiency pickaxe (which is higher than the max) will
 * keep the enchantment at level six instead of changing to it's max level of five.
 *
 * Remapped by Jab125
 */
@Mixin(AnvilScreenHandler.class)
public class AnvilMenuMixin
{

    private int maxLevel;

    @SuppressWarnings("unchecked")
    @Inject(method = "updateResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/Enchantment;isAcceptableItem(Lnet/minecraft/item/ItemStack;)Z", ordinal = 0), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void beforeCanApply(CallbackInfo ci, ItemStack leftOriginal, int enchantCost, int repairCost, int renameCost, ItemStack leftCopy, ItemStack rightOriginal, Map leftEnchantments, boolean enchantingItem, Map rightEnchantments, boolean combinedEnchants, boolean invalidRepair, Iterator var12, Enchantment enchantment, int leftEnchantmentLevel)//, int combinedEnchantmentLevel)
    {
        int maxLevel = this.getEnchantmentLevel(enchantment);
        int leftLevel = (int) leftEnchantments.getOrDefault(enchantment, 0);
        int rightLevel = (int) rightEnchantments.get(enchantment);
        this.maxLevel = Math.max(rightLevel, leftLevel);
        if(leftLevel == rightLevel && leftLevel < maxLevel && !isOverflowInstalled())
        {
            this.maxLevel = rightLevel + 1;
        }
    }

    // Prevents mixin from targeting getMaxLevel
    private int getEnchantmentLevel(Enchantment enchantment)
    {
        return enchantment.getMaxLevel();
    }

    @ModifyArg(method = "updateResult", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), index = 1)
    private Object afterSetMaxLevel(Object o)
    {
        return this.maxLevel;
    }
}