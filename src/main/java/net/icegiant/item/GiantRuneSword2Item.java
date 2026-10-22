package net.icegiant.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Map;

/**
 * Riesen-Runenschwert 2.0 (Rubenbuben4's model, 2026-10-18): the Giant Rune Sword with its paper seals waving
 * (his "wind_v_1") and shimmering runes (animated texture, giant_rune_sword_2.png.mcmeta). Being a GiantRuneSwordItem
 * it has every ability of the first one (right click, H, J, K, N, the giant's AI). Stronger: 20 damage (+4 hearts)
 * and built-in Smite VIII (+20 against undead) - the "secret" level he asked for. It works through Forge's
 * enchantment hooks wherever vanilla reads enchantments (normal hits of players and mobs), but is not stored on the
 * stack: no tooltip line, no glint, the grindstone cannot take it off. Not in the giants' spawn mix; given to a giant
 * by hand in creative mode (IceGiantEntity#mobInteract).
 * <p>
 * Its normal hits set blue flames that water cannot put out (entity/BlueFire, since 2026-10-21) - in place of the
 * built-in Fire Aspect IV it had until then (orange fire, put out by water).
 */
public class GiantRuneSword2Item extends GiantRuneSwordItem {

    public static final String MODEL_NAME_2 = "giant_rune_sword_2";

    private static final Map<Enchantment, Integer> BUILT_IN = Map.of(Enchantments.SMITE, 8);

    public GiantRuneSword2Item(Properties properties, double totalAttackDamage, double attackSpeedModifier) {
        super(properties, totalAttackDamage, attackSpeedModifier, MODEL_NAME_2);
    }

    /** EnchantmentHelper.getItemEnchantmentLevel asks this: stack level or built in, the higher. */
    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        return HiddenEnchantments.level(super.getEnchantmentLevel(stack, enchantment), BUILT_IN.get(enchantment));
    }

    /** EnchantmentHelper's damage bonus (e.g. Smite) iterates this: the stack's own plus the built-in ones. */
    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        return HiddenEnchantments.merge(super.getAllEnchantments(stack), BUILT_IN);
    }
}
