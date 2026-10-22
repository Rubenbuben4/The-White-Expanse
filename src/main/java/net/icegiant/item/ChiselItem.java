package net.icegiant.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Meißel - crafting tool for engraving (e.g. raw Ice Giant Core -> engraved core).
 * It is not used up by a recipe: it stays in the crafting grid and loses one
 * point of durability per craft, breaking when durability runs out.
 */
public class ChiselItem extends Item {

    public ChiselItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack worn = stack.copy();
        worn.setCount(1);
        worn.setDamageValue(worn.getDamageValue() + 1);
        if (worn.getDamageValue() >= worn.getMaxDamage()) {
            return ItemStack.EMPTY; // chisel breaks
        }
        return worn;
    }
}
