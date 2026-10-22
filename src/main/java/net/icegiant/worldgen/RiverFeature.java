package net.icegiant.worldgen;

import com.mojang.serialization.Codec;
import net.icegiant.block.RiverWaterBlock;
import net.icegiant.dimension.ModDimensions;
import net.icegiant.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Digs the Frozen Realm's rivers and lakes (worked out by RiverNetwork) into one chunk. Runs in the "lakes"
 * step, before trees, boulders and the snow cover. Per block column:
 * <ul>
 *   <li><b>channel</b>: a flat oval bed (deepest in the middle), water up to 1 block below the land,
 *       gravel/stone bed;</li>
 *   <li><b>rim</b> (1-3 blocks, grows with the river): one block below the land next to it, gravel/stone
 *       with packed ice at the water line; the water another block lower (2 below its own ground). Every
 *       column's water is checked against its neighbours and lowered where one is too low - nothing is ever
 *       raised above the land;</li>
 *   <li><b>lake</b>: water up to the lake level, gravel bed; <b>shore</b>: a gravel/stone ring, filled up
 *       where the ground is lower than the water so nothing runs out.</li>
 * </ul>
 * River water is RiverWaterBlock with the river's direction (it visibly flows and carries boats); lakes are
 * still, ordinary water. Frozen Realm look: a band of packed ice at the water line, gravel and cobble beds,
 * stone at steep mountain streams, thin ice on calm shallow edges.
 * Columns that are sea already are left alone (river mouths just meet the sea).
 */
public class RiverFeature extends Feature<NoneFeatureConfiguration> {

    private static final ConcurrentHashMap<FrozenTerrainMath, RiverNetwork> NETWORKS = new ConcurrentHashMap<>();

    private record Last(Object router, RiverNetwork network) {
    }

    private static volatile Last last;

    public RiverFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** The river network of a Frozen Realm level (null if its terrain is not ours). */
    public static RiverNetwork network(ServerLevel level) {
        Object router = level.getChunkSource().randomState().router().continents();
        Last l = last;
        if (l != null && l.router() == router) return l.network();          // fast path: same world as last time
        FrozenTerrainMath math = NorwayTerrain.find(level.getChunkSource().randomState().router().continents());
        if (math == null) return null;
        if (NETWORKS.size() > 4 && !NETWORKS.containsKey(math)) NETWORKS.clear();
        RiverNetwork net = NETWORKS.computeIfAbsent(math, m -> new RiverNetwork(m, level.getSeed()));
        last = new Last(router, net);
        return net;
    }

    /** The river network of a terrain (for structures: they only know the terrain maths and the world seed). */
    public static RiverNetwork networkFor(FrozenTerrainMath math, long seed) {
        if (NETWORKS.size() > 4 && !NETWORKS.containsKey(math)) NETWORKS.clear();
        return NETWORKS.computeIfAbsent(math, m -> new RiverNetwork(m, seed));
    }

    /** River/lake data for the columns of one chunk (null if nothing there). */
    public static RiverNetwork.ChunkData chunkData(ServerLevel level, ChunkPos chunk) {
        RiverNetwork net = network(level);
        if (net == null) return null;
        RiverNetwork.ChunkData d = net.chunk(chunk.x, chunk.z);
        return d.any ? d : null;
    }

    /** True if the column is water or its gravel/stone rim (no snow there). */
    public static boolean isWaterOrRim(RiverNetwork.ChunkData d, int i) {
        if (d == null) return false;
        byte k = d.kind[i];
        return k == RiverNetwork.ChunkData.CHANNEL || k == RiverNetwork.ChunkData.RIM
                || k == RiverNetwork.ChunkData.LAKE || k == RiverNetwork.ChunkData.SHORE;
    }

    // ---- TUNE: fitting the river to the real ground of every column ----
    static final int MAX_BELOW = 3;                   // river running into a lake: water this far below at most
    static final int RIM_DROP = 1;                    // the gravel rim lies this much below the land next to it
    static final int WATER_DROP = 2;                  // the water lies this much below its column's ground

    // ---- TUNE: holes and tunnels ----
    static final int MAX_FILL = 40;                   // a hole next to the water is filled at most this deep
    static final int TUNNEL_DEPTH = 14;               // water this far below the untouched land: a tunnel through the
                                                      // ridge (only real ridges; shallower cuts are open valleys)
    static final int TUNNEL_HEAD = 3;                 // headroom over the water at the tunnel's sides ...
    static final int TUNNEL_ARCH = 2;                 // ... and this much more in its middle (a rounded vault)

    /** Planned water of a river or lake column in another chunk (MIN if there is none). */
    private static int outsideWater(RiverNetwork net, int x, int z) {
        if (net == null) return Integer.MIN_VALUE;
        RiverNetwork.ChunkData nd = net.chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int n = Math.floorMod(x, 16) * 16 + Math.floorMod(z, 16);
        byte k = nd.kind[n];
        return k == RiverNetwork.ChunkData.CHANNEL || k == RiverNetwork.ChunkData.LAKE ? nd.water[n] : Integer.MIN_VALUE;
    }

    /**
     * True if this river column (or the rim / land beside it) belongs to a tunnel: decided once along the river's
     * middle line in the plan (RiverNetwork.piece: how deep the water lies below the land there), so the water,
     * its rims and the land above all agree - and every chunk too.
     */
    static boolean tunnel(RiverNetwork.ChunkData d, int i) {
        byte k = d.kind[i];
        return (k == RiverNetwork.ChunkData.CHANNEL || k == RiverNetwork.ChunkData.RIM || k == RiverNetwork.ChunkData.NEAR)
                && d.gorge[i] >= TUNNEL_DEPTH;
    }

