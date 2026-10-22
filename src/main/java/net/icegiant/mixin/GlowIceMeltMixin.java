package net.icegiant.mixin;

import net.icegiant.block.GlowIceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ice does not melt in the light of glow ice (Rubenbuben4, 2026-10-22: "the Glowing ice should not be able to melt
 * ice as right now the ice spire biome is melting"). Vanilla melts ice in block light above 10 on a random tick; when
 * glow ice is what gives that light (it lies within GlowIceBlock.COLD_REACH), the tick is skipped. Torches and other
 * light still melt ice as before.
 */
@Mixin(IceBlock.class)
public abstract class GlowIceMeltMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void icegiant$coldLight(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 10 && GlowIceBlock.coldLightNear(level, pos)) ci.cancel();
    }
}
