package net.icegiant.mixin;

import net.icegiant.worldgen.CaveEntrances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Ravines under the Frozen Realm's deep sea start as far down as its floor lies (CaveEntrances.ravineY), so they cut
 * into the sea floor as often as in a vanilla ocean. The random numbers are drawn exactly as before (the vanilla
 * height is still sampled), only the result is moved. Other dimensions: unchanged (not our terrain).
 */
@Mixin(CanyonWorldCarver.class)
public abstract class SeaRavineMixin {

    @Unique
    private static final ThreadLocal<ChunkPos> icegiant$chunk = new ThreadLocal<>();

    @Inject(method = "carve(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/levelgen/carver/CanyonCarverConfiguration;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/Aquifer;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/CarvingMask;)Z",
            at = @At("HEAD"))
    private void icegiant$rememberChunk(CarvingContext context, CanyonCarverConfiguration config, ChunkAccess chunk,
                                        Function<BlockPos, Holder<Biome>> biomes, RandomSource random, Aquifer aquifer,
                                        ChunkPos chunkPos, CarvingMask mask, CallbackInfoReturnable<Boolean> cir) {
        icegiant$chunk.set(chunkPos);
    }

    @Redirect(method = "carve(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/levelgen/carver/CanyonCarverConfiguration;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/Aquifer;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/CarvingMask;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int icegiant$seaRavineY(HeightProvider provider, RandomSource random, WorldGenerationContext context) {
        int y = provider.sample(random, context);
        ChunkPos chunkPos = icegiant$chunk.get();
        if (chunkPos == null || !(context instanceof CarvingContext carving)) return y;
        return CaveEntrances.ravineY(carving.randomState(), chunkPos.x, chunkPos.z, y);
    }
}
