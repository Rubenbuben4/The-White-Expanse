package net.icegiant.mixin;

import net.icegiant.block.GlowIceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snow layers do not melt in the light of glow ice either (see GlowIceMeltMixin): vanilla melts them in block light
 * above 11 on a random tick; near glow ice the tick is skipped.
 */
@Mixin(SnowLayerBlock.class)
public abstract class GlowIceSnowMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void icegiant$coldLight(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11 && GlowIceBlock.coldLightNear(level, pos)) ci.cancel();
    }
}
