package net.icegiant.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Grey hint lines under our weapons, the armour and the horseshoes (2026-10-24): which key does what. The key is shown
 * with the player's own binding ({@link Component#keybind}), so it stays right when they change it in the controls.
 */
public final class ItemTooltips {

    private ItemTooltips() {
    }

    /** "[H]: Ground Slam" - {@code lineKey} gets the key as its %s. */
    public static void key(List<Component> tooltip, String lineKey, String keyMapping) {
        tooltip.add(Component.translatable(lineKey, Component.keybind(keyMapping).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
    }

    public static void line(List<Component> tooltip, String lineKey, Object... args) {
        tooltip.add(Component.translatable(lineKey, args).withStyle(ChatFormatting.GRAY));
    }
}
