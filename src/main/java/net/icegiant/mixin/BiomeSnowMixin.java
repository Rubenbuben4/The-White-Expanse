package net.icegiant.mixin;

import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frozen Realm: falling snow (normal snowfall and the snowstorm, which both ask Biome#shouldSnow)
 * never settles on the rock the mountains are painted with (polished basalt, basalt, grey concrete,
 * stone, ...) or on the gravel beaches, so the slope painting stays visible. The blocks are listed in the block tag
 * icegiant:no_snow_cover. Water there never freezes over by itself (rivers, lakes, the sea between the ice floes).
 * Other dimensions are untouched.
 */
@Mixin(Biome.class)
public abstract class BiomeSnowMixin {

    @Unique
    private static final TagKey<Block> ICEGIANT$NO_SNOW_COVER =
            TagKey.create(Registries.BLOCK, new ResourceLocation(IceGiantMod.MOD_ID, "no_snow_cover"));

    @Inject(method = "shouldSnow", at = @At("HEAD"), cancellable = true)
    private void icegiant$keepRockBare(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof Level real) || real.dimension() != ModDimensions.FROZEN_REALM) return;
        // pos is where the new snow (or the next layer of a thin layer already lying there) would go
        if (level.getBlockState(pos.below()).is(ICEGIANT$NO_SNOW_COVER)) cir.setReturnValue(false);
    }

    /**
     * Frozen Realm water never freezes over by itself: rivers and lakes stay open, and the sea keeps its
     * generated ice floes (IceFloes) with open water between them instead of slowly closing up.
     */
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void icegiant$openRivers(LevelReader level, BlockPos pos, boolean mustBeAtEdge,
                                     CallbackInfoReturnable<Boolean> cir) {
        Level real = level instanceof Level l ? l
                : level instanceof net.minecraft.server.level.WorldGenRegion r ? r.getLevel() : null;
        if (real != null && real.dimension() == ModDimensions.FROZEN_REALM) cir.setReturnValue(false);
    }
}
