package net.icegiant.mixin;

import net.icegiant.dimension.ModDimensions;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No vanilla lakes in the Frozen Realm - neither lava lakes (on the surface and underground) nor water lakes
 * (Rubenbuben4, 2026-10-15). Its lakes are the river system's own (RiverNetwork). Other dimensions are untouched.
 */
@Mixin(LakeFeature.class)
public abstract class LakeFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void icegiant$noLakes(FeaturePlaceContext<LakeFeature.Configuration> context, CallbackInfoReturnable<Boolean> cir) {
        if (context.level().getLevel().dimension() == ModDimensions.FROZEN_REALM) cir.setReturnValue(false);
    }
}
