package net.icegiant.mixin;

import net.icegiant.worldgen.CaveEntrances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/** Caves and ravines of the Frozen Realm don't break through the ground outside the mountains (CaveEntrances). */
@Mixin(WorldCarver.class)
public abstract class CaveEntranceMixin {

    @Inject(method = "carveBlock", at = @At("HEAD"), cancellable = true)
    private void icegiant$noLowlandEntrances(CarvingContext context, CarverConfiguration config, ChunkAccess chunk,
                                            Function<BlockPos, Holder<Biome>> biomes, CarvingMask mask,
                                            BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos checkPos,
                                            Aquifer aquifer, MutableBoolean reachedSurface,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (CaveEntrances.keepSolid(context, pos)) cir.setReturnValue(false);
    }
}
