package net.icegiant.mixin;

import net.icegiant.worldgen.NorwayTerrain;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The aquifers keep the deep dark dry - no water or lava pockets, so ancient cities don't fill up. Minecraft finds the
 * deep dark by its erosion and depth noise; the Frozen Realm's erosion means something else (how far from the shore),
 * so there the deep dark is told by its depth alone: the noise router adds 0.5 to the depth in the deep dark's patches
 * (noise_settings/frozen_realm.json, "depth"; the biome source puts the deep dark at depth 1.45 - 1.75).
 * Recognised by the router's erosion being our own terrain function; other dimensions are untouched.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class FrozenRealmDeepDarkMixin {

    private static final double DEEP_DARK_DEPTH = 1.4;   // (no other place of the Frozen Realm is this deep)

    @Inject(method = "isDeepDarkRegion", at = @At("HEAD"), cancellable = true)
    private static void icegiant$frozenRealmDeepDark(DensityFunction erosion, DensityFunction depth,
                                                     DensityFunction.FunctionContext context,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (erosion instanceof NorwayTerrain) cir.setReturnValue(depth.compute(context) > DEEP_DARK_DEPTH);
    }
}
