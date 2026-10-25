package net.icegiant.crystal;

import net.icegiant.IceGiantMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Changes the biome around the Schöpfungskristalle (Rubenbuben4, 2026-10-25): a cylinder around the crystal - radius
 * 64 (small crystal) or 192 (large), 120 blocks up and 120 down - gets the biome the engraved core carries. When the
 * crystal is switched off or broken, the natural biome comes back.
 * <p>
 * Only the biome changes (colours, weather, fog, music, which mobs spawn), not the blocks. Works like the vanilla
 * /fillbiome command, cell by cell (4 x 4 x 4 blocks): chunks that are loaded are changed a few per tick; chunks that
 * load later (also new ones) are brought in line as soon as they load. Players get the new biomes sent.
 */
@Mod.EventBusSubscriber(modid = IceGiantMod.MOD_ID)
public final class CreationCrystals {

    public static final int SMALL_RADIUS = 64, LARGE_RADIUS = 3 * SMALL_RADIUS, HALF_HEIGHT = 120;
    static final int CHUNKS_PER_TICK = 16;

    /** Chunks still to be brought in line, per dimension (filled from the server thread and chunk loading). */
    private static final Map<ResourceKey<net.minecraft.world.level.Level>, Set<Long>> QUEUE = new HashMap<>();

    private CreationCrystals() {
    }

    public static CreationCrystalData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CreationCrystalData::load, CreationCrystalData::new, CreationCrystalData.NAME);
    }

    /** Switches the crystal at {@code center} on with this biome. */
    public static void activate(ServerLevel level, BlockPos center, int radius, ResourceLocation biome) {
        CreationCrystalData.Zone zone = new CreationCrystalData.Zone(center, radius, HALF_HEIGHT, biome);
        data(level).activate(zone);
        queueLoaded(level, zone);
    }

    /** Switches the crystal at {@code center} off (natural biome comes back). */
    public static void deactivate(ServerLevel level, BlockPos center) {
        CreationCrystalData.Zone old = data(level).deactivate(center);
        if (old != null) queueLoaded(level, old);
    }

    @Nullable
    public static ResourceLocation activeBiome(ServerLevel level, BlockPos center) {
        CreationCrystalData.Zone z = data(level).activeAt(center);
        return z == null ? null : z.biome();
    }

    private static void queueLoaded(ServerLevel level, CreationCrystalData.Zone zone) {
        ServerChunkCache cache = level.getChunkSource();
        int r = zone.radius();
        int cx0 = (zone.center().getX() - r) >> 4, cx1 = (zone.center().getX() + r) >> 4;
        int cz0 = (zone.center().getZ() - r) >> 4, cz1 = (zone.center().getZ() + r) >> 4;
        Set<Long> q = queue(level);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (zone.touchesChunk(cx, cz) && cache.getChunkNow(cx, cz) != null) q.add(ChunkPos.asLong(cx, cz));
            }
        }
    }

    private static Set<Long> queue(ServerLevel level) {
        synchronized (QUEUE) {
            return QUEUE.computeIfAbsent(level.dimension(), k -> java.util.Collections.synchronizedSet(new LinkedHashSet<>()));
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        ChunkPos p = chunk.getPos();
        // (reading the saved data here is cheap; the change itself waits for the next tick)
        level.getServer().execute(() -> {
            if (data(level).touchesChunk(p.x, p.z)) queue(level).add(p.toLong());
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Set<Long> q;
            synchronized (QUEUE) {
                q = QUEUE.get(level.dimension());
            }
            if (q == null || q.isEmpty()) continue;
            List<Long> batch = new ArrayList<>(CHUNKS_PER_TICK);
            synchronized (q) {
                var it = q.iterator();
                while (it.hasNext() && batch.size() < CHUNKS_PER_TICK) {
                    batch.add(it.next());
                    it.remove();
                }
            }
            List<ChunkAccess> changed = new ArrayList<>();
            CreationCrystalData data = data(level);
            for (long key : batch) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null && apply(level, data, chunk)) changed.add(chunk);
            }
            if (!changed.isEmpty()) level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
        }
    }

    /** Brings one chunk in line with the crystals; true if a biome changed. */
    private static boolean apply(ServerLevel level, CreationCrystalData data, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        List<CreationCrystalData.Zone> active = new ArrayList<>(), reverts = new ArrayList<>();
        for (CreationCrystalData.Zone z : data.active) if (z.touchesChunk(cp.x, cp.z)) active.add(z);
        for (CreationCrystalData.Zone z : data.reverts) if (z.touchesChunk(cp.x, cp.z)) reverts.add(z);
        if (active.isEmpty() && reverts.isEmpty()) return false;

        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<ResourceLocation, Holder<Biome>> holders = new HashMap<>();
        for (CreationCrystalData.Zone z : active) {
            Optional<Holder.Reference<Biome>> h = biomes.getHolder(ResourceKey.create(Registries.BIOME, z.biome()));
            h.ifPresent(ref -> holders.put(z.biome(), ref));
        }
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        boolean[] any = {false};
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            Holder<Biome> now = chunk.getNoiseBiome(qx, qy, qz);
            int x = (qx << 2) + 2, y = (qy << 2) + 2, z = (qz << 2) + 2;   // middle of the 4 x 4 x 4 cell
            Holder<Biome> want = null;
            for (CreationCrystalData.Zone zone : active) {                  // the newest crystal wins where they overlap
                if (zone.contains(x, y, z) && holders.containsKey(zone.biome())) want = holders.get(zone.biome());
            }
            if (want == null) {
                for (CreationCrystalData.Zone zone : reverts) {
                    if (zone.contains(x, y, z)) {
                        want = source.getNoiseBiome(qx, qy, qz, sampler);   // the natural biome
                        break;
                    }
                }
            }
            if (want == null || want.equals(now)) return now;
            any[0] = true;
            return want;
        }, level.getChunkSource().randomState().sampler());
        if (any[0]) chunk.setUnsaved(true);
        return any[0];
    }
}
