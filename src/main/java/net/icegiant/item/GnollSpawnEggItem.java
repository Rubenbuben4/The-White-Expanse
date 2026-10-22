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
 * Spawn egg for one Gnoll look (snow / normal / warm): a normal spawn egg that always carries
 * the entity data {Variant:n}, which vanilla applies to the spawned Gnoll
 * (GnollEntity.readAdditionalSaveData).
 */
public class GnollSpawnEggItem extends ForgeSpawnEggItem {

    private final byte variant;

    public GnollSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int variant, int backgroundColor,
                              int highlightColor, Properties props) {
        super(type, backgroundColor, highlightColor, props);
        this.variant = (byte) variant;
    }

    private ItemStack withVariant(ItemStack stack) {
        CompoundTag entityTag = stack.getOrCreateTagElement("EntityTag");
        entityTag.putByte("Variant", this.variant);
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return this.withVariant(super.getDefaultInstance());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        this.withVariant(context.getItemInHand());
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        this.withVariant(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }
}
