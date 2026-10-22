package net.icegiant.worldgen;

import com.mojang.serialization.Codec;
import net.icegiant.IceGiantMod;
import net.icegiant.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Frozen Realm's sea is a cold ocean (Rubenbuben4, 2026-10-21: "wie ein kalter Ozean, tief, mit Fischen und
 * den Tieren, die da leben"). Its biomes stay vanilla's frozen oceans (snow, ice, polar bears, glow squids in the
 * deep), and only in this dimension they also get what vanilla's cold ocean has:
 * <ul>
 * <li>cod (weight 15, groups of 3 - 6) next to the salmon, and squids as often as in a cold ocean (weight 3);</li>
 * <li>kelp forests and seagrass - the vanilla features, placed by data/icegiant/worldgen/placed_feature/
 * frozen_realm_*.json with the filter {@link #ONLY_HERE}, so the Overworld's frozen oceans stay as they are.</li>
 * </ul>
 * Since 2026-10-22 fish live in the rivers and lakes too (Rubenbuben4: "let fish spawn in rivers and lakes"): salmon
 * (weight 15, 1 - 5) and some cod (weight 5, 2 - 4) in open water at least 2 blocks deep, at any height. Vanilla lets
 * fish spawn only between 13 below sea level and sea level, and only under plain water blocks, so its rule would
 * keep them out of almost every river (they lie higher, in river water) - {@link #onPlacement} lets them in there.
 * The current does not carry them away (FishCurrentMixin).
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class ColdSea {

    private static final MobSpawnSettings.SpawnerData COD = new MobSpawnSettings.SpawnerData(EntityType.COD, 15, 3, 6);
    private static final MobSpawnSettings.SpawnerData SQUID = new MobSpawnSettings.SpawnerData(EntityType.SQUID, 3, 1, 4);
    private static final MobSpawnSettings.SpawnerData RIVER_SALMON = new MobSpawnSettings.SpawnerData(EntityType.SALMON, 15, 1, 5);
    private static final MobSpawnSettings.SpawnerData RIVER_COD = new MobSpawnSettings.SpawnerData(EntityType.COD, 5, 2, 4);

    private ColdSea() {
    }

    private static boolean isSea(Holder<Biome> biome) {
        return biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN);
    }

    /**
     * Open river or lake water of the Frozen Realm's land at pos: water there and right above or below it (2 blocks
     * deep at least), and nothing solid over it (not in a cave: the ground of the column - ocean floor heightmap - lies
     * below it). Rivers and lakes lie in land biomes, the sea in the frozen oceans.
     */
    static boolean freshWater(LevelReader level, BlockPos pos) {
        if (isSea(level.getBiome(pos))) return false;
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return false;
        if (!level.getFluidState(pos.below()).is(FluidTags.WATER) && !level.getFluidState(pos.above()).is(FluidTags.WATER)) return false;
        return pos.getY() >= level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ());
    }

    /** Fish may spawn in the rivers and lakes (any height, also in river water - see the class comment). */
    @SubscribeEvent
    public static void onPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (event.getEntityType() != EntityType.SALMON && event.getEntityType() != EntityType.COD) return;
        if (event.getLevel().getLevel().dimension() != ModDimensions.FROZEN_REALM) return;
        if (freshWater(event.getLevel(), event.getPos())) event.setResult(Event.Result.ALLOW);
    }

    /** Natural spawning asks for the possible creatures at a spot: in the Frozen Realm's sea add the cold ocean's. */
    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        MobCategory category = event.getMobCategory();
        if (category != MobCategory.WATER_AMBIENT && category != MobCategory.WATER_CREATURE) return;
        if (!(event.getLevel() instanceof Level level) || level.dimension() != ModDimensions.FROZEN_REALM) return;
        Holder<Biome> biome = level.getBiome(event.getPos());
        if (!isSea(biome)) {                                                     // the land: fish in rivers and lakes
            if (category == MobCategory.WATER_AMBIENT && freshWater(level, event.getPos())) {
                event.addSpawnerData(RIVER_SALMON);
                event.addSpawnerData(RIVER_COD);
            }
            return;
        }
        if (category == MobCategory.WATER_AMBIENT) {
            event.addSpawnerData(COD);
        } else {
            // (the list we get is read-only: remove through the event; our entries are always the same objects,
            // so the spawner's later check "is this still allowed here" finds them again)
            for (MobSpawnSettings.SpawnerData d : java.util.List.copyOf(event.getSpawnerDataList())) {
                if (d.type == EntityType.SQUID) event.removeSpawnerData(d);
            }
            event.addSpawnerData(SQUID);
        }
    }

    /** Placement filter "icegiant:frozen_realm_only": lets a feature through in the Frozen Realm only. */
    public static final class OnlyHere extends PlacementFilter {
        public static final OnlyHere INSTANCE = new OnlyHere();
        public static final Codec<OnlyHere> CODEC = Codec.unit(() -> INSTANCE);

        private OnlyHere() {
        }

        @Override
        protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
            return context.getLevel().getLevel().dimension() == ModDimensions.FROZEN_REALM;
        }

        @Override
        public PlacementModifierType<?> type() {
            return net.icegiant.registry.ModFeatures.FROZEN_REALM_ONLY.get();
        }
    }

    /** (see ModFeatures) */
    public static final String ONLY_HERE = "frozen_realm_only";
}
