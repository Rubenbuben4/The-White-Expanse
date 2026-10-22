package net.icegiant.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.function.Supplier;

/**
 * Ice Giant spawn egg WITH its Gnoll escort (2-5 Gnolls). The normal giant eggs spawn the giant
 * alone; this one always carries the entity data {SpawnEscort:1b}, which the giant reads and then
 * calls its Gnolls (IceGiantEntity.readAdditionalSaveData / tick).
 */
public class EscortSpawnEggItem extends ForgeSpawnEggItem {

    public EscortSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor,
                              int highlightColor, Properties props) {
        super(type, backgroundColor, highlightColor, props);
    }

    private static ItemStack withEscort(ItemStack stack) {
        CompoundTag entityTag = stack.getOrCreateTagElement("EntityTag");
        entityTag.putBoolean("SpawnEscort", true);
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return withEscort(super.getDefaultInstance());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        withEscort(context.getItemInHand());
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        withEscort(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }
}
