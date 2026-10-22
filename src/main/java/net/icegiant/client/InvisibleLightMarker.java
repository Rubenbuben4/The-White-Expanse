package net.icegiant.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Client side: shows where invisible light blocks are while a creative player holds their item. */
public final class InvisibleLightMarker {

    private InvisibleLightMarker() {
    }

    public static void maybeShow(BlockState state, Level level, BlockPos pos, Item item) {
        if (!level.isClientSide()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getAbilities().instabuild) return;
        if (!player.getMainHandItem().is(item) && !player.getOffhandItem().is(item)) return;
        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
    }
}
