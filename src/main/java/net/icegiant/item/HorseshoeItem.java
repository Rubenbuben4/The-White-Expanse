package net.icegiant.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Horseshoe for the Frost Goat (Rubenbuben4, 2026-10-23): goes into the armour slot of its horse screen (or right-click
 * the goat with it) and makes it faster. Iron, gold, diamond and netherite (from diamond with the netherite upgrade
 * template). Takes Soul Speed and Frost Walker (from books, at the anvil); the goat wears it on its feet, so both work
 * as on boots.
 */
public class HorseshoeItem extends Item {

    private final double speedBonus;
    private final int enchantability;

    public HorseshoeItem(Properties properties, double speedBonus, int enchantability) {
        super(properties.stacksTo(1));
        this.speedBonus = speedBonus;
        this.enchantability = enchantability;
    }

    /** Extra speed of the goat (0.1 = 10 % faster). */
    public double speedBonus() {
        return this.speedBonus;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.SOUL_SPEED || enchantment == Enchantments.FROST_WALKER;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantability;
    }
}
