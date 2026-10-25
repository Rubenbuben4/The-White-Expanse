package net.icegiant.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** Schöpfungskristall items: the lore and what they do in the tooltip. */
public class CreationCrystalItem extends BlockItem {

    private final int radius;
    private final boolean large;

    public CreationCrystalItem(Block block, Properties properties, int radius, boolean large) {
        super(block, properties);
        this.radius = radius;
        this.large = large;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.icegiant.creation_crystal.lore").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.icegiant.creation_crystal.radius", this.radius).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.icegiant.creation_crystal.use").withStyle(ChatFormatting.GRAY));
        if (this.large) tooltip.add(Component.translatable("tooltip.icegiant.creation_crystal.space").withStyle(ChatFormatting.DARK_GRAY));
    }
}
