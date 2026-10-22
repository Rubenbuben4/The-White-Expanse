package net.icegiant.mixin;

import net.icegiant.dimension.ModDimensions;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.IcebergFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No vanilla icebergs in the Frozen Realm (2026-10-21): its sea has its own, after Rubenbuben4's pictures
 * (worldgen/Icebergs, built by SnowCover). Other dimensions keep the vanilla ones.
 */
@Mixin(IcebergFeature.class)
public abstract class IcebergFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void icegiant$noVanillaIcebergs(FeaturePlaceContext<BlockStateConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
        if (context.level().getLevel().dimension() == ModDimensions.FROZEN_REALM) cir.setReturnValue(false);
    }
}