    /** Fills a column from above its ground up to (and with) top: stone inside, rim material on top. */
    private static void fill(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, boolean rocky) {
        for (int y = Math.max(ground + 1, top - MAX_FILL); y <= top; y++) {
            BlockState st = level.getBlockState(pos.set(x, y, z));
            if (!st.canBeReplaced() && st.getFluidState().isEmpty() && !isTree(st)) continue;
            set(level, pos, x, y, z, y == top ? rimBlock(x, y, z, rocky) : Blocks.STONE.defaultBlockState());
        }
    }

    /** Gravel must not hang over a cave: whatever is open right below this block becomes stone. */
    private static void support(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        BlockState below = level.getBlockState(pos.set(x, y - 1, z));
        if (below.isAir() || !below.getFluidState().isEmpty()) set(level, pos, x, y - 1, z, Blocks.STONE.defaultBlockState());
    }

    static final int MAX_PLUG = 10;                   // openings below a column are closed at most this deep

    /**
     * Openings (air, water, lava: a cave) in this column from y down to bottom become stone, at most
     * {@value #MAX_PLUG} blocks. Where the bed next to a column lies deeper than the column's own bottom, a cave
     * there would let that water out sideways (deeper round beds and lake bowls since 2026-10-22).
     */
    private static void plug(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int bottom, int z) {
        if (bottom == Integer.MIN_VALUE || bottom == Integer.MAX_VALUE) return;  // (no bed next to it)
        for (int n = 0; y >= bottom && n < MAX_PLUG; y--, n++) {
            BlockState st = level.getBlockState(pos.set(x, y, z));
            if (st.isAir() || !st.getFluidState().isEmpty()) set(level, pos, x, y, z, Blocks.STONE.defaultBlockState());
        }
    }

    /** One above the deepest bed next to a column (how deep it is closed), MAX if there is none. */
    private static int above(int low) {
        return low == Integer.MAX_VALUE ? Integer.MAX_VALUE : low + 1;
    }

    /** Planned bed of a river or lake column (round bed / bowl; a deeper hollow is not known here), MAX if none. */
    private static int plannedBed(RiverNetwork.ChunkData d, int n) {
        byte k = d.kind[n];
        if (k == RiverNetwork.ChunkData.CHANNEL) return d.water[n] - Math.max(1, d.depth[n]);
        if (k == RiverNetwork.ChunkData.LAKE) return d.water[n] - Math.max(1, Math.max(d.lakeDepth[n], d.depth[n]));
        return Integer.MAX_VALUE;
    }

    /** The deepest planned bed of the four columns next to this one (this chunk: with their real ground). */
    private static int lowestBedNext(RiverNetwork net, RiverNetwork.ChunkData d, int[] ground, int x0, int z0, int i) {
        int lx = i / 16, lz = i % 16, low = Integer.MAX_VALUE;
        for (int dir = 0; dir < 4; dir++) {
            int nx = lx + (dir == 0 ? 1 : dir == 1 ? -1 : 0), nz = lz + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
            if (nx >= 0 && nz >= 0 && nx < 16 && nz < 16) {
                int n = nx * 16 + nz, b = plannedBed(d, n);
                if (d.kind[n] == RiverNetwork.ChunkData.LAKE && ground[n] < b) b = ground[n];    // a deeper hollow
                low = Math.min(low, b);
            } else if (net != null) {
                int x = x0 + nx, z = z0 + nz;
                RiverNetwork.ChunkData nd = net.chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                low = Math.min(low, plannedBed(nd, Math.floorMod(x, 16) * 16 + Math.floorMod(z, 16)));
            }
        }
        return low;
    }

