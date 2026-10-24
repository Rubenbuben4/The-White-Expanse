package net.icegiant.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Horseshoe for the Frost Goat (Rubenbuben4, 2026-10-23): goes into the armour slot of its horse screen (or right-click
 * the goat with it) and makes it faster and jump higher. Iron, gold, diamond and netherite (from diamond with the netherite upgrade
 * template). Takes Soul Speed and Frost Walker (from books, at the anvil); the goat wears it on its feet, so both work
 * as on boots.
 */
public class HorseshoeItem extends Item {

    private final double speedBonus;
    private final double jumpBonus;
    private final int enchantability;

    public HorseshoeItem(Properties properties, double speedBonus, double jumpBonus, int enchantability) {
        super(properties.stacksTo(1));
        this.speedBonus = speedBonus;
        this.jumpBonus = jumpBonus;
        this.enchantability = enchantability;
    }

    /** Added to the goat's jump strength (base 0.8 = ~3.6 blocks; +0.1 = about half a block higher). */
    public double jumpBonus() {
        return this.jumpBonus;
    }

    /** Extra speed of the goat (0.1 = 10 % faster). */
    public double speedBonus() {
        return this.speedBonus;
    }

    /** "For the Frost Goat: +10 % speed, jumps higher" and the dash key. */
    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ItemTooltips.line(tooltip, "tooltip.icegiant.horseshoe", "+" + Math.round(this.speedBonus * 100) + " %");
        ItemTooltips.key(tooltip, "tooltip.icegiant.frost_goat_dash", "key.icegiant.activate_dash");
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
