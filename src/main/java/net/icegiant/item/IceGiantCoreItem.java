package net.icegiant.item;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gravierter Eisriesen-Kern (engraved Ice Giant core). Besides its old uses (armour, repairs) it can now carry a biome
 * for the Schöpfungskristalle (Rubenbuben4, 2026-10-25): right-click takes in the biome you stand in, sneak +
 * right-click lets it go again. A core with a biome shimmers and names it in its tooltip.
 */
public class IceGiantCoreItem extends Item {

    public static final String BIOME_TAG = "CrystalBiome";

    public IceGiantCoreItem(Properties properties) {
        super(properties);
    }

    /** The biome this core carries, or null. */
    @Nullable
    public static ResourceLocation biome(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(BIOME_TAG) ? ResourceLocation.tryParse(tag.getString(BIOME_TAG)) : null;
    }

    public static Component biomeName(ResourceLocation biome) {
        return Component.translatable(Util.makeDescriptionId("biome", biome));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (biome(stack) == null) return InteractionResultHolder.pass(stack);
            if (!level.isClientSide()) {
                stack.removeTagKey(BIOME_TAG);
                player.displayClientMessage(Component.translatable("message.icegiant.core.cleared").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!level.isClientSide()) {
            ResourceLocation here = level.getBiome(player.blockPosition()).unwrapKey().map(ResourceKey::location).orElse(null);
            if (here == null) return InteractionResultHolder.pass(stack);
            stack.getOrCreateTag().putString(BIOME_TAG, here.toString());
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("message.icegiant.core.charged", biomeName(here))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return biome(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation biome = biome(stack);
        if (biome != null) {
            tooltip.add(Component.translatable("tooltip.icegiant.core.biome", biomeName(biome)).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.icegiant.core.clear").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.icegiant.core.charge").withStyle(ChatFormatting.GRAY));
        }
    }
}
