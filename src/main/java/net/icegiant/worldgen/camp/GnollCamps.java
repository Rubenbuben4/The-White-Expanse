package net.icegiant.worldgen.camp;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.icegiant.IceGiantMod;
import net.icegiant.entity.gnoll.GnollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gnoll camps in the world:
 * <ul>
 *   <li>Gnolls of a camp keep to it: they wander within {@value #STAY_LARGE} (large camp) or {@value #STAY_SMALL}
 *       blocks of its square, in a huge camp over all its districts (they still chase intruders further). Kept in
 *       their NBT, so it survives a reload.</li>
 *   <li>Their skins ({@link #variantFor}).</li>
 *   <li>Where camps are ({@link #campsAt}, {@link #insideCamp}): trees, boulders and fallen logs keep out of them.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GnollCamps {

    public static final String TAG = "icegiant_camp", LARGE = "icegiant_camp_large", STAY = "icegiant_camp_stay";
    static final int STAY_LARGE = 52, STAY_SMALL = 34;
    // ---- TUNE: skins (Rubenbuben4, 2026-10-17) ----
    static final float SNOW_CAMP_SNOW = 0.65F;       // snowy Overworld camps: this share snow Gnolls, the rest normal
    static final float TAIGA_CAMP_NORMAL = 0.55F;    // taiga camps: this share normal (brown) Gnolls, the rest warm (desert)

    private GnollCamps() {
    }

    static void markCamp(GnollEntity k, BlockPos center, int stay) {
        k.getPersistentData().putLong(TAG, center.asLong());
        k.getPersistentData().putInt(STAY, stay);
        k.restrictTo(center, stay);
    }

    /**
     * Skin of a camp Gnoll: in the Frozen Realm always snow; in the Overworld snowy camps snow and normal, taiga camps
     * normal (brown) and warm (desert) - mixed in every camp.
     */
    static int variantFor(String kind, boolean frozenRealm, RandomSource random) {
        if (frozenRealm) return GnollEntity.VARIANT_SNOW;
        if ("taiga".equals(kind)) return random.nextFloat() < TAIGA_CAMP_NORMAL ? GnollEntity.VARIANT_NORMAL : GnollEntity.VARIANT_WARM;
        return random.nextFloat() < SNOW_CAMP_SNOW ? GnollEntity.VARIANT_SNOW : GnollEntity.VARIANT_NORMAL;
    }

    /** A camp: middle of its square and how far it reaches. */
    public record Camp(int x, int z, int radius) {
        public boolean contains(int px, int pz, int margin) {
            double dx = px - this.x, dz = pz - this.z, r = this.radius + margin;
            return dx * dx + dz * dz <= r * r;
        }
    }

    /**
     * The camps reaching into this chunk - read from the chunk's structure references, like Minecraft finds structures
     * while it generates (the chunks with the camps' starts are within the world generation's reach).
     */
    public static List<Camp> campsAt(WorldGenLevel level, ChunkPos chunkPos) {
        List<Camp> out = new ArrayList<>();
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) return out;
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_REFERENCES, false);
        if (chunk == null) return out;
        for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
            if (!(e.getKey() instanceof GnollCampStructure)) continue;
            for (long ref : e.getValue()) {
                ChunkPos sp = new ChunkPos(ref);
                if (!level.hasChunk(sp.x, sp.z)) continue;
                ChunkAccess startChunk = level.getChunk(sp.x, sp.z, ChunkStatus.STRUCTURE_STARTS, false);
                if (startChunk == null) continue;
                StructureStart start = startChunk.getStartForStructure(e.getKey());
                if (start == null || !start.isValid()) continue;
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof GnollCampPathPiece p && p.isSquare()) out.add(new Camp(p.centerX(), p.centerZ(), p.campRadius()));
                }
            }
        }
        return out;
    }

    /** True if this spot lies in a Gnoll camp (during world generation). */
    public static boolean insideCamp(WorldGenLevel level, BlockPos pos, int margin) {
        for (Camp c : campsAt(level, new ChunkPos(pos))) if (c.contains(pos.getX(), pos.getZ(), margin)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof GnollEntity k)) return;
        if (!k.getPersistentData().contains(TAG)) return;
        int stay = k.getPersistentData().contains(STAY) ? k.getPersistentData().getInt(STAY)
                : k.getPersistentData().getBoolean(LARGE) ? STAY_LARGE : STAY_SMALL;     // (Gnolls from before 2026-10-17)
        k.restrictTo(BlockPos.of(k.getPersistentData().getLong(TAG)), stay);
    }
}
