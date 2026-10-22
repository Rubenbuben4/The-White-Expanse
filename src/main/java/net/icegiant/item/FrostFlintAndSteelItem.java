package net.icegiant.item;

import net.icegiant.dimension.FrostPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * "Frost-Feuerstein" - lights a Frost Portal: build a rectangular Blue Ice
 * frame of (almost) any size, right-click the empty pocket inside it (or one
 * of the frame's inner faces) with this, and it lights up into a Frost Portal
 * to the Frozen Realm. See {@link FrostPortalShape} for the frame detection.
 * <p>
 * Single use: one item is consumed per lit portal (creative mode excepted),
 * nothing happens - and nothing is consumed - if there is no valid frame.
 */
public class FrostFlintAndSteelItem extends Item {

    public FrostFlintAndSteelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // Same convention as vanilla flint and steel: the position that
        // actually gets checked/lit is the empty space just past the
        // clicked face, not the clicked block itself.
        BlockPos checkPos = context.getClickedPos().relative(context.getClickedFace());

        if (!level.getBlockState(checkPos).isAir()) {
            return InteractionResult.PASS;
        }

        Optional<FrostPortalShape> shape = FrostPortalShape.findEmptyPortalShape(level, checkPos);
        if (shape.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            shape.get().fill(level);
            level.playSound(null, checkPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F,
                    1.3F + level.getRandom().nextFloat() * 0.2F);

            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().instabuild) {
                // Single use: the item is consumed when it lights a portal (like a fire charge).
                context.getItemInHand().shrink(1);
            }
        } else {
            level.playSound(context.getPlayer(), checkPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F,
                    1.3F + level.getRandom().nextFloat() * 0.2F);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
