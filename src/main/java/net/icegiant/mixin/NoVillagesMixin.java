package net.icegiant.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No villages in the Frozen Realm (its biomes are vanilla snowy biomes, which would get snowy villages). Gnoll camps
 * are allowed there since 2026-10-17 (snowy camps, snow Gnolls only - see GnollCamps.variantFor).
 * Recognised by the dimension's own noise settings ({@code icegiant:frozen_realm}); the Overworld and every
 * other dimension keep their villages.
 * <p>
 * Since 2026-10-21 also no ocean monuments there: vanilla always builds them at Y 39, but the Frozen Realm's deep
 * sea floor lies at Y 0 - 15, so they would float in the water.
 * <p>
 * Since 2026-10-22 also no ocean ruins (cold and warm) there - they don't fit the Frozen Realm (Rubenbuben4).
 * Shipwrecks stay.
 */
@Mixin(ChunkGenerator.class)
public abstract class NoVillagesMixin {

    private static final ResourceLocation FROZEN_REALM_SETTINGS = new ResourceLocation("icegiant", "frozen_realm");

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void icegiant$noVillages(StructureSet.StructureSelectionEntry entry, StructureManager structures,
                                     RegistryAccess registries, RandomState random, StructureTemplateManager templates,
                                     long seed, ChunkAccess chunk, ChunkPos chunkPos, SectionPos sectionPos,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof NoiseBasedChunkGenerator noise)) return;
        if (!noise.generatorSettings().is(FROZEN_REALM_SETTINGS)) return;
        if (entry.structure().is(StructureTags.VILLAGE)
                || entry.structure().is(BuiltinStructures.OCEAN_MONUMENT)
                || entry.structure().is(BuiltinStructures.OCEAN_RUIN_COLD)
                || entry.structure().is(BuiltinStructures.OCEAN_RUIN_WARM)) {
            cir.setReturnValue(false);
        }
    }
}
