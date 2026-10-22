package net.icegiant.mixin;

import net.icegiant.dimension.ModDimensions;
import net.icegiant.worldgen.NorwegianPines;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the Frozen Realm every vanilla tree (any biome, any tree type) is replaced by a
 * Norwegian pine at the same spot. During world generation the forest also follows the height
 * (thick in the lowlands, thinning out up to the tree line - NorwegianPines.placeInForest);
 * saplings grown by players always become a normal pine. Other dimensions are untouched - except that no tree grows
 * inside a Gnoll camp while the world is generated (GnollCamps.insideCamp).
 */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void icegiant$norwegianPines(FeaturePlaceContext<TreeConfiguration> context,
                                         CallbackInfoReturnable<Boolean> cir) {
        WorldGenLevel level = context.level();
        // no trees in a Gnoll camp (any dimension; only while the world is generated - players may plant some)
        if (level instanceof net.minecraft.server.level.WorldGenRegion
                && net.icegiant.worldgen.camp.GnollCamps.insideCamp(level, context.origin(), 2)) {
            cir.setReturnValue(false);
            return;
        }
        if (level.getLevel().dimension() != ModDimensions.FROZEN_REALM) return;
        boolean bigTree = context.config().trunkPlacer instanceof GiantTrunkPlacer; // 2x2 mega spruce
        if (level instanceof net.minecraft.server.level.WorldGenRegion) {
            cir.setReturnValue(NorwegianPines.placeInForest(level, context.origin(), context.random(), bigTree));
        } else {
            cir.setReturnValue(NorwegianPines.place(level, context.origin(), context.random(), bigTree)); // sapling
        }
    }
}
