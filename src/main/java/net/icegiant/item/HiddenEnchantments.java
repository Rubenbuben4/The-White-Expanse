package net.icegiant.item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in ("hidden") enchantments of an item, merged with the enchantments really on the stack: the higher level
 * wins. The Giant Rune Sword 2.0 (Rubenbuben4, 2026-10-18) has Fire Aspect IV and Smite VIII this way - they work
 * like enchantments, but are not stored on the stack, so no tooltip line, no glint and nothing for the grindstone.
 * No Minecraft classes here (the keys are generic), so it can be checked outside the game (test/HiddenEnchantmentsTest).
 */
public final class HiddenEnchantments {

    private HiddenEnchantments() {
    }

    /** Level of one enchantment: the stack's own level or the built-in one, whichever is higher (null: none built in). */
    public static int level(int onStack, Integer builtIn) {
        return builtIn == null ? onStack : Math.max(onStack, builtIn);
    }

    /** All enchantments: the stack's own plus the built-in ones, the higher level of each. The inputs stay unchanged. */
    public static <K> Map<K, Integer> merge(Map<K, Integer> onStack, Map<K, Integer> builtIn) {
        Map<K, Integer> all = new LinkedHashMap<>(onStack);
        builtIn.forEach((key, lvl) -> all.merge(key, lvl, Math::max));
        return all;
    }
}