    /** Planned kind and water of any column (this chunk or the next one); null data: none. */
    private static RiverNetwork.ChunkData planAt(RiverNetwork net, RiverNetwork.ChunkData d, int x0, int z0, int x, int z) {
        if (Math.floorDiv(x, 16) == Math.floorDiv(x0, 16) && Math.floorDiv(z, 16) == Math.floorDiv(z0, 16)) return d;
        return net == null ? null : net.chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    /**
     * How many blocks a lake shore column lies above the lake (RiverMouths.shoreStep): from the nearest column of
     * that lake's water within {@value RiverMouths#SHORE_SEARCH} blocks, from the plan (the same in every chunk).
     */
    private static int shoreStep(RiverNetwork net, RiverNetwork.ChunkData d, int x0, int z0, int i) {
        int x = x0 + i / 16, z = z0 + i % 16, wl = d.water[i], r = RiverMouths.SHORE_SEARCH, best = Integer.MAX_VALUE;
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                int d2 = a * a + b * b;
                if (d2 == 0 || d2 >= best) continue;
                RiverNetwork.ChunkData nd = planAt(net, d, x0, z0, x + a, z + b);
                if (nd == null) continue;
                int n = Math.floorMod(x + a, 16) * 16 + Math.floorMod(z + b, 16);
                if (nd.kind[n] == RiverNetwork.ChunkData.LAKE && nd.water[n] == wl) best = d2;
            }
        }
        return RiverMouths.shoreStep(best == Integer.MAX_VALUE ? -1.0 : Math.sqrt(best));
    }

    /** Highest river or lake water right next to this column (4 sides, from the plan), MIN if none. */
    private static int waterNext(RiverNetwork net, RiverNetwork.ChunkData d, int x0, int z0, int i) {
        int x = x0 + i / 16, z = z0 + i % 16, top = Integer.MIN_VALUE;
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0), nz = z + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
            RiverNetwork.ChunkData nd = planAt(net, d, x0, z0, nx, nz);
            if (nd == null) continue;
            int n = Math.floorMod(nx, 16) * 16 + Math.floorMod(nz, 16);
            byte k = nd.kind[n];
            if (k == RiverNetwork.ChunkData.CHANNEL || k == RiverNetwork.ChunkData.LAKE) top = Math.max(top, nd.water[n]);
        }
        return top;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (level.getLevel().dimension() != ModDimensions.FROZEN_REALM) return false;
        ChunkPos chunk = new ChunkPos(context.origin());
        RiverNetwork.ChunkData d = chunkData(level.getLevel(), chunk);
        if (d == null) return false;
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 0) trees standing in or over the river (grown in from the next chunks) are taken out whole
        clearTrees(level, pos, chunk, d);
        // 1) the real ground of every column (before anything is changed) - trees and snow on them don't count
        int[] ground = new int[256];
        boolean[] wet = new boolean[256];                                         // sea or water already there
        for (int i = 0; i < 256; i++) {
            int x = x0 + i / 16, z = z0 + i % 16;
            ground[i] = groundY(level, pos, x, z);
            wet[i] = !level.getFluidState(pos.set(x, ground[i], z)).isEmpty()
                    || !level.getFluidState(pos.set(x, ground[i] + 1, z)).isEmpty();
        }
        RiverNetwork net = network(level.getLevel());
        // 2) the water surface: the river plan's level (RiverNetwork.junction / piece). It comes from the untouched
        //    terrain only and never rises downstream - so it is the same for every chunk (no jumps at chunk
        //    borders) and the river only ever stays level or goes down.
        int[] water = new int[256];
        java.util.Arrays.fill(water, Integer.MIN_VALUE);
        boolean[] inTunnel = new boolean[256];
        for (int i = 0; i < 256; i++) {
            if (!wet[i] && d.kind[i] == RiverNetwork.ChunkData.CHANNEL) {
                water[i] = d.water[i];
                inTunnel[i] = tunnel(d, i);
            }
        }
        // 3) the banks must hold it: every dry column next to river or lake water is solid at least up to that water.
        //    Normally it already is (the water lies WATER_DROP below the land); where a cave, a ravine or a dip
        //    opens next to the river, the bank is built up instead of letting the water down. (Lake water counts
        //    since 2026-10-17: a river's rim next to a lake was cut below the lake and let it run out.)
        int[] need = new int[256];
        java.util.Arrays.fill(need, Integer.MIN_VALUE);
        for (int i = 0; i < 256; i++) {
            if (water[i] != Integer.MIN_VALUE || wet[i]) continue;
            byte k = d.kind[i];
            if (k == RiverNetwork.ChunkData.LAKE || k == RiverNetwork.ChunkData.SHORE) continue;
            int lx = i / 16, lz = i % 16;
            for (int dir = 0; dir < 4; dir++) {
                int nx = lx + (dir == 0 ? 1 : dir == 1 ? -1 : 0), nz = lz + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                int w;
                if (nx >= 0 && nz >= 0 && nx < 16 && nz < 16) {
                    int n = nx * 16 + nz;
                    w = water[n];
                    if (d.kind[n] == RiverNetwork.ChunkData.LAKE && !wet[n]) w = Math.max(w, d.water[n]);
                } else {
                    w = outsideWater(net, x0 + nx, z0 + nz);                        // the next chunk's river
                    if (w != Integer.MIN_VALUE) {
                        int x = x0 + nx, z = z0 + nz, g = groundY(level, pos, x, z);
                        if (!level.getFluidState(pos.set(x, g, z)).isEmpty() && g > w) w = Integer.MIN_VALUE;   // sea there
                    }
                }
                if (w > need[i]) need[i] = w;
            }
        }
        int[] rimTop = new int[256];
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] == RiverNetwork.ChunkData.RIM) {
                // a step below the land; a river cut deep into the land: a ledge 1 block over the water (not a wall
                // up to the land - the land behind it slopes down to it, slopeBank); never below the water next to
                // it nor its own (since 2026-10-18: no gravel trench where the river lies level with the land)
                rimTop[i] = RiverBanks.rimTop(ground[i], d.water[i], RIM_DROP, !tunnel(d, i),
                        need[i] == Integer.MIN_VALUE ? RiverBanks.NO_NEED : need[i]);
                continue;
            }
            rimTop[i] = ground[i] - RIM_DROP;                                        // (not used: no rim)
            if (need[i] == Integer.MIN_VALUE) continue;
            if (rimTop[i] < need[i]) rimTop[i] = need[i];
            if (ground[i] < need[i]) {                                               // plain land too low: fill it
                fill(level, pos, x0 + i / 16, z0 + i % 16, ground[i], need[i], d.rocky[i]);
                ground[i] = need[i];
            }
        }
        // 4) build
        boolean[] inflow = net != null ? RiverMouths.inflowClear(net, d, x0, z0) : null;
        int[] mouth = net != null ? RiverMouths.mouthDepth(net, d, x0, z0) : d.depth;
        int[] placed = new int[256];
        java.util.Arrays.fill(placed, Integer.MIN_VALUE);
        int[] low = new int[256];                                                // deepest bed next to each column
        for (int i = 0; i < 256; i++) {
            byte k = d.kind[i];
            if (k != RiverNetwork.ChunkData.NONE && k != RiverNetwork.ChunkData.NEAR) low[i] = lowestBedNext(net, d, ground, x0, z0, i);
        }
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] == RiverNetwork.ChunkData.NONE) continue;
            int x = x0 + i / 16, z = z0 + i % 16;
            if (wet[i]) {                                                        // water there already (a pond
                if (d.kind[i] == RiverNetwork.ChunkData.LAKE) {                  // below sea level): a lake over
                    for (int y = ground[i] + 1; y <= d.water[i]; y++) {          // it fills it up to its level
                        if (level.getBlockState(pos.set(x, y, z)).isAir()) set(level, pos, x, y, z, Blocks.WATER.defaultBlockState());
                    }
                }
                continue;
            }
            if (water[i] != Integer.MIN_VALUE) {
                placed[i] = channel(level, pos, x, z, d, i, ground[i], water[i], inTunnel[i], inflow != null && inflow[i], low[i]);
            } else if (d.kind[i] == RiverNetwork.ChunkData.LAKE) {
                placed[i] = lake(level, pos, x, z, d, i, ground[i], inflow != null && inflow[i], mouth[i], low[i]);
            } else if (d.kind[i] == RiverNetwork.ChunkData.SHORE) {
                shore(level, pos, x, z, d, i, ground[i], shoreStep(net, d, x0, z0, i), waterNext(net, d, x0, z0, i), low[i]);
            } else {
                bank(level, pos, x, z, d, i, ground[i], rimTop[i],
                        d.kind[i] == RiverNetwork.ChunkData.RIM && tunnel(d, i), low[i]);
            }
        }
        // 4b) the land next to the river slopes down to it (no walls where the river cut through a rise)
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] != RiverNetwork.ChunkData.NEAR || wet[i]) continue;
            if (tunnel(d, i)) continue;           // over a tunnel: the hill stays
            slopeBank(level, pos, net, x0 + i / 16, z0 + i % 16, d, i, ground[i], placed, water);
        }
        halfSteps(level, pos, chunk, d, net);
        // waterfalls and rapids: where the next river or lake column is lower, the water is woken up once the chunk
        // is loaded - Minecraft then lets it run over the edge by itself (the banks keep it in the bed). The next
        // chunk's water comes from the plan (until 2026-10-17 steps over a chunk border and from a lake into its
        // way out were never woken: the water stood there as a still wall)
        for (int i = 0; i < 256; i++) {
            if (placed[i] == Integer.MIN_VALUE) continue;
            int lx = i / 16, lz = i % 16;
            for (int dir = 0; dir < 4; dir++) {
                int nx = lx + (dir == 0 ? 1 : dir == 1 ? -1 : 0), nz = lz + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                int n = nx < 0 || nz < 0 || nx > 15 || nz > 15 ? outsideWater(net, x0 + nx, z0 + nz) : placed[nx * 16 + nz];
                if (n != Integer.MIN_VALUE && n < placed[i]) {
                    level.scheduleTick(pos.set(x0 + lx, placed[i], z0 + lz), Fluids.WATER, 2);
                    break;
                }
            }
        }
        return true;
    }

    static final int ISLET_DIG = 2;                    // lake columns whose ground is at most this above the water: dug out

    // ---- TUNE: river banks ----
    static final double BANK_SLOPE = 1.0;             // land beside a lowland river rises at most 1 block per block
    static final double ROCKY_BANK_SLOPE = 1.6;       // ... beside a mountain stream (steeper, still not a wall)

    /**
     * The land beside the river: where it stands higher than a bank sloping up from the water would (the river
     * cut through a rise, or the ground is rough), it is cut down to that slope - the top block (grass, dirt,
     * stone) moves down with it. Only ever lowered; the slope starts above the water, so nothing runs out.
     * Columns with a tree trunk on them are left alone.
     */
    private static void slopeBank(WorldGenLevel level, BlockPos.MutableBlockPos pos, RiverNetwork net, int x, int z,
                                  RiverNetwork.ChunkData d, int i, int top, int[] placed, int[] water) {
        double slope = d.rocky[i] ? ROCKY_BANK_SLOPE : BANK_SLOPE;
        int target = d.water[i] + 1 + (int) Math.ceil(d.edge[i] * slope);
        int lx = i / 16, lz = i % 16;
        for (int ax = -2; ax <= 2; ax++) {                                       // stay above the real water nearby
            for (int az = -2; az <= 2; az++) {
                int nx = lx + ax, nz = lz + az;
                RiverNetwork.ChunkData nd = d;
                int n;
                if (nx < 0 || nz < 0 || nx > 15 || nz > 15) {                   // next chunk: its planned water
                    if (net == null) continue;
                    nd = net.chunk(Math.floorDiv(x + ax, 16), Math.floorDiv(z + az, 16));
                    n = Math.floorMod(x + ax, 16) * 16 + Math.floorMod(z + az, 16);
                    byte k = nd.kind[n];
                    if (k == RiverNetwork.ChunkData.CHANNEL || k == RiverNetwork.ChunkData.LAKE || k == RiverNetwork.ChunkData.SHORE) {
                        target = Math.max(target, nd.water[n] + 2);
                    }
                    continue;
                }
                n = nx * 16 + nz;
                int w = Math.max(placed[n], water[n]);
                if (w != Integer.MIN_VALUE) target = Math.max(target, w + 2);
                byte k = d.kind[n];
                if (k == RiverNetwork.ChunkData.LAKE || k == RiverNetwork.ChunkData.SHORE) target = Math.max(target, d.water[n] + 2);
            }
        }
        if (top <= target) return;
        if (level.getBlockState(pos.set(x, top + 1, z)).is(BlockTags.LOGS)) return;   // a tree stands here
        BlockState surface = level.getBlockState(pos.set(x, top, z));
        clearPlants(level, pos, x, top, z);
        for (int y = top; y > target; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        if (surface.is(BlockTags.DIRT) || surface.is(Blocks.GRAVEL) || surface.is(Blocks.SNOW_BLOCK)) {
            set(level, pos, x, target, z, surface);                              // the grass etc. moves down
        }
    }

    /** Plants standing on the ground at this column (grass, ferns - both halves of tall ones) are removed. */
    private static void clearPlants(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z) {
        for (int y = top + 1; y <= top + 3; y++) {
            BlockState st = level.getBlockState(pos.set(x, y, z));
            if (st.isAir() || !st.getFluidState().isEmpty()) return;
            if (!(st.canBeReplaced() || st.getBlock() instanceof net.minecraft.world.level.block.BushBlock)) return;
            set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * A river column: round bed, water up to wl (already fitted to this column's ground). Openings below the bed are
     * closed down to the deepest bed next to it (low).
     */
    private static int channel(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z,
                               RiverNetwork.ChunkData d, int i, int top, int wl, boolean tunnel, boolean nearLake, int low) {
        boolean rocky = d.rocky[i];
        BlockState river = ModBlocks.RIVER_WATER.get().defaultBlockState()
                .setValue(RiverWaterBlock.FLOW, RiverWaterBlock.flowFor(d.angle[i]));
        int depth = Math.max(1, d.depth[i]);
        int bed = wl - depth;
        if (tunnel) {                                                            // through the hill: a vault over the
            int head = TUNNEL_HEAD + Math.min(TUNNEL_ARCH, depth);          // water, the rock above stays
            for (int y = Math.min(top, wl + head); y > wl; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        } else {
            clearPlants(level, pos, x, top, z);                                  // nothing may float over the water
            for (int y = top; y > wl; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        }
        for (int y = top + 1; y < bed; y++) set(level, pos, x, y, z, Blocks.STONE.defaultBlockState());
        set(level, pos, x, bed, z, bedBlock(x, bed, z, rocky));
        support(level, pos, x, bed, z);                                          // a cave under the bed: closed
        plug(level, pos, x, bed - 2, above(low), z);                                // ... as deep as the bed next to it
        for (int y = bed + 1; y <= wl; y++) set(level, pos, x, y, z, river);
        if (!rocky && !nearLake && depth == 1 && d.edge[i] > -0.6F && hash(x >> 1, wl, z >> 1) < RiverMouths.ICE_FRINGE) {
            set(level, pos, x, wl, z, Blocks.ICE.defaultBlockState());          // calm shallow edge: thin ice
            return Integer.MIN_VALUE;
        }
        if (rocky && !nearLake && depth <= 2 && hash(x, wl + 7, z) < RAPID_STONES) {     // mountain stream: a stone
            set(level, pos, x, wl, z, hash(z, wl, x) < 0.5 ? Blocks.STONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            return Integer.MIN_VALUE;
        }
        return wl;
    }

    /**
     * Rim: cut RIM_DROP below its natural ground and covered with gravel/stone, packed ice at the water line -
     * so it always lies a step below the land and a step above the water. Land further away is untouched.
     */
    private static void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z,
                             RiverNetwork.ChunkData d, int i, int top, int rimTop, boolean tunnel, int low) {
        if (d.kind[i] != RiverNetwork.ChunkData.RIM) return;                     // plain land: untouched
        boolean rocky = d.rocky[i];
        if (tunnel) {                                                            // in a tunnel: a ledge beside the
            int ledge = Math.max(rimTop == top - RIM_DROP ? d.water[i] + 1 : rimTop, d.water[i] + 1);   // water,
            if (ledge > top) fill(level, pos, x, z, top, ledge, rocky);          // rock overhead
            for (int y = Math.min(top, d.water[i] + TUNNEL_HEAD); y > ledge; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
            set(level, pos, x, ledge, z, rimBlock(x, ledge, z, rocky));
            set(level, pos, x, ledge - 1, z, waterline(x, ledge - 1, z, rocky));
            plug(level, pos, x, ledge - 2, above(low), z);                          // no cave beside the deep water
            return;
        }
        if (rimTop > top) {                                                      // a hole next to the water: built up
            fill(level, pos, x, z, top, rimTop, rocky);
            top = rimTop;
        }
        clearPlants(level, pos, x, top, z);                                      // (both halves of tall plants)
        for (int y = top + 1; y > rimTop; y--) {                                  // cut (and clear what stood on it)
            BlockState st = level.getBlockState(pos.set(x, y, z));
            if (y <= top || st.canBeReplaced()) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
        }
        set(level, pos, x, rimTop, z, rimBlock(x, rimTop, z, rocky));
        set(level, pos, x, rimTop - 1, z, waterline(x, rimTop - 1, z, rocky));   // level of the water next to it
        plug(level, pos, x, rimTop - 2, above(low), z);                             // no cave beside the deep water
    }

    static boolean isRim(byte kind) {
        return kind == RiverNetwork.ChunkData.RIM;
    }

    private static boolean isRimMaterial(BlockState st) {
        return st.is(Blocks.GRAVEL) || st.is(Blocks.STONE) || st.is(Blocks.ANDESITE) || st.is(Blocks.COBBLESTONE)
                || st.is(Blocks.PACKED_ICE) || st.is(Blocks.STONE_SLAB) || st.is(Blocks.COBBLESTONE_SLAB)
                || st.is(Blocks.ANDESITE_SLAB);
    }

    static final int SHORE_CUT = 2;                   // a lake shore is cut down at most this much (higher: natural)

    /**
     * Lake shore (since 2026-10-22): a beach flush with the water at the water's edge, so one walks out of the lake
     * (it stood 1 block over the water - Rubenbuben4: "really hard to get out of them"), then 1 block up per block
     * (step: RiverMouths.shoreStep). Higher ground is cut down to that slope (by at most {@value #SHORE_CUT}, else the
     * shore stays as it is), lower ground is built up to hold the water. Next to a river (or a higher lake) it is
     * solid at least up to that water (need), so nothing runs out over the beach. The beach (up to 1 block over the
     * water) is ice and stones at the water line and gravel above it; cut land keeps its grass or snow on top.
     */
    private static void shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, RiverNetwork.ChunkData d,
                              int i, int top, int step, int need, int low) {
        int wl = d.water[i];
        int target = Math.max(wl + step, need);
        if (top > target + SHORE_CUT) return;                                    // steep shore: natural
        int least = Math.max(Math.min(target, wl + 1), need);                    // it holds the water next to it
        if (top <= target && top >= least && top > wl + 1) return;               // land above the beach: as it is
        BlockState surface = level.getBlockState(pos.set(x, top, z));
        clearPlants(level, pos, x, top, z);
        boolean cut = top > target;
        if (cut) {                                                               // cut down to the slope
            for (int y = top; y > target; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
            top = target;
        } else if (top < least) {                                                // too low: built up
            fill(level, pos, x, z, top, least, false);
            top = least;
        }
        if (top <= wl) set(level, pos, x, top, z, waterline(x, top, z, false));  // at the water: ice and stones
        else if (top <= wl + 1) set(level, pos, x, top, z, rimBlock(x, top, z, false));   // the beach: gravel
        else if (cut && (surface.is(BlockTags.DIRT) || surface.is(Blocks.SNOW_BLOCK) || surface.is(Blocks.GRAVEL)))
            set(level, pos, x, top, z, surface);                                 // land: its grass / snow moves down
        support(level, pos, x, top, z);
        plug(level, pos, x, top - 2, above(low), z);                                // no cave beside the lake's water
    }

    /**
     * Lake water: the bowl (RiverNetwork.bowlDepth, deeper where the hollow is deeper), a river's channel running on
     * into it, specks of ground in the water dug away.
     */
    private static int lake(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z,
                            RiverNetwork.ChunkData d, int i, int top, boolean inflow, int mouthDepth, int low) {
        int wl = d.water[i];
        if (top >= wl + 1) {                                                     // ground above the lake here
            int depth = Math.max(d.depth[i], mouthDepth);                        // (the river's channel / beside it)
            if (depth == 0) {
                if (top > wl + ISLET_DIG) return Integer.MIN_VALUE;              // a real island stays
                clearPlants(level, pos, x, top, z);                              // a speck of ground in the lake (or a
                for (int y = top; y > wl; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());   // pond dug
                top = wl;                                                        // into flat land): dug away, the
            } else {                                                             // bowl below as everywhere
                clearPlants(level, pos, x, top, z);
                int rw = Math.max(d.water[i], Math.min(d.riverWater[i], top - 1));  // a river runs in: its bed goes on,
                                                                                     // never below the lake's level
                int bed = rw - depth;
                BlockState river = ModBlocks.RIVER_WATER.get().defaultBlockState()
                        .setValue(RiverWaterBlock.FLOW, RiverWaterBlock.flowFor(d.angle[i]));
                for (int y = top; y > rw; y--) set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
                set(level, pos, x, bed, z, bedBlock(x, bed, z, d.rocky[i]));
                support(level, pos, x, bed, z);
                plug(level, pos, x, bed - 2, above(low), z);
                for (int y = bed + 1; y <= rw; y++) set(level, pos, x, y, z, river);
                return rw;
            }
        }
        clearPlants(level, pos, x, top, z);
        int bed = Math.min(RiverMouths.lakeBed(top, wl, mouthDepth), wl - Math.max(1, d.lakeDepth[i]));
        set(level, pos, x, bed, z, bedBlock(x, bed, z, d.rocky[i] && d.depth[i] > 0));
        support(level, pos, x, bed, z);
        plug(level, pos, x, bed - 2, above(low), z);                                // no cave beside a deeper part
        for (int y = bed + 1; y <= wl; y++) set(level, pos, x, y, z, Blocks.WATER.defaultBlockState());
        if (inflow) return wl;                                                   // the river's water keeps it open
        if (RiverMouths.fringeIce(x, wl, z, wl - bed)) {                          // shallow lake edge: ice
            set(level, pos, x, wl, z, Blocks.ICE.defaultBlockState());
            return Integer.MIN_VALUE;
        }
        BlockState lid = lakeIce(x, wl, z);                                      // mountain tarns freeze over
        if (lid != null) {
            set(level, pos, x, wl, z, lid);
            return Integer.MIN_VALUE;
        }
        return wl;                                                               // open water (where it is higher than
    }                                                                            // its way out, it is woken up)

    // ---- TUNE: trees at the river ----
    static final float TREE_CLEAR = 3.0F;            // land this close to the rim is kept free of trees, too
    static final int TREE_KEEP_AWAY = 5;             // a new tree's trunk keeps this far from that strip (its crown)
    static final int TREE_SCAN = 48;                 // how high above the ground trees are looked for
    static final int LEAF_SEARCH = 4;                // leaves over the river: the trunk within this many leaves is cut
    static final int LEAF_REACH = 6;                 // leaves this close to a remaining trunk stay (neighbouring trees)
    static final int MAX_LOGS = 3000;                // safety limit per chunk

    /** True if this column is water, rim or the tree-free strip next to them. */
    /** Land over a river tunnel: it stays as it is - trees and snow included. */
    public static boolean overTunnel(ServerLevel level, RiverNetwork.ChunkData d, int x, int z, int i) {
        return overTunnel(network(level), d, x, z, i);
    }

    static boolean overTunnel(RiverNetwork net, RiverNetwork.ChunkData d, int x, int z, int i) {
        return tunnel(d, i);
    }

    static boolean treeFree(RiverNetwork.ChunkData d, int i) {
        byte k = d.kind[i];
        return k == RiverNetwork.ChunkData.CHANNEL || k == RiverNetwork.ChunkData.RIM || k == RiverNetwork.ChunkData.LAKE
                || k == RiverNetwork.ChunkData.SHORE || (k == RiverNetwork.ChunkData.NEAR && d.edge[i] <= TREE_CLEAR);
    }

    /** For the forest: true if a tree with its trunk here would stand in or hang over a river or lake. */
    public static boolean tooCloseForTree(ServerLevel level, int x, int z) {
        RiverNetwork net = network(level);
        if (net == null) return false;
        for (int s = 0; s < 9; s++) {
            double a = s * Math.PI / 4.0;
            int px = s == 8 ? x : x + (int) Math.round(Math.cos(a) * TREE_KEEP_AWAY);
            int pz = s == 8 ? z : z + (int) Math.round(Math.sin(a) * TREE_KEEP_AWAY);
            RiverNetwork.ChunkData d = net.chunk(Math.floorDiv(px, 16), Math.floorDiv(pz, 16));
            int n = Math.floorMod(px, 16) * 16 + Math.floorMod(pz, 16);
            if (d.any && treeFree(d, n) && !overTunnel(net, d, px, pz, n)) return true;
        }
        return false;
    }

    private static boolean isTree(BlockState st) {
        return st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES);
    }

    private static boolean isSnowOnTree(BlockState st) {
        return st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW);
    }

    /** Top solid ground block of a column: trees, the snow on them, plants and air are skipped. */
    static int groundY(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        int min = level.getMinBuildHeight();
        while (y > min) {
            BlockState st = level.getBlockState(pos.set(x, y, z));
            if (isTree(st) || (st.canBeReplaced() && st.getFluidState().isEmpty())) {
                y--;
            } else if (isSnowOnTree(st) && isTree(level.getBlockState(pos.set(x, y - 1, z)))) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /**
     * Takes the trees out that stand in or hang over the river, its rim and a narrow strip next to it - whole
     * trees (trunk, branches, their crown and the snow on it), not just the part over the water, so no
     * floating leaves or cut trunks are left. Leaves that belong to a tree that stays are only trimmed where
     * they hang over the strip. Only blocks of this chunk and its direct neighbours are touched.
     */
    private static void clearTrees(WorldGenLevel level, BlockPos.MutableBlockPos pos, ChunkPos chunk, RiverNetwork.ChunkData d) {
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        int bx0 = x0 - 16, bx1 = x0 + 31, bz0 = z0 - 16, bz1 = z0 + 31;
        HashSet<Long> logSeeds = new HashSet<>(), trim = new HashSet<>();
        ArrayDeque<long[]> leafQueue = new ArrayDeque<>();
        HashMap<Long, Integer> leafSeen = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            if (!treeFree(d, i) || overTunnel(level.getLevel(), d, chunk.getMinBlockX() + i / 16, chunk.getMinBlockZ() + i % 16, i)) continue;
            int x = x0 + i / 16, z = z0 + i % 16;
            int g = groundY(level, pos, x, z);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = g + 1; y <= Math.min(top, g + TREE_SCAN); y++) {
                BlockState st = level.getBlockState(pos.set(x, y, z));
                long key = BlockPos.asLong(x, y, z);
                if (st.is(BlockTags.LOGS)) {
                    logSeeds.add(key);
                } else if (st.is(BlockTags.LEAVES)) {
                    trim.add(key);
                    if (leafSeen.putIfAbsent(key, 0) == null) leafQueue.add(new long[]{key, 0});
                }
            }
        }
        if (logSeeds.isEmpty() && trim.isEmpty()) return;
        // leaves over the river: find the trunk they belong to (the nearest logs through the crown)
        while (!leafQueue.isEmpty()) {
            long[] e = leafQueue.poll();
            if (e[1] >= LEAF_SEARCH) continue;
            BlockPos p = BlockPos.of(e[0]);
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!inBox(n, bx0, bx1, bz0, bz1)) continue;
                long nk = n.asLong();
                BlockState st = level.getBlockState(n);
                if (st.is(BlockTags.LOGS)) {
                    logSeeds.add(nk);
                } else if (st.is(BlockTags.LEAVES) && leafSeen.putIfAbsent(nk, (int) e[1] + 1) == null) {
                    leafQueue.add(new long[]{nk, e[1] + 1});
                }
            }
        }
        // whole trunks and branches (logs touching each other, also diagonally)
        HashSet<Long> logs = new HashSet<>(logSeeds);
        ArrayDeque<Long> queue = new ArrayDeque<>(logSeeds);
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = BlockPos.of(queue.poll());
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!inBox(n, bx0, bx1, bz0, bz1) || logs.contains(n.asLong())) continue;
                if (level.getBlockState(n).is(BlockTags.LOGS)) {
                    logs.add(n.asLong());
                    queue.add(n.asLong());
                }
            }
        }
        // their crown: leaves within LEAF_REACH of a removed log - unless a trunk that stays is just as close
        HashMap<Long, Integer> crown = new HashMap<>();
        HashSet<Long> otherLogs = new HashSet<>();
        ArrayDeque<long[]> q = new ArrayDeque<>();
        for (long l : logs) q.add(new long[]{l, 0});
        while (!q.isEmpty()) {
            long[] e = q.poll();
            if (e[1] >= LEAF_REACH) continue;
            BlockPos p = BlockPos.of(e[0]);
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!inBox(n, bx0, bx1, bz0, bz1)) continue;
                long nk = n.asLong();
                if (crown.containsKey(nk) || logs.contains(nk)) continue;
                BlockState st = level.getBlockState(n);
                if (st.is(BlockTags.LEAVES)) {
                    crown.put(nk, (int) e[1] + 1);
                    q.add(new long[]{nk, e[1] + 1});
                } else if (st.is(BlockTags.LOGS)) {
                    otherLogs.add(nk);
                }
            }
        }
        HashSet<Long> keep = new HashSet<>();                                      // reached from a tree that stays
        q.clear();
        for (long l : otherLogs) q.add(new long[]{l, 0});
        while (!q.isEmpty()) {
            long[] e = q.poll();
            if (e[1] >= LEAF_REACH) continue;
            BlockPos p = BlockPos.of(e[0]);
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                long nk = p.relative(dir).asLong();
                Integer own = crown.get(nk);
                if (own == null || keep.contains(nk) || e[1] + 1 > own) continue;   // closer to the removed tree
                keep.add(nk);
                q.add(new long[]{nk, e[1] + 1});
            }
        }
        HashSet<Long> remove = new HashSet<>(logs);
        for (long l : crown.keySet()) if (!keep.contains(l)) remove.add(l);
        remove.addAll(trim);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (long l : remove) {
            BlockPos p = BlockPos.of(l);
            level.setBlock(p, air, 2);
            BlockPos.MutableBlockPos up = p.mutable().move(0, 1, 0);           // the snow that lay on it
            while (isSnowOnTree(level.getBlockState(up))) {
                level.setBlock(up, air, 2);
                up.move(0, 1, 0);
            }
        }
    }

    private static boolean inBox(BlockPos p, int bx0, int bx1, int bz0, int bz1) {
        return p.getX() >= bx0 && p.getX() <= bx1 && p.getZ() >= bz0 && p.getZ() <= bz1;
    }

    private static void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
        level.setBlock(pos.set(x, y, z), state, 2);
    }

    static final double SLAB_CHANCE = 0.6;            // share of rim blocks one below the land that get a slab

    /**
     * Softer banks: where the land next to the rim is exactly one block higher, a slab on the rim makes a
     * half step (stone/cobblestone/andesite). Inside this chunk only.
     */
    private static void halfSteps(WorldGenLevel level, BlockPos.MutableBlockPos pos, ChunkPos chunk, RiverNetwork.ChunkData d,
                                  RiverNetwork net) {
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                byte k = d.kind[lx * 16 + lz];
                if (k != RiverNetwork.ChunkData.RIM && k != RiverNetwork.ChunkData.SHORE) continue;
                int x = x0 + lx, z = z0 + lz;
                if (k == RiverNetwork.ChunkData.RIM && tunnel(d, lx * 16 + lz)) continue;   // inside a tunnel
                int top = groundY(level, pos, x, z);
                if (!level.getBlockState(pos.set(x, top + 1, z)).isAir()) continue;
                if (hash(x, top + 3, z) >= SLAB_CHANCE) continue;
                boolean step = false;
                for (int dir = 0; dir < 4 && !step; dir++) {
                    int nx = lx + (dir == 0 ? 1 : dir == 1 ? -1 : 0), nz = lz + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                    if (nx < 0 || nz < 0 || nx > 15 || nz > 15) continue;
                    byte nk = d.kind[nx * 16 + nz];
                    if (nk != RiverNetwork.ChunkData.NONE && nk != RiverNetwork.ChunkData.NEAR) continue;   // land only
                    step = groundY(level, new BlockPos.MutableBlockPos(), x0 + nx, z0 + nz) == top + 1;
                }
                if (!step) continue;
                double v = hash(z, top, x);
                BlockState slab = (d.rocky[lx * 16 + lz] ? v < 0.5 : v < 0.25) ? Blocks.STONE_SLAB.defaultBlockState()
                        : v < 0.75 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.ANDESITE_SLAB.defaultBlockState();
                set(level, pos, x, top + 1, z, slab);
            }
        }
    }

    // ---- TUNE: Frozen Realm banks ----
    // (thin ice on calm edges: RiverMouths.ICE_FRINGE)
    static final double WATERLINE_ICE = 0.55;         // share of packed ice in the band at the water line
    static final double ROCKY_WATERLINE_ICE = 0.2;    // ... at mountain streams
    static final double RAPID_STONES = 0.04;          // share of mountain-stream columns with a stone at the surface

    // ---- TUNE: frozen mountain lakes ----
    // (when lakes freeze over: RiverMouths.TARN_FROM_Y / TARN_FULL_Y / TARN_ICE_SIZE)
    static final double TARN_PACKED = 0.25, TARN_SNOW = 0.18;        // share of packed ice / snow drifts on the ice

    /**
     * Ice on a lake at this height: low lakes stay open, higher up sheets of ice with open leads between them,
     * the highest tarns frozen over completely (clear and packed ice, a few snow drifts). Null = open water.
     */
    static BlockState lakeIce(int x, int wl, int z) {
        if (!RiverMouths.frozenOver(x, wl, z)) return null;                // low lake or open lead
        double t = RiverMouths.tarnFreeze(wl);
        double v = hash(x, wl + 31, z);
        if (t > 0.6 && smoothNoise(x / 4.0, z / 4.0, 43) < TARN_SNOW * t) return Blocks.SNOW_BLOCK.defaultBlockState();
        return v < TARN_PACKED ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
    }

    private static double smoothNoise(double x, double z, int salt) {
        return RiverMouths.smoothNoise(x, z, salt);
    }

    // ---- TUNE: river and lake beds (calmer since 2026-10-22: before every block was gravel, cobble or stone at random)
    static final double BED_SAND = 0.3;               // share of the lowland bed in patches of sand (the rest gravel)
    static final double BED_COBBLE = 0.04;            // single cobbles in it
    static final double ROCKY_BED_STONE = 0.55;       // mountain streams: patches of stone, the rest cobbles and gravel

    /** River and lake bed: gravel with patches of sand and a few cobbles (lowland); stone, cobbles, gravel (mountain streams). */
    private static BlockState bedBlock(int x, int y, int z, boolean rocky) {
        double patch = smoothNoise(x / 5.0, z / 5.0, 47), v = hash(x, y, z);
        if (rocky) {
            if (patch < ROCKY_BED_STONE) return v < 0.85 ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            return v < 0.6 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        }
        if (v < BED_COBBLE) return Blocks.COBBLESTONE.defaultBlockState();
        return patch < BED_SAND ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    /** The band right at the water line: patches of packed ice between the rim stones. */
    private static BlockState waterline(int x, int y, int z, boolean rocky) {
        double v = hash(x >> 1, y, z >> 1) * 0.6 + hash(x, y, z) * 0.4;
        if (v < (rocky ? ROCKY_WATERLINE_ICE : WATERLINE_ICE)) return Blocks.PACKED_ICE.defaultBlockState();
        return rimBlock(x, y, z, rocky);
    }

    /** Rim: gravel with stone, andesite and cobbles (lowland); stone, andesite and cobbles (mountain streams). */
    private static BlockState rimBlock(int x, int y, int z, boolean rocky) {
        double v = hash(x >> 1, y, z >> 1) * 0.6 + hash(x, y, z) * 0.4;
        if (rocky) {
            if (v < 0.45) return Blocks.STONE.defaultBlockState();
            if (v < 0.75) return Blocks.ANDESITE.defaultBlockState();
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (v < 0.55) return Blocks.GRAVEL.defaultBlockState();
        if (v < 0.75) return Blocks.STONE.defaultBlockState();
        if (v < 0.9) return Blocks.ANDESITE.defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    private static double hash(int x, int y, int z) {
        return RiverMouths.hash(x, y, z);
    }
}
