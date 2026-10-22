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
 * Ice Giant spawn egg whose giant can NOT use the wall-breaker dash. It is a normal spawn egg
 * that always carries the entity data {CanDash:false}; vanilla applies that data to the
 * spawned giant (IceGiantEntity.readAdditionalSaveData).
 */
public class NoDashSpawnEggItem extends ForgeSpawnEggItem {

    public NoDashSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor,
                              int highlightColor, Properties props) {
        super(type, backgroundColor, highlightColor, props);
    }

    private static ItemStack withNoDash(ItemStack stack) {
        CompoundTag entityTag = stack.getOrCreateTagElement("EntityTag");
        entityTag.putBoolean("CanDash", false);
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return withNoDash(super.getDefaultInstance());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        withNoDash(context.getItemInHand());
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        withNoDash(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }
}
