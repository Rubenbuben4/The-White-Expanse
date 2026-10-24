package net.icegiant.mixin;

import net.icegiant.dimension.ModDimensions;
import net.icegiant.worldgen.Boulders;
import net.icegiant.worldgen.SnowCover;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the Frozen Realm the vanilla "freeze_top_layer" step (one snow layer everywhere) is replaced
 * by our terrain-aware snow cover (SnowCover): bare rock on cliffs, deep smooth snow in hollows
 * and on gentle slopes. Afterwards the small boulders (Boulders) are placed. Other dimensions are untouched.
 */
@Mixin(SnowAndFreezeFeature.class)
public abstract class SnowAndFreezeFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void icegiant$smoothSnow(FeaturePlaceContext<NoneFeatureConfiguration> context,
                                     CallbackInfoReturnable<Boolean> cir) {
        WorldGenLevel level = context.level();
        if (!(level instanceof WorldGenRegion region)) return;
        if (level.getLevel().dimension() != ModDimensions.FROZEN_REALM) return;
        boolean done = SnowCover.apply(region, context.origin());
        Boulders.place(region, context.origin());              // after the snow, so their snow caps stay
        Boulders.riverLogs(region, context.origin());          // fallen spruce trunks along the rivers
        Boulders.forestLogs(region, context.origin());         // ... and now and then in the forest
        Boulders.riverStones(region, context.origin());        // a stone now and then in wider rivers
        Boulders.bankPlants(region, context.origin());         // ferns and grass right next to the water
        Boulders.pebbles(region, context.origin());            // little stones on open gravel
        Boulders.fixTallPlants(region, context.origin());      // no floating halves of tall ferns/grass
        net.icegiant.worldgen.SnowyDecor.apply(region, context.origin());   // snowy plants / leaves, icicles
        cir.setReturnValue(done);
    }
}
