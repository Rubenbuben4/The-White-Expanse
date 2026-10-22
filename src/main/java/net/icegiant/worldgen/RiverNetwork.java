package net.icegiant.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Frozen Realm's rivers and lakes, worked out from the finished terrain (own system, not vanilla rivers).
 *
 * <p><b>Drainage.</b> The terrain is read on a coarse grid ({@value #CELL} blocks; each grid point nudged a
 * little so rivers don't follow grid lines). Every grid point drains to its steepest lower neighbour. If it has
 * none, it may drain over a <i>small</i> rise (at most {@value #MAX_CUT} blocks) to a lower point up to
 * {@value #JUMP_RADIUS} cells away; otherwise it is a hollow. Because this is decided point by point, every
 * point also knows exactly which points drain into it - so the whole river system above any point can be
 * worked out, no matter which chunk asks first.
 *
 * <p><b>Rivers.</b> Sources sit in hollows between mountain ridges (and a few in the hilly lowland). A river
 * runs from its source along the drainage. Its width (in blocks) follows Rubenbuben4's rules:
 * <ul>
 *   <li>every river starts 1 block wide;</li>
 *   <li>on its own it gets 1 block wider after {@value #WIDEN_FIRST} blocks, and every further widening needs
 *       {@value #WIDEN_STEP} blocks more than the one before (50, 65, 80, ...);</li>
 *   <li>where another river joins, it gets 1 block wider at once (and the length count starts again).</li>
 * </ul>
 * On steep ground a river is drawn narrower (a torrent, see {@link #shown}); below it, it only widens again by the
 * same rule. Between the grid points the width changes gradually, never in one step.
 * <b>Lakes.</b> Where a river runs into a hollow, the water rises like real water would until it reaches the
 * lowest saddle (see {@link #outlet}). A shallow dip (less than {@value #MIN_LAKE_DEPTH} blocks) is simply cut
 * through. A deeper hollow becomes a lake filled up to just below its saddle, and the river flows on out of it
 * over the saddle - lake chains down to the sea. Not always: some lakes fed only by a small river (at most
 * {@value #CLOSED_MAX_WIDTH} wide) keep their water and the river ends there, as does any river whose hollow
 * is too deep to fill (more than {@value #LAKE_MAX_DEPTH} blocks for a small river, {@value #DEEP_LAKE_MAX_DEPTH}
 * for a big one) or has no way out within {@value #LINK_R} grid cells.
 *
 * <p><b>Shape.</b> The water surface is 1 block below the terrain right at the river (the gravel/stone rim
 * is at the height of the land around it) and the bed is a flat oval, deepest in the middle.
 */
public final class RiverNetwork {

    // ---- TUNE ----
    static final int CELL = 32;                       // coarse grid (blocks)
    static final int TILE_CELLS = 64;                 // river tile = 2048 blocks
    static final int SOURCE_SPACING = 128;            // at most one source per 128 x 128 blocks (multiple of CELL)
    static final double JITTER = 10.0;                // grid points nudged up to this far (natural bends)
    static final int JUMP_RADIUS = 3;                 // a hollow may drain over a small rise this many cells away
    static final double MAX_CUT = 4.0;                // ... if that rise is at most this high
    static final double MOUNTAIN_SRC_MIN_Y = 110, MOUNTAIN_SRC_MAX_Y = 460, MOUNTAIN_SRC_HOLLOW = 5, MOUNTAIN_SRC_CHANCE = 0.9;
    static final double LOW_SRC_MIN_Y = 68, LOW_SRC_HOLLOW = 1, LOW_SRC_CHANCE = 0.35;
    static final double WIDEN_FIRST = 50.0, WIDEN_STEP = 15.0;   // width rule (blocks of river length)
    static final int MAX_WIDTH = 14;
    static final int LAKE_MAX_DEPTH = 8;              // deepest lake (blocks below its lowest point's ground) ...
    static final int DEEP_LAKE_MAX_DEPTH = 16;        // ... unless a big river fills a deeper basin up to its saddle
    static final int LAKE_GRID = 4;                   // lake outline resolution (blocks)
    static final int ISLET_MAX = 2;                   // tiny islets / notches up to this high above a lake are dug away
    static final int LAKE_MAX_POINTS = 6000;          // biggest lake: ~96 000 blocks^2
    static final int POND_RADIUS = 5;                 // on flat land: a round pond of 5 x 4 = 20 blocks radius
    static final double SEA_Y = 63.0;
    static final int SEA_MIN_CELLS = 48;              // below sea level and at least this big (~50 000 blocks^2): sea
    /** How far around a river point columns are looked at (channel, rim, gorge sides, snow fade). */
    public static final int REACH = 20;
    static final int MOUTH_FLARE = 3;                 // a river is up to this much wider where it meets the sea
    static final int MAX_RIVER_DEPTH = 7;             // deepest river bed (the widest rivers, in the middle)
    // ---- TUNE: lake bowls (deeper towards the middle, also on flat land) ----
    static final int BOWL_MAX = 9;                    // deepest bowl of the biggest lakes
    static final double BOWL_PER_BLOCK = 0.25;        // deepest point: 1.5 + this x blocks from the water's edge
    static final double ROCKY_SLOPE = 0.25;           // river falls more than 1 block per 4: rocky mountain stream
    // ---- TUNE: valley floor and mountain streams ----
    static final int VALLEY_SHIFT = 10;               // a river moves up to this far sideways to the lowest ground
    static final double VALLEY_KEEP = 0.35;           // ... but each block sideways must win this much height
    static final double VALLEY_TAPER = 12.0;          // no shift at the piece ends (they meet the next piece)
    static final double STEEP_SLOPE = 0.12, VERY_STEEP_SLOPE = 0.25;  // on slopes this steep ...
    static final int STEEP_MAX_WIDTH = 4, VERY_STEEP_MAX_WIDTH = 2;   // ... a river is drawn this narrow (torrent, see shown)
    static final double TORRENT_REGROW = 32.0;        // ... and gets 1 block wider / narrower per this much river before / after it
    // ---- TUNE: lakes that overflow ----
    static final int LINK_R = 24;                     // a hollow looks this many cells (768 blocks) for its way out
    static final int LINK_MAX_CELLS = 2000;           // ... over at most this many cells
    static final double MIN_LAKE_DEPTH = 2.0;         // shallower hollows: no lake, the river just cuts through
    // ---- TUNE: meanders on flat land ----
    static final double MEANDER_AMP = 4.0;            // biggest bend (blocks) of a stream up to ...
    static final int MEANDER_FULL_WIDTH = 4;          // ... this wide; wider rivers bend less (they need longer loops)
    static final double MEANDER_PER_LENGTH = 0.25;    // a bend is at most this share of its length
    static final double MEANDER_FLAT = 1.5;           // full bends where the land across the river varies <= this
    // ---- TUNE: lakes without a river (tarns in mountain hollows, a few ponds below) ----
    static final double TARN_MIN_Y = 130.0, TARN_CHANCE = 0.6, POND_CHANCE = 0.12, LONE_LAKE_MIN_DEPTH = 2.5;
    static final int LONE_LAKE_MAX_DEPTH = 6;
    static final double CLOSED_CHANCE = 0.25;         // share of small-river lakes without an outflow ...
    static final int CLOSED_MAX_WIDTH = 3;            // ... (a river wider than this always fills its lake)
    // ---- TUNE: the water's way down (never up) ----
    static final int JUNCTION_SIDE = 4;               // the ground at a grid point is looked at this far to the sides
    // ---- TUNE: the river looks for its way down (it does not cut its own) ----
    static final int PATH_GRID = 3;                   // the way between two grid points is searched on this grid (blocks)
    static final int PATH_MARGIN = 14;                // ... and may swing out this far beside the grid points
    static final double RISE_COST = 25.0;             // every block uphill costs as much as 25 blocks of detour
    static final int MEET_RADIUS = 8;                 // where two pieces meet: the lowest ground this close to half way

    private static final long NONE = Long.MIN_VALUE;
    private static final long UNKNOWN_DOWN = Long.MIN_VALUE + 1;
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int[] RX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] RZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private final FrozenTerrainMath math;
    private final long seed;
    private final ConcurrentHashMap<Long, float[]> heights = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, long[]> downs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Outlet> outlets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<Long, List<Long>>> inflows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<Long>> pitsNear = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> seaCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, int[]> stats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Tile> tiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> junctions = new ConcurrentHashMap<>();

    public RiverNetwork(FrozenTerrainMath math, long seed) {
        this.math = math;
        this.seed = seed;
    }

    /** The terrain the network was worked out from (the river feature reads the untouched ground height from it). */
    public FrozenTerrainMath math() {
        return this.math;
    }

    // ------------------------------------------------------------------ shape rules

    /** Half width of the water for a river width in blocks (0.75 for 1: no gaps on diagonals). */
    public static double halfWidth(int width) {
        return halfWidth((double) width);
    }

    /** ... for a width between two whole numbers (where a river gets wider or narrower along a piece). */
    public static double halfWidth(double width) {
        return Math.max(0.75, width * 0.5 + 0.25);
    }

    /**
     * Deepest point of the round bed: half a circle as wide as the river, a little deeper than half its width (in
     * blocks a true half circle looks flat), at most {@value #MAX_RIVER_DEPTH}. (Until 2026-10-22 an oval of at most
     * 5 blocks.)
     */
    public static double maxDepth(int width) {
        return Math.min(MAX_RIVER_DEPTH, halfWidth(width) + 0.5);
    }

    /** Thickness of the gravel/stone rim. */
    public static int border(int width) {
        return 1 + width / 5;
    }

    static double gap(int width) {
        return WIDEN_FIRST + WIDEN_STEP * (width - 1);
    }

    // ------------------------------------------------------------------ chunk query

    /** What the river system does to the columns of one chunk. */
    public static final class ChunkData {
        public static final byte NONE = 0, CHANNEL = 1, RIM = 2, NEAR = 3, LAKE = 4, SHORE = 5;
        public final byte[] kind = new byte[256];
        public final int[] water = new int[256];      // top water block (channel; rim/near: the river next to it; lake)
        public final int[] depth = new int[256];      // channel: water depth of this column
        public final int[] lakeDepth = new int[256];  // lake: depth of its bowl here (at least 1; deeper ground stays)
        public final float[] edge = new float[256];   // near: distance from the rim (snow fade, gorge sides)
        public final float[] gorge = new float[256];  // how much deeper than the land the river is cut here
        public final int[] riverWater = new int[256]; // lake columns a river runs into: the river's water level
        public final float[] angle = new float[256];  // river columns: direction downstream (radians, 0 = +X, pi/2 = +Z)
        public final boolean[] rocky = new boolean[256]; // mountain stream (steep): stone instead of gravel
        public boolean any;
    }

    private final ConcurrentHashMap<Long, ChunkData> chunkCache = new ConcurrentHashMap<>();

    /** River/lake data of one chunk (cached: the river feature, snow cover and boulders all ask). */
    public ChunkData chunk(int chunkX, int chunkZ) {
        ChunkData d = this.chunkCache.get(pack(chunkX, chunkZ));
        if (d != null) return d;
        d = computeChunk(chunkX, chunkZ);
        if (this.chunkCache.size() > 1024) this.chunkCache.clear();
        this.chunkCache.put(pack(chunkX, chunkZ), d);
        return d;
    }

    private ChunkData computeChunk(int chunkX, int chunkZ) {
        ChunkData out = new ChunkData();
        List<float[]> pts = pointsNear(chunkX, chunkZ);
        List<Lake> lakes = lakesNear(chunkX, chunkZ);
        if (pts.isEmpty() && lakes.isEmpty()) return out;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int i = lx * 16 + lz, x = chunkX * 16 + lx, z = chunkZ * 16 + lz;
                byte lake = lakeColumn(lakes, x, z, out, i);
                float[] bp = null;
                double best = Double.MAX_VALUE;
                for (float[] p : pts) {
                    double dx = p[0] - (x + 0.5), dz = p[1] - (z + 0.5);
                    double e = Math.sqrt(dx * dx + dz * dz) - p[3];
                    if (e < best) {
                        best = e;
                        bp = p;
                    }
                }
                boolean river = bp != null && best <= REACH;
                int width = river ? (int) bp[4] : 0;
                if (lake == ChunkData.LAKE) {                                    // lake water wins ...
                    if (river && best <= 0.0) {                                  // ... but where the lake's ground is
                        out.depth[i] = channelDepth(best, bp[3], width);         //     a bit higher, the river keeps
                        out.riverWater[i] = (int) bp[2];                         //     its bed into the water
                        out.angle[i] = bp[6];
                    }
                    continue;
                }
                if (!river || (lake == ChunkData.SHORE && best > border(width))) continue;   // shore (or nothing)
                // a river may only break through a lake shore where it really runs into the lake (at lake level);
                // one passing below a higher lake leaves the shore standing
                int lakeLevel = lake == ChunkData.SHORE ? out.water[i] : Integer.MIN_VALUE;
                if (lake == ChunkData.SHORE && bp[2] < lakeLevel - 1) continue;
                out.any = true;
                out.water[i] = (int) bp[2];
                out.edge[i] = (float) best;
                out.gorge[i] = bp[5];
                out.angle[i] = bp[6];
                out.rocky[i] = bp[7] > ROCKY_SLOPE;
                if (best <= 0.0) {
                    out.kind[i] = ChunkData.CHANNEL;                              // river beats the lake shore
                    out.depth[i] = channelDepth(best, bp[3], width);
                } else if (best <= border(width)) {
                    out.kind[i] = ChunkData.RIM;          // (it holds the water next to it: RiverFeature, "need")
                } else {
                    out.kind[i] = ChunkData.NEAR;
                    out.edge[i] = (float) (best - border(width));               // distance from the rim
                }
            }
        }
        return out;
    }

    /**
     * Round bed (half a circle across the river): depth of a channel column (edge = distance from the water's edge,
     * negative inside). The same everywhere along the river - no more deep outer bends and gravel bars in the inner
     * ones (until 2026-10-22: in rivers of 4 - 8 blocks they made the bed ragged, ridges and holes next to each other -
     * Rubenbuben4: "rau und ungleich").
     */
    static int channelDepth(double edge, double halfWidth, int width) {
        double d = Math.max(0.0, edge + halfWidth) / halfWidth;                 // 0 = middle, 1 = water's edge
        return Math.max(1, (int) Math.round(maxDepth(width) * Math.sqrt(Math.max(0.0, 1.0 - d * d))));
    }

    /**
     * Lake water (or shore) at this column; where lakes overlap (a small one inside a bigger one) the higher wins.
     * The outline is smoothed between the 4-block grid points (the share of water at the four around this
     * column), so the shore runs in soft curves and diagonals instead of 4 x 4 steps. A grid point g stands at
     * block 4g, where its ground was measured (groundAt). (Until 2026-10-17 the outline was drawn 2 blocks off
     * towards +x/+z: gravel dams where the lake should be on the one side, dug-out banks on the other.)
     * Between a wet and a dry grid point the water reaches as far as the ground lies below it - at a steep shore
     * the half way line could lie 4 blocks under the water and became a gravel dam (until 2026-10-17).
     */
    private byte lakeColumn(List<Lake> lakes, int x, int z, ChunkData out, int i) {
        byte kind = ChunkData.NONE;
        int level = Integer.MIN_VALUE;
        double u = (x + 0.5) / LAKE_GRID, w = (z + 0.5) / LAKE_GRID;          // grid units, column middle
        long g = pack((int) Math.round(u), (int) Math.round(w));               // nearest grid point
        int gx = (int) Math.floor(u), gz = (int) Math.floor(w);
        double tx = u - gx, tz = w - gz;
        int ground = Integer.MIN_VALUE;                                        // (asked only at shores)
        Lake won = null;
        for (Lake lake : lakes) {
            if (x < lake.minX || x > lake.maxX || z < lake.minZ || z > lake.maxZ) continue;
            double share = 0.0;
            for (int c = 0; c < 4; c++) {
                int cx = gx + (c & 1), cz = gz + (c >> 1);
                if (!lake.water.contains(pack(cx, cz))) continue;
                share += ((c & 1) == 1 ? tx : 1.0 - tx) * ((c >> 1) == 1 ? tz : 1.0 - tz);
            }
            byte k = share >= 0.5 ? ChunkData.LAKE
                    : share > 0.0 || lake.shore.contains(g) || lake.water.contains(g) ? ChunkData.SHORE : ChunkData.NONE;
            if (k == ChunkData.SHORE && share > 0.0) {                         // shore, but below the water: lake
                if (ground == Integer.MIN_VALUE) ground = (int) Math.ceil(this.math.surfaceY(x + 0.5, z + 0.5)) - 1;
                if (ground <= lake.level - 1) k = ChunkData.LAKE;
            }
            if (k == ChunkData.NONE) continue;
            // water beats shore at the same level; a higher lake beats a lower one
            if (lake.level > level || (lake.level == level && k == ChunkData.LAKE)) {
                level = lake.level;
                kind = k;
                won = lake;
            }
        }
        if (kind == ChunkData.NONE) return kind;
        out.kind[i] = kind;
        out.water[i] = level;
        out.any = true;
        if (kind == ChunkData.LAKE) {                                          // its bowl: from the distance to the
            double from = 0.0;                                                 // shore at the four grid points round
            for (int c = 0; c < 4; c++) {                                      // it (0 at dry ones), in blocks from
                double wt = ((c & 1) == 1 ? tx : 1.0 - tx) * ((c >> 1) == 1 ? tz : 1.0 - tz);   // the water's edge
                from += wt * won.fromShore(gx + (c & 1), gz + (c >> 1));
            }
            out.lakeDepth[i] = bowlDepth(from * LAKE_GRID - LAKE_GRID / 2.0, won.reach);
        }
        return kind;
    }

    // ------------------------------------------------------------------ grid helpers

    /** Smooth terrain height of a grid cell (at its nudged point). */
    public double cellY(int cx, int cz) {
        float[] t = this.heights.get(pack(cx >> 6, cz >> 6));
        if (t == null) {
            t = this.heights.computeIfAbsent(pack(cx >> 6, cz >> 6), k -> {
                int bx = (cx >> 6) << 6, bz = (cz >> 6) << 6;
                float[] a = new float[64 * 64];
                for (int i = 0; i < 64; i++) {
                    for (int j = 0; j < 64; j++) {
                        a[i * 64 + j] = (float) this.math.smoothSurfaceY(px(bx + i, bz + j), pz(bx + i, bz + j));
                    }
                }
                return a;
            });
            if (this.heights.size() > 3000) this.heights.clear();
        }
        return t[(cx & 63) * 64 + (cz & 63)];
    }

    double px(int cx, int cz) {
        return cx * CELL + CELL * 0.5 + JITTER * (hash01(cx, cz, 11) * 2.0 - 1.0);
    }

    double pz(int cx, int cz) {
        return cz * CELL + CELL * 0.5 + JITTER * (hash01(cx, cz, 23) * 2.0 - 1.0);
    }

    private double hash01(long a, long b, int salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ (this.seed + salt) * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int unpackX(long k) {
        return (int) (k >> 32);
    }

    static int unpackZ(long k) {
        return (int) k;
    }

    /**
     * Is this grid point the sea? Ground below sea level only counts if it belongs to a big enough water body
     * ({@value #SEA_MIN_CELLS} grid points): a small dip below sea level inland is just a pond - rivers run into
     * it like into a lake and on out of it, instead of ending there.
     */
    boolean sea(int cx, int cz) {
        if (cellY(cx, cz) >= SEA_Y) return false;
        long key = pack(cx, cz);
        Boolean v = this.seaCache.get(key);
        if (v != null) return v;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> todo = new ArrayDeque<>();
        seen.add(key);
        todo.add(key);
        while (!todo.isEmpty() && seen.size() < SEA_MIN_CELLS) {
            long k = todo.poll();
            for (int d = 0; d < 8; d++) {
                int nx = unpackX(k) + RX[d], nz = unpackZ(k) + RZ[d];
                long n = pack(nx, nz);
                if (seen.contains(n) || cellY(nx, nz) >= SEA_Y) continue;
                seen.add(n);
                todo.add(n);
            }
        }
        boolean big = seen.size() >= SEA_MIN_CELLS;
        if (this.seaCache.size() > 200000) this.seaCache.clear();
        if (big) this.seaCache.put(key, true);                        // (only this point: the search stopped early)
        else for (long k : seen) this.seaCache.put(k, false);           // the whole pond
        return big;
    }

    // ------------------------------------------------------------------ drainage (local, cached)

    /**
     * Where a grid point drains to, or NONE (sea, or a lake without outflow). A hollow whose lake overflows
     * drains to the first point beyond its saddle (a "link", see {@link #isLink}).
     */
    long down(int cx, int cz) {
        long v = naturalDown(cx, cz);
        if (v != NONE || sea(cx, cz)) return v;
        return isLink(cx, cz) ? outlet(cx, cz).exit : NONE;          // a hollow: does its lake overflow?
    }

    /** Does this hollow drain through its lake (or a shallow dip) over its saddle to a far point? */
    boolean isLink(int cx, int cz) {
        if (naturalDown(cx, cz) != NONE || sea(cx, cz)) return false;
        Outlet o = outlet(cx, cz);
        if (o.exit == NONE) return false;                            // no way out: a closed lake
        if (o.depth < MIN_LAKE_DEPTH) return true;                   // a shallow dip never stops a river
        if (!o.mayClose && o.depth <= LAKE_MAX_DEPTH + 1) return true;
        // a small river may end in its lake (it evaporates / freezes) and never fills a deep basin;
        // a big one always fills it up to the saddle
        return (stat(cx, cz) & 0xff) > CLOSED_MAX_WIDTH;
    }

    /** Steepest way down (or over a small rise), ignoring lakes (cached). */
    long naturalDown(int cx, int cz) {
        long key = pack(cx >> 6, cz >> 6);
        long[] tile = this.downs.get(key);
        if (tile == null) {
            if (this.downs.size() > 3000) this.downs.clear();
            tile = this.downs.computeIfAbsent(key, k -> {
                long[] a = new long[64 * 64];
                Arrays.fill(a, UNKNOWN_DOWN);
                return a;
            });
        }
        int idx = (cx & 63) * 64 + (cz & 63);
        long v = tile[idx];
        if (v == UNKNOWN_DOWN) {
            v = computeNaturalDown(cx, cz);
            tile[idx] = v;
        }
        return v;
    }

    private long computeNaturalDown(int cx, int cz) {
        if (sea(cx, cz)) return NONE;
        double h = cellY(cx, cz), best = 0.0;
        long out = NONE;
        for (int d = 0; d < 8; d++) {
            double drop = h - cellY(cx + RX[d], cz + RZ[d]);
            if (drop <= 0) continue;
            double s = drop / (d < 4 ? 1.0 : 1.41421356);
            if (s > best) {
                best = s;
                out = pack(cx + RX[d], cz + RZ[d]);
            }
        }
        if (out != NONE) return out;
        // hollow: drain over a small rise to a lower point a few cells away (never a deep cut)
        best = 0.0;
        for (int dx = -JUMP_RADIUS; dx <= JUMP_RADIUS; dx++) {
            for (int dz = -JUMP_RADIUS; dz <= JUMP_RADIUS; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                double drop = h - cellY(cx + dx, cz + dz);
                if (drop <= 0) continue;
                double s = drop / Math.sqrt(dx * dx + dz * dz);
                if (s <= best) continue;
                double rise = 0.0;                                       // highest point on the way
                int n = Math.max(Math.abs(dx), Math.abs(dz)) * 2;
                for (int k = 1; k < n; k++) {
                    double t = k / (double) n;
                    double wx = px(cx, cz) + (px(cx + dx, cz + dz) - px(cx, cz)) * t;
                    double wz = pz(cx, cz) + (pz(cx + dx, cz + dz) - pz(cx, cz)) * t;
                    rise = Math.max(rise, this.math.smoothSurfaceY(wx, wz) - h);
                }
                if (rise > MAX_CUT) continue;
                best = s;
                out = pack(cx + dx, cz + dz);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ lakes that overflow

    /**
     * The way out of a hollow: the water rises until it spills over the lowest saddle (found like water
     * would: always over the lowest ground next to what is already flooded). {@code exit} is the first
     * point beyond the saddle that lies lower than the hollow; {@code route} runs from the hollow to it.
     */
    static final class Outlet {
        boolean mayClose;      // a lake that may keep its water (if only a small river runs into it)
        double depth;          // saddle height above the hollow (lake depth)
        long exit = NONE;
        long[] route = new long[0];
    }

    Outlet outlet(int cx, int cz) {
        long self = pack(cx, cz);
        Outlet o = this.outlets.get(self);
        if (o != null) return o;
        o = computeOutlet(cx, cz);
        if (this.outlets.size() > 20000) this.outlets.clear();
        this.outlets.put(self, o);
        return o;
    }

    private Outlet computeOutlet(int cx, int cz) {
        Outlet o = new Outlet();
        double h0 = cellY(cx, cz), level = h0;
        Map<Long, Long> parent = new HashMap<>();
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        q.add(new double[]{h0, cx, cz});
        parent.put(pack(cx, cz), NONE);
        long exit = NONE;
        while (!q.isEmpty() && parent.size() < LINK_MAX_CELLS) {
            double[] e = q.poll();
            int x = (int) e[1], z = (int) e[2];
            if ((x != cx || z != cz) && (cellY(x, z) < h0 || sea(x, z))) {
                exit = pack(x, z);
                break;
            }
            level = Math.max(level, e[0]);
            if (level - h0 > DEEP_LAKE_MAX_DEPTH + 1) break;            // too deep to ever fill up
            for (int d = 0; d < 8; d++) {
                int nx = x + RX[d], nz = z + RZ[d];
                if (Math.abs(nx - cx) > LINK_R || Math.abs(nz - cz) > LINK_R) continue;
                long n = pack(nx, nz);
                if (parent.containsKey(n)) continue;
                parent.put(n, pack(x, z));
                double nh = cellY(nx, nz);
                q.add(new double[]{nh < h0 ? nh : Math.max(nh, e[0]), nx, nz});
            }
        }
        o.depth = level - h0;
        if (exit == NONE) return o;                                    // no way out: a closed lake
        o.mayClose = hash01(cx, cz, 404) < CLOSED_CHANCE;
        List<Long> path = new ArrayList<>();
        for (long k = exit; k != NONE; k = parent.get(k)) path.add(k);
        o.route = new long[path.size()];
        for (int i = 0; i < path.size(); i++) o.route[i] = path.get(path.size() - 1 - i);   // hollow ... exit
        o.exit = exit;
        return o;
    }

    /** Hollows (within reach) whose lakes overflow into this point. */
    private List<Long> lakeInflows(int cx, int cz) {
        long key = pack(cx >> 6, cz >> 6);
        Map<Long, List<Long>> m = this.inflows.get(key);
        if (m == null) {
            m = buildInflows(cx >> 6, cz >> 6);
            if (this.inflows.size() > 400) this.inflows.clear();
            this.inflows.put(key, m);
        }
        List<Long> l = m.get(pack(cx, cz));
        if (l == null) return List.of();
        List<Long> out = new ArrayList<>(l.size());
        for (long k : l) if (isLink(unpackX(k), unpackZ(k))) out.add(k);   // (upstream of here: no loop)
        return out;
    }

    private Map<Long, List<Long>> buildInflows(int tx, int tz) {
        Map<Long, List<Long>> m = new HashMap<>();
        for (long p : pits(tx, tz)) {
            long d = outlet(unpackX(p), unpackZ(p)).exit;
            if (d == NONE || unpackX(d) >> 6 != tx || unpackZ(d) >> 6 != tz) continue;
            m.computeIfAbsent(d, k -> new ArrayList<>(2)).add(p);
        }
        return m;
    }

    /** All hollows whose way out may touch this 64 x 64 cell square (they lie at most LINK_R cells outside). */
    private List<Long> pits(int tx, int tz) {
        long key = pack(tx, tz);
        List<Long> l = this.pitsNear.get(key);
        if (l != null) return l;
        l = new ArrayList<>();
        for (int x = tx * 64 - LINK_R; x < tx * 64 + 64 + LINK_R; x++) {
            for (int z = tz * 64 - LINK_R; z < tz * 64 + 64 + LINK_R; z++) {
                if (naturalDown(x, z) == NONE && !sea(x, z)) l.add(pack(x, z));
            }
        }
        if (this.pitsNear.size() > 400) this.pitsNear.clear();
        this.pitsNear.put(key, l);
        return l;
    }

    /** Is this cell the (accepted) source of its source square? */
    boolean isSource(int cx, int cz) {
        int per = SOURCE_SPACING / CELL;
        int gx = Math.floorDiv(cx, per), gz = Math.floorDiv(cz, per);
        double x = gx * (double) SOURCE_SPACING + hash01(gx, gz, 101) * SOURCE_SPACING;
        double z = gz * (double) SOURCE_SPACING + hash01(gx, gz, 202) * SOURCE_SPACING;
        if ((int) Math.floor(x / CELL) != cx || (int) Math.floor(z / CELL) != cz) return false;
        double h = cellY(cx, cz);
        if (h < LOW_SRC_MIN_Y || h > MOUNTAIN_SRC_MAX_Y) return false;
        double ring = 0.0;
        for (int d = 0; d < 8; d++) ring += cellY(cx + RX[d] * 4, cz + RZ[d] * 4);
        double hollow = ring / 8.0 - h;                                  // > 0: in a hollow between ridges
        double roll = hash01(gx, gz, 303);
        return h >= MOUNTAIN_SRC_MIN_Y
                ? hollow >= MOUNTAIN_SRC_HOLLOW && roll < MOUNTAIN_SRC_CHANCE
                : hollow >= LOW_SRC_HOLLOW && roll < LOW_SRC_CHANCE;
    }

    /** Points that drain into this one (neighbours, and lakes that overflow into it). */
    List<Long> children(int cx, int cz) {
        List<Long> out = new ArrayList<>(3);
        long self = pack(cx, cz);
        for (int dx = -JUMP_RADIUS; dx <= JUMP_RADIUS; dx++) {
            for (int dz = -JUMP_RADIUS; dz <= JUMP_RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (naturalDown(cx + dx, cz + dz) == self) out.add(pack(cx + dx, cz + dz));
            }
        }
        out.addAll(lakeInflows(cx, cz));
        return out;
    }

    /** River mouths: extra width on the last two grid steps before the sea (a small estuary). */
    private int mouth(int cx, int cz) {
        if (sea(cx, cz)) return MOUTH_FLARE;
        long n = down(cx, cz);
        if (n == NONE) return 0;
        if (sea(unpackX(n), unpackZ(n))) return MOUTH_FLARE - 1;
        long n2 = down(unpackX(n), unpackZ(n));
        return n2 != NONE && sea(unpackX(n2), unpackZ(n2)) ? (MOUTH_FLARE - 1) / 2 : 0;
    }

    // ------------------------------------------------------------------ river size (from everything upstream)

    /** River width (0 = no river) and blocks since the last widening, packed: width | dist << 8. */
    int stat(int cx, int cz) {
        int v = getStat(cx, cz);
        if (v != UNKNOWN) return v;
        ArrayDeque<Long> stack = new ArrayDeque<>();
        stack.push(pack(cx, cz));
        while (!stack.isEmpty()) {
            long k = stack.peek();
            int x = unpackX(k), z = unpackZ(k);
            if (getStat(x, z) != UNKNOWN) {
                stack.pop();
                continue;
            }
            List<Long> kids = children(x, z);
            boolean ready = true;
            for (long c : kids) {
                if (getStat(unpackX(c), unpackZ(c)) == UNKNOWN) {
                    stack.push(c);
                    ready = false;
                }
            }
            if (!ready) continue;
            setStat(x, z, combine(x, z, kids));
            stack.pop();
        }
        int out = getStat(cx, cz);
        return out == UNKNOWN ? stat(cx, cz) : out;                      // (cache was cleared meanwhile)
    }

    private int combine(int x, int z, List<Long> kids) {
        int rivers = 0, width = 0;
        double dist = 0.0;
        for (long c : kids) {
            int s = getStat(unpackX(c), unpackZ(c));
            if (s == UNKNOWN) s = stat(unpackX(c), unpackZ(c));
            int w = s & 0xff;
            if (w == 0) continue;
            double d = (s >>> 8) + Math.hypot(px(x, z) - px(unpackX(c), unpackZ(c)), pz(x, z) - pz(unpackX(c), unpackZ(c)));
            while (w < MAX_WIDTH && d >= gap(w)) {                         // grew on its way here
                d -= gap(w);
                w++;
            }
            rivers++;
            if (w > width || (w == width && d > dist)) {
                width = w;
                dist = d;
            }
        }
        if (rivers == 0) return isSource(x, z) && !sea(x, z) ? 1 : 0;   // every river starts 1 block wide
        if (rivers >= 2) return Math.min(MAX_WIDTH, width + 1);         // another river joins: 1 wider
        return width | ((int) Math.min(dist, 0xffff) << 8);
    }

    private int getStat(int cx, int cz) {
        int[] t = this.stats.get(pack(cx >> 6, cz >> 6));
        return t == null ? UNKNOWN : t[(cx & 63) * 64 + (cz & 63)];
    }

    private void setStat(int cx, int cz, int v) {
        int[] t = this.stats.computeIfAbsent(pack(cx >> 6, cz >> 6), k -> {
            int[] a = new int[64 * 64];
            Arrays.fill(a, UNKNOWN);
            return a;
        });
        t[(cx & 63) * 64 + (cz & 63)] = v;
        if (this.stats.size() > 6000) this.stats.clear();
    }

    // ------------------------------------------------------------------ width the river is drawn with

    private final ConcurrentHashMap<Long, int[]> shownStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, int[]> routeShownCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> aheadCache = new ConcurrentHashMap<>();

    /**
     * How wide the river may get coming down to this grid point, packed: width | dist << 8 | cap << 24 (dist: blocks
     * since it last got wider; cap: {@link #steepCap} of this point, 0 = not steep). The planned width ({@link #stat},
     * Rubenbuben4's rule - it also decides about the lakes), but on steep ground a mountain stream stays narrow
     * ({@value #STEEP_MAX_WIDTH} / {@value #VERY_STEEP_MAX_WIDTH} blocks) and below it the river grows back to the
     * planned width by 1 block per {@value #TORRENT_REGROW} blocks. The width it is drawn with ({@link #drawnWidth})
     * also narrows ahead of a torrent at that pace, and the pieces change their width gradually from one grid point
     * to the next - the width never jumps.
     * <p>(Until 2026-10-17 every piece was cut to the torrent width on its own and otherwise drawn as wide as planned:
     * a big river went 4 - 13 - 4 - 13 blocks wide piece by piece and swelled up on every flatter stretch, very often
     * at lakes, which lie on the flat ground in the mountains. And a lake's way out was drawn as wide as the river
     * into the lake, but the point after it counted the whole way through the lake as river length: 1 -> 4 blocks there.)
     */
    int shown(int cx, int cz) {
        int v = getShown(cx, cz);
        if (v != UNKNOWN) return v;
        ArrayDeque<Long> stack = new ArrayDeque<>();
        stack.push(pack(cx, cz));
        while (!stack.isEmpty()) {
            long k = stack.peek();
            int x = unpackX(k), z = unpackZ(k);
            if (getShown(x, z) != UNKNOWN) {
                stack.pop();
                continue;
            }
            List<Long> kids = children(x, z);
            boolean ready = true;
            for (long c : kids) {                                      // (for a lake: the hollow's own width)
                if ((stat(unpackX(c), unpackZ(c)) & 0xff) == 0) continue;
                if (getShown(unpackX(c), unpackZ(c)) == UNKNOWN) {
                    stack.push(c);
                    ready = false;
                }
            }
            if (!ready) continue;
            setShown(x, z, combineShown(x, z, kids));
            stack.pop();
        }
        int out = getShown(cx, cz);
        return out == UNKNOWN ? shown(cx, cz) : out;                     // (cache was cleared meanwhile)
    }

    private int combineShown(int x, int z, List<Long> kids) {
        int planned = stat(x, z) & 0xff;
        if (planned == 0) return 0;
        int rivers = 0, width = 0;
        double dist = 0.0;
        long main = NONE;
        for (long c : kids) {
            int kx = unpackX(c), kz = unpackZ(c);
            if ((stat(kx, kz) & 0xff) == 0) continue;
            long from = c;
            int s;
            if (isLink(kx, kz)) {                                       // a lake's outflow: along its way out
                long[] r = outlet(kx, kz).route;
                from = r[r.length - 2];
                s = routeShown(kx, kz)[r.length - 2];
            } else {
                s = shown(kx, kz);
            }
            int w = s & 0xff;
            double d = ((s >>> 8) & 0xffff) + Math.hypot(px(x, z) - px(unpackX(from), unpackZ(from)), pz(x, z) - pz(unpackX(from), unpackZ(from)));
            while (w < planned && d >= TORRENT_REGROW) {                // grows back to the planned width
                d -= TORRENT_REGROW;
                w++;
            }
            rivers++;
            if (w > width || (w == width && d > dist)) {
                width = w;
                dist = d;
                main = from;
            }
        }
        if (rivers == 0) {                                              // a source
            width = 1;
            dist = 0.0;
        } else if (rivers >= 2) {                                       // another river joins: 1 wider
            width = Math.min(planned, width + 1);
            dist = 0.0;
        }
        return capped(width, dist, steepCap(main, x, z, pieceNext(x, z)));
    }

    /** Packs width | dist | cap; a torrent is no wider than its cap (and starts to grow back from there). */
    private static int capped(int width, double dist, int cap) {
        if (width > cap) {
            width = cap;
            dist = 0.0;
        }
        return width | ((int) Math.min(dist, 0xffff) << 8) | ((cap == Integer.MAX_VALUE ? 0 : cap) << 24);
    }

    /**
     * Like shown, along a lake's way out: index k = the piece around route[k] (0: the hollow itself, as wide as the
     * rivers into the lake). It grows back to the hollow's planned width along the way and stays narrow where the
     * way runs steeply down.
     */
    int[] routeShown(int cx, int cz) {
        long key = pack(cx, cz);
        int[] v = this.routeShownCache.get(key);
        if (v != null) return v;
        long[] route = outlet(cx, cz).route;
        int planned = stat(cx, cz) & 0xff;
        int[] out = new int[Math.max(1, route.length - 1)];
        out[0] = shown(cx, cz);
        for (int k = 1; k < route.length - 1; k++) {
            int w = out[k - 1] & 0xff, x = unpackX(route[k]), z = unpackZ(route[k]);
            double d = ((out[k - 1] >>> 8) & 0xffff) + Math.hypot(px(x, z) - px(unpackX(route[k - 1]), unpackZ(route[k - 1])),
                    pz(x, z) - pz(unpackX(route[k - 1]), unpackZ(route[k - 1])));
            while (w < planned && d >= TORRENT_REGROW) {
                d -= TORRENT_REGROW;
                w++;
            }
            out[k] = capped(w, d, steepCap(route[k - 1], x, z, route[k + 1]));
        }
        if (this.routeShownCache.size() > 20000) this.routeShownCache.clear();
        this.routeShownCache.put(key, out);
        return out;
    }

    /**
     * How wide a river may be drawn on the ground of this grid point's pieces (from where the river comes in, over
     * the point, to where they end): {@value #STEEP_MAX_WIDTH} on slopes steeper than {@value #STEEP_SLOPE},
     * {@value #VERY_STEEP_MAX_WIDTH} steeper than {@value #VERY_STEEP_SLOPE} (a torrent, not a wide water stair).
     */
    private int steepCap(long from, int x, int z, long next) {
        long self = pack(x, z);
        double cx = px(x, z), cz = pz(x, z);
        double[] a = from == NONE ? new double[]{cx, cz} : meetPoint(from, self);
        double[] b = next == NONE ? new double[]{cx, cz} : meetPoint(self, next);
        double len = Math.hypot(cx - a[0], cz - a[1]) + Math.hypot(b[0] - cx, b[1] - cz);
        if (len < 1.0) return Integer.MAX_VALUE;
        double fall = ((Math.ceil(this.math.surfaceY(a[0], a[1])) - 1.0) - (Math.ceil(this.math.surfaceY(b[0], b[1])) - 1.0)) / len;
        return fall > VERY_STEEP_SLOPE ? VERY_STEEP_MAX_WIDTH : fall > STEEP_SLOPE ? STEEP_MAX_WIDTH : Integer.MAX_VALUE;
    }

    /**
     * The narrowest the river has to be ahead (downstream, through lakes' ways out): a torrent's cap plus 1 block per
     * {@value #TORRENT_REGROW} blocks of river between here and there - so a river narrows gradually before it runs
     * into steep ground, instead of in one step. Start at grid point (cx, cz) (k = -1) or on the way out of the
     * hollow (cx, cz) at route[k].
     */
    private int ahead(int cx, int cz, int k) {
        Long key = k < 0 ? pack(cx, cz) : null;
        if (key != null) {
            Integer v = this.aheadCache.get(key);
            if (v != null) return v;
        }
        double dist = 0.0, best = Double.MAX_VALUE, limit = (MAX_WIDTH + MOUTH_FLARE) * TORRENT_REGROW;
        double lx = Double.NaN, lz = Double.NaN;                         // centre of the last point
        int x = cx, z = cz;
        for (int guard = 0; guard < 400 && dist <= limit; guard++) {
            int s;
            double sx, sz;
            if (k >= 0) {                                                // on a lake's way out
                long[] route = outlet(x, z).route;
                if (k >= route.length - 1) {                             // its exit: on as a plain point
                    long e = route[route.length - 1];
                    x = unpackX(e);
                    z = unpackZ(e);
                    k = -1;
                    continue;
                }
                s = routeShown(x, z)[k];
                sx = px(unpackX(route[k]), unpackZ(route[k]));
                sz = pz(unpackX(route[k]), unpackZ(route[k]));
            } else {
                s = shown(x, z);
                sx = px(x, z);
                sz = pz(x, z);
            }
            if (!Double.isNaN(lx)) dist += Math.hypot(sx - lx, sz - lz);
            lx = sx;
            lz = sz;
            int cap = s >>> 24;
            if (cap > 0) best = Math.min(best, cap + dist / TORRENT_REGROW);
            if (k >= 0) {
                k++;
                continue;
            }
            if (sea(x, z)) break;
            if (isLink(x, z)) {                                          // on through its lake's way out
                k = 1;
                continue;
            }
            long n = naturalDown(x, z);
            if (n == NONE) break;
            x = unpackX(n);
            z = unpackZ(n);
        }
        int out = best == Double.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(best);
        if (key != null) {
            if (this.aheadCache.size() > 100000) this.aheadCache.clear();
            this.aheadCache.put(key, out);
        }
        return out;
    }

    /** Drawn width at the end of this grid point's pieces (see shown), with the extra width just before the sea. */
    int drawnWidth(int cx, int cz) {
        int w = Math.min(shown(cx, cz) & 0xff, ahead(cx, cz, -1));
        return Math.min(MAX_WIDTH + MOUTH_FLARE, w + mouth(cx, cz));
    }

    /** Drawn width at the end of the piece around route[k] of the way out of hollow (cx, cz) (k >= 1). */
    private int routeDrawnWidth(int cx, int cz, int k) {
        return Math.min(routeShown(cx, cz)[k] & 0xff, ahead(cx, cz, k));
    }

    private int getShown(int cx, int cz) {
        int[] t = this.shownStats.get(pack(cx >> 6, cz >> 6));
        return t == null ? UNKNOWN : t[(cx & 63) * 64 + (cz & 63)];
    }

    private void setShown(int cx, int cz, int v) {
        int[] t = this.shownStats.computeIfAbsent(pack(cx >> 6, cz >> 6), k -> {
            int[] a = new int[64 * 64];
            Arrays.fill(a, UNKNOWN);
            return a;
        });
        t[(cx & 63) * 64 + (cz & 63)] = v;
        if (this.shownStats.size() > 6000) this.shownStats.clear();
    }

    // ------------------------------------------------------------------ lakes

    /** A lake in a hollow: water up to {@code level}, outline on a 4-block grid. */
    static final class Lake {
        int level, minX, minZ, maxX, maxZ;
        final Set<Long> water = new HashSet<>(), shore = new HashSet<>();
        // distance of each water grid point from the shore (grid units: 1 = next to it), for the bowl (measure)
        int gx0, gz0, gw, gh;
        float[] fromShore;
        double reach;                                 // the farthest from the shore, in blocks from the water's edge

        /** Distance of grid point (gx, gz) from the shore; 0 on the shore and outside the water. */
        double fromShore(int gx, int gz) {
            int a = gx - this.gx0, b = gz - this.gz0;
            return this.fromShore == null || a < 0 || b < 0 || a >= this.gw || b >= this.gh ? 0.0 : this.fromShore[a * this.gh + b];
        }
    }

    Lake makeLake(int cx, int cz) {
        return makeLake(cx, cz, isLink(cx, cz) ? DEEP_LAKE_MAX_DEPTH : LAKE_MAX_DEPTH);
    }

    private static final Object NO_LAKE = new Object();
    private final ConcurrentHashMap<Long, Object> lakeCache = new ConcurrentHashMap<>();

    /**
     * The lake of a river's hollow (makeLake), worked out once: the tile draws it, the rivers in and out use its
     * level. It lies no higher than any river that reaches it outside its water (from the coarse grid a river may
     * seem to run into a lake that in the real terrain lies behind a rise, higher up - until 2026-10-17 such a river
     * then climbed up to the lake, and the lake ran out into its bed).
     * <p>Nor higher than a river flowing in is where it reaches the lake's shore (since 2026-10-18): one that came
     * through a rise lay up to 5 below the lake there, and the shore stood between them as a wall (computeChunk keeps
     * a shore standing over a lower river) - Rubenbuben4 saw it. Rivers that dive into the water at the lake's level
     * or 1 below are left as they are (no wall).
     */
    Lake cachedLake(int cx, int cz) {
        long key = pack(cx, cz);
        Object v = this.lakeCache.get(key);
        if (v == null) {
            Lake made = makeLake(cx, cz);
            if (made != null) {
                int cap = made.level;
                boolean atShore = false;
                for (long c : children(cx, cz)) {
                    int x = unpackX(c), z = unpackZ(c);
                    if ((stat(x, z) & 0xff) == 0) continue;
                    long from = c;                                        // (a lake's outflow comes along its route)
                    if (isLink(x, z)) {
                        long[] r = outlet(x, z).route;
                        if (r.length >= 2) from = r[r.length - 2];
                    }
                    double[] m = meetPoint(from, key);                    // where it reaches this lake's pieces
                    int end = endJunction(x, z);
                    if (!inLake(made, m[0], m[1])) {
                        if (end < cap) {
                            cap = end;
                            atShore = false;
                        }
                    } else if (end < made.level - 1) {                    // it runs in below the lake's level: is it
                        int at = arrival(made, x, z);                     // still lower where it reaches the shore?
                        if (at < made.level - 1 && at <= cap) {
                            cap = at;
                            atShore = true;
                        }
                    }
                }
                if (cap < made.level) {
                    int depth = isLink(cx, cz) ? DEEP_LAKE_MAX_DEPTH : LAKE_MAX_DEPTH;
                    Lake low = makeLake(cx, cz, depth, cap);
                    // lowered to a river at its shore: rather a shallow lake (1 block where the river ends) than none
                    if (low == null && atShore) low = makeLake(cx, cz, depth, cap, 1);
                    made = low;
                }
            }
            v = made != null ? made : NO_LAKE;
            if (this.lakeCache.size() > 512) this.lakeCache.clear();
            this.lakeCache.put(key, v);
        }
        return v == NO_LAKE ? null : (Lake) v;
    }

    /** The lake a river leaves through its way out (null: a shallow dip or no lake there - it just flows on). */
    private Lake wayOutLake(long hollow) {
        int x = unpackX(hollow), z = unpackZ(hollow);
        return outlet(x, z).depth >= MIN_LAKE_DEPTH ? cachedLake(x, z) : null;
    }

    /**
     * Lowest water of the river from grid point (cx, cz) at the lake's shore columns its last pieces would leave
     * standing (computeChunk: a shore column whose nearest river point is within the rim and more than 1 below the
     * lake), Integer.MAX_VALUE if there are none. The pieces are drawn once more on their own, just like the tile does.
     */
    private int arrival(Lake lake, int cx, int cz) {
        Tile probe = new Tile();
        lastPieces(probe, cx, cz, 2);
        Set<float[]> seen = Collections.newSetFromMap(new IdentityHashMap<>());   // (a point lies in several chunks'
        List<float[]> pts = new ArrayList<>();                                    //  lists; kept in a fixed order)
        for (List<float[]> list : probe.byChunk.values()) {
            for (float[] p : list) if (seen.add(p)) pts.add(p);
        }
        List<Lake> only = List.of(lake);
        ChunkData scratch = new ChunkData();
        Set<Long> tried = new HashSet<>();
        int low = Integer.MAX_VALUE;
        for (float[] p : pts) {
            if ((int) p[2] >= lake.level - 1) continue;                  // not lower: no wall next to it
            int r = (int) Math.ceil(p[3]) + border((int) p[4]);
            if (p[0] + r < lake.minX || p[0] - r > lake.maxX || p[1] + r < lake.minZ || p[1] - r > lake.maxZ) continue;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = (int) Math.floor(p[0]) + dx, z = (int) Math.floor(p[1]) + dz;
                    if (!tried.add(pack(x, z))) continue;
                    if (lakeColumn(only, x, z, scratch, 0) != ChunkData.SHORE) continue;
                    float[] bp = null;                                    // as computeChunk: the nearest river point
                    double best = Double.MAX_VALUE;
                    for (float[] q : pts) {
                        double e = Math.hypot(q[0] - (x + 0.5), q[1] - (z + 0.5)) - q[3];
                        if (e < best) {
                            best = e;
                            bp = q;
                        }
                    }
                    if (bp != null && best <= border((int) bp[4]) && bp[2] < lake.level - 1
                            && openNear(pts, only, scratch, x, z, lake.level - 1)) {
                        low = Math.min(low, (int) bp[2]);
                    }
                }
            }
        }
        return low;
    }

    /**
     * The last piece of the river from grid point (cx, cz) towards the next one and, levels - 1 times, the last
     * pieces of the rivers flowing into it (for a lake's outflow: the last levels pieces of its way out) - a grid
     * point may lie right at a lake's shore, so the river reaches the shore one piece earlier.
     */
    private void lastPieces(Tile probe, int cx, int cz, int levels) {
        long[] route = isLink(cx, cz) ? outlet(cx, cz).route : null;
        if (route != null && route.length > 2) {
            int start = junction(cx, cz);
            int[] lv = routeLevels(route, start);
            Lake above = wayOutLake(route[0]);
            for (int k = Math.max(1, route.length - 1 - levels); k <= route.length - 2; k++) {
                routePiece(probe, cx, cz, route, lv, above, start, k);
            }
            return;
        }
        cellPieces(probe, cx, cz);
        if (levels <= 1) return;
        for (long c : children(cx, cz)) {
            if ((stat(unpackX(c), unpackZ(c)) & 0xff) > 0) lastPieces(probe, unpackX(c), unpackZ(c), levels - 1);
        }
    }

    /**
     * True if one of these river points lower than below lies in the open (not in the lake, not on its shore) within
     * WALL_REACH blocks of (x, z): the river runs there in its own bed, so a shore between it and the lake is a wall.
     * (A river under the lake's water, with shore islets over it, is no wall.)
     */
    private boolean openNear(List<float[]> pts, List<Lake> only, ChunkData scratch, int x, int z, int below) {
        for (float[] q : pts) {
            if (q[2] >= below || Math.abs(q[0] - (x + 0.5)) > WALL_REACH || Math.abs(q[1] - (z + 0.5)) > WALL_REACH) continue;
            if (lakeColumn(only, (int) Math.floor(q[0]), (int) Math.floor(q[1]), scratch, 0) == ChunkData.NONE) return true;
        }
        return false;
    }

    static final int WALL_REACH = 6;

    /** True if (x, z) lies in the lake's water (on its 4-block grid). */
    static boolean inLake(Lake lake, double x, double z) {
        return lake.water.contains(pack((int) Math.round(x / LAKE_GRID), (int) Math.round(z / LAKE_GRID)));
    }

    /** Lake in the hollow at this cell, filled up to just below its saddle, at most maxDepth deep. */
    Lake makeLake(int cx, int cz, int maxDepth) {
        return makeLake(cx, cz, maxDepth, Integer.MAX_VALUE);
    }

    /** ... and its water no higher than maxLevel (null if that leaves no lake). */
    Lake makeLake(int cx, int cz, int maxDepth, int maxLevel) {
        return makeLake(cx, cz, maxDepth, maxLevel, 2);
    }

    /** ... with at least minDepth blocks of water where the river ends. */
    Lake makeLake(int cx, int cz, int maxDepth, int maxLevel, int minDepth) {
        double h0 = cellY(cx, cz);
        // lowest saddle out of the hollow (see outlet: the highest ground on the water's lowest way out)
        Outlet o = outlet(cx, cz);
        double saddle = o.exit != NONE ? h0 + o.depth : h0 + maxDepth + 1;
        // the lake starts exactly where the river ends (so they always touch) and covers that spot
        // with at least 2 blocks of water
        Map<Long, Double> ground = new HashMap<>();
        int bx = (int) Math.round(px(cx, cz) / LAKE_GRID), bz = (int) Math.round(pz(cx, cz) / LAKE_GRID);
        double g0 = groundAt(bx, bz, ground);
        int minLevel = (int) g0 + minDepth;
        int level = Math.max(minLevel, (int) Math.floor(Math.min(saddle - 1.0, h0 + maxDepth)) - 1);   // top water block
        level = Math.min(level, maxLevel);
        for (; level >= minLevel; level--) {
            int lv = level;
            Set<Long> water = flood(bx, bz, level, ground, Integer.MAX_VALUE);
            if (water == null && level == minLevel) {                     // flat land: no basin holds the water -
                lv = Math.min(maxLevel, (int) g0 - 1);                    // a round pond dug into it instead, 1 below
                water = pond(bx, bz, lv, ground);                         // the ground where the river ends (none
            }                                                             // on a slope: it would run away)
            if (water == null) continue;                                  // too big: lower the water
            Lake lake = new Lake();
            lake.level = lv;
            fillNotches(water, lv, ground);
            lake.water.addAll(water);
            for (long g : water) {
                for (int d = 0; d < 8; d++) {
                    long n = pack(unpackX(g) + RX[d], unpackZ(g) + RZ[d]);
                    if (!water.contains(n)) lake.shore.add(n);
                }
            }
            lake.minX = lake.minZ = Integer.MAX_VALUE;
            lake.maxX = lake.maxZ = Integer.MIN_VALUE;
            for (long g : lake.shore) {
                lake.minX = Math.min(lake.minX, unpackX(g) * LAKE_GRID - LAKE_GRID);
                lake.maxX = Math.max(lake.maxX, unpackX(g) * LAKE_GRID + LAKE_GRID);
                lake.minZ = Math.min(lake.minZ, unpackZ(g) * LAKE_GRID - LAKE_GRID);
                lake.maxZ = Math.max(lake.maxZ, unpackZ(g) * LAKE_GRID + LAKE_GRID);
            }
            measure(lake);
            return lake;
        }
        return null;
    }

    /**
     * How far each water grid point lies from the shore (grid units, 1 right next to it, diagonal steps count
     * sqrt 2 - two passes over the lake's box), and the farthest of them in blocks from the water's edge. The bowl of
     * the lake (bowlDepth) is cut from it.
     */
    static void measure(Lake lake) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long g : lake.water) {
            x0 = Math.min(x0, unpackX(g));
            x1 = Math.max(x1, unpackX(g));
            z0 = Math.min(z0, unpackZ(g));
            z1 = Math.max(z1, unpackZ(g));
        }
        if (x0 > x1) return;
        int w = x1 - x0 + 3, h = z1 - z0 + 3;                                    // one dry point all round
        float[] f = new float[w * h];
        for (long g : lake.water) f[(unpackX(g) - x0 + 1) * h + unpackZ(g) - z0 + 1] = Float.MAX_VALUE;
        float diag = (float) Math.sqrt(2.0);
        for (int a = 1; a < w - 1; a++) {                                        // forward ...
            for (int b = 1; b < h - 1; b++) {
                int i = a * h + b;
                if (f[i] == 0.0F) continue;
                f[i] = Math.min(f[i], Math.min(Math.min(f[i - h] + 1.0F, f[i - 1] + 1.0F), Math.min(f[i - h - 1] + diag, f[i - h + 1] + diag)));
            }
        }
        float most = 0.0F;
        for (int a = w - 2; a >= 1; a--) {                                       // ... and back
            for (int b = h - 2; b >= 1; b--) {
                int i = a * h + b;
                if (f[i] == 0.0F) continue;
                f[i] = Math.min(f[i], Math.min(Math.min(f[i + h] + 1.0F, f[i + 1] + 1.0F), Math.min(f[i + h + 1] + diag, f[i + h - 1] + diag)));
                most = Math.max(most, f[i]);
            }
        }
        lake.gx0 = x0 - 1;
        lake.gz0 = z0 - 1;
        lake.gw = w;
        lake.gh = h;
        lake.fromShore = f;
        lake.reach = most * LAKE_GRID - LAKE_GRID / 2.0;
    }

    /**
     * Depth of a lake's bowl (since 2026-10-22): 1 block at the water's edge (so one can walk out), then deeper
     * towards the middle, the deepest point 1.5 + {@value #BOWL_PER_BLOCK} x its distance from the edge
     * (2 - {@value #BOWL_MAX} blocks). Before, a lake on flat land - a pond dug into it, or a shallow hollow - was 1
     * block deep everywhere (Rubenbuben4: "no deep parts if they are generated on flat terrain"). Where the hollow is
     * deeper than the bowl its ground stays.
     *
     * @param edge  blocks from the water's edge (0 at the edge)
     * @param reach the farthest any of the lake's water lies from its edge
     */
    public static int bowlDepth(double edge, double reach) {
        if (edge <= 0.0) return 1;
        double far = Math.max(2.0, reach);
        double deepest = Math.round(Math.max(2.0, Math.min(BOWL_MAX, 1.5 + far * BOWL_PER_BLOCK)));
        double t = Math.min(1.0, edge / far);
        return Math.max(1, (int) Math.round(deepest * (1.0 - (1.0 - t) * (1.0 - t))));
    }

    /**
     * Smoother lakes: a grid point that is dry but mostly surrounded by water (a one-point islet or a notch in
     * the shore) becomes water too, if its ground is at most {@value #ISLET_MAX} above the water (it is dug away).
     */
    private void fillNotches(Set<Long> water, int level, Map<Long, Double> ground) {
        for (int round = 0; round < 2; round++) {
            List<Long> add = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (long g : water) {
                for (int d = 0; d < 4; d++) {
                    int nx = unpackX(g) + RX[d], nz = unpackZ(g) + RZ[d];
                    long n = pack(nx, nz);
                    if (water.contains(n) || !seen.add(n)) continue;
                    int wet = 0;
                    for (int e = 0; e < 4; e++) if (water.contains(pack(nx + RX[e], nz + RZ[e]))) wet++;
                    if (wet >= 3 && groundAt(nx, nz, ground) <= level + ISLET_MAX) add.add(n);
                }
            }
            if (add.isEmpty()) return;
            water.addAll(add);
        }
    }

    /**
     * A pond on flat land (round (sx, sz), at most POND_RADIUS grid points): the grid points connected to the
     * river's end where the ground can be dug out to the water (lake() digs up to 2 blocks). Null if the water
     * would run away - ground at or below it just outside the circle (a slope, no flat land). (Until 2026-10-17 a
     * pond lay 2 blocks above the ground at the river's end; where that spot was a small bump or the land sloped
     * away, it stood up to 5 blocks above the land round it, held by a gravel dam.)
     */
    private Set<Long> pond(int sx, int sz, int level, Map<Long, Double> ground) {
        Set<Long> water = new HashSet<>();
        ArrayDeque<Long> todo = new ArrayDeque<>();
        todo.add(pack(sx, sz));
        water.add(pack(sx, sz));
        while (!todo.isEmpty()) {
            long g = todo.poll();
            for (int d = 0; d < 4; d++) {
                int nx = unpackX(g) + RX[d], nz = unpackZ(g) + RZ[d];
                long n = pack(nx, nz);
                if (water.contains(n)) continue;
                double h = groundAt(nx, nz, ground);
                if (h > level + ISLET_MAX) continue;                      // high enough: the pond's shore
                if ((nx - sx) * (nx - sx) + (nz - sz) * (nz - sz) > POND_RADIUS * POND_RADIUS) {
                    if (h <= level) return null;                          // lower land beyond: it would run away
                    continue;
                }
                water.add(n);
                todo.add(n);
            }
        }
        return water;
    }

    /** Fine-grid points connected to (sx, sz) whose ground lies below the water; null if too many. */
    private Set<Long> flood(int sx, int sz, int level, Map<Long, Double> ground, int radius) {
        Set<Long> water = new HashSet<>();
        ArrayDeque<Long> todo = new ArrayDeque<>();
        todo.add(pack(sx, sz));
        water.add(pack(sx, sz));
        while (!todo.isEmpty()) {
            long g = todo.poll();
            for (int d = 0; d < 4; d++) {
                int nx = unpackX(g) + RX[d], nz = unpackZ(g) + RZ[d];
                long n = pack(nx, nz);
                if (water.contains(n)) continue;
                if (groundAt(nx, nz, ground) >= level + 1) continue;
                if ((long) (nx - sx) * (nx - sx) + (long) (nz - sz) * (nz - sz) > (long) radius * radius) continue;
                water.add(n);
                todo.add(n);
                if (water.size() > LAKE_MAX_POINTS) return null;
            }
        }
        return water;
    }

    private double groundAt(int gx, int gz, Map<Long, Double> cache) {
        return cache.computeIfAbsent(pack(gx, gz), k -> Math.ceil(this.math.surfaceY(gx * LAKE_GRID, gz * LAKE_GRID)) - 1.0);
    }

    // ------------------------------------------------------------------ water levels along the rivers

    /**
     * Where the river of grid point {@code up} hands over to the next one ({@code down}): not just half way between
     * them, but the lowest ground near there - so the pieces meet in the valley, not on a slope. Both pieces work it
     * out the same way (only from the two points), so they always meet exactly.
     */
    double[] meetPoint(long up, long down) {
        double mx = (px(unpackX(up), unpackZ(up)) + px(unpackX(down), unpackZ(down))) * 0.5;
        double mz = (pz(unpackX(up), unpackZ(up)) + pz(unpackX(down), unpackZ(down))) * 0.5;
        double best = Double.MAX_VALUE, bx = mx, bz = mz;
        for (int dx = -MEET_RADIUS; dx <= MEET_RADIUS; dx += 2) {
            for (int dz = -MEET_RADIUS; dz <= MEET_RADIUS; dz += 2) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > MEET_RADIUS) continue;
                double v = this.math.smoothSurfaceY(mx + dx, mz + dz) + 0.08 * d;
                if (v < best) {
                    best = v;
                    bx = mx + dx;
                    bz = mz + dz;
                }
            }
        }
        return new double[]{bx, bz};
    }

    /**
     * The river's way from a to b (past the grid point c): searched on the real terrain like water would run -
     * the cheapest way where every block uphill costs {@value #RISE_COST} blocks of detour. So it follows valleys
     * and goes round rises instead of cutting through them; only where there is no way round it cuts (and the
     * water then keeps its level, see piece). Returns the way as points (a first, b last), smoothed.
     */
    private double[][] findWay(double ax, double az, double cx, double cz, double bx, double bz) {
        double minX = Math.min(ax, Math.min(cx, bx)) - PATH_MARGIN, minZ = Math.min(az, Math.min(cz, bz)) - PATH_MARGIN;
        double maxX = Math.max(ax, Math.max(cx, bx)) + PATH_MARGIN, maxZ = Math.max(az, Math.max(cz, bz)) + PATH_MARGIN;
        int nx = (int) Math.ceil((maxX - minX) / PATH_GRID) + 1, nz = (int) Math.ceil((maxZ - minZ) / PATH_GRID) + 1;
        int n = nx * nz;
        double[] h = new double[n];
        Arrays.fill(h, Double.NaN);
        double[] cost = new double[n];
        Arrays.fill(cost, Double.MAX_VALUE);
        int[] from = new int[n];
        int sx = (int) Math.round((ax - minX) / PATH_GRID), sz = (int) Math.round((az - minZ) / PATH_GRID);
        int gx = (int) Math.round((bx - minX) / PATH_GRID), gz = (int) Math.round((bz - minZ) / PATH_GRID);
        int start = sx * nz + sz, goal = gx * nz + gz;
        PriorityQueue<double[]> q = new PriorityQueue<>((p1, p2) -> Double.compare(p1[0], p2[0]));
        cost[start] = 0.0;
        from[start] = -1;
        q.add(new double[]{0.0, start});
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int cur = (int) e[1];
            if (e[0] > cost[cur]) continue;
            if (cur == goal) break;
            int ci = cur / nz, cj = cur % nz;
            double ch = wayHeight(h, cur, minX + ci * PATH_GRID, minZ + cj * PATH_GRID);
            for (int d = 0; d < 8; d++) {
                int ni = ci + RX[d], nj = cj + RZ[d];
                if (ni < 0 || nj < 0 || ni >= nx || nj >= nz) continue;
                int nb = ni * nz + nj;
                double nh = wayHeight(h, nb, minX + ni * PATH_GRID, minZ + nj * PATH_GRID);
                double c = e[0] + (d < 4 ? 1.0 : 1.41421356) * PATH_GRID + RISE_COST * Math.max(0.0, nh - ch);
                if (c < cost[nb]) {
                    cost[nb] = c;
                    from[nb] = cur;
                    q.add(new double[]{c, nb});
                }
            }
        }
        List<double[]> way = new ArrayList<>();
        for (int k = goal; k >= 0; k = from[k]) way.add(new double[]{minX + (k / nz) * PATH_GRID, minZ + (k % nz) * PATH_GRID});
        java.util.Collections.reverse(way);
        way.set(0, new double[]{ax, az});                                       // exactly from a ...
        if (way.size() == 1) way.add(new double[]{bx, bz});
        else way.set(way.size() - 1, new double[]{bx, bz});                     // ... to b
        double[][] pts = way.toArray(new double[0][]);
        for (int pass = 0; pass < 2; pass++) {                                   // round off the grid corners
            double[][] sm = new double[pts.length][];
            sm[0] = pts[0];
            sm[pts.length - 1] = pts[pts.length - 1];
            for (int k = 1; k < pts.length - 1; k++) {
                sm[k] = new double[]{(pts[k - 1][0] + 2 * pts[k][0] + pts[k + 1][0]) * 0.25,
                        (pts[k - 1][1] + 2 * pts[k][1] + pts[k + 1][1]) * 0.25};
            }
            pts = sm;
        }
        return pts;
    }

    private double wayHeight(double[] h, int k, double x, double z) {
        if (Double.isNaN(h[k])) h[k] = this.math.surfaceY(x, z);
        return h[k];
    }

    /** Water 1 block below the top ground block at a spot (what a river there would have on its own). */
    private int groundWater(double x, double z) {
        return (int) Math.round(Math.ceil(this.math.surfaceY(x, z)) - 1.0) - 1;
    }

    /** Lowest such water on a short line across (x, z) - the river slides to the valley floor. */
    private int floorWater(double x, double z, double dirX, double dirZ) {
        double l = Math.hypot(dirX, dirZ);
        double nx = l < 1e-6 ? 1.0 : -dirZ / l, nz = l < 1e-6 ? 0.0 : dirX / l;
        int best = Integer.MAX_VALUE;
        for (int k = -JUNCTION_SIDE; k <= JUNCTION_SIDE; k += 2) best = Math.min(best, groundWater(x + nx * k, z + nz * k));
        return best;
    }

    /** Ground water where the pieces of grid point (cx, cz) end (the meeting point with the next one). */
    private int cellGround(int cx, int cz, long next) {
        double x = px(cx, cz), z = pz(cx, cz);
        if (next == NONE) return floorWater(x, z, 1.0, 0.0);
        double[] e = meetPoint(pack(cx, cz), next);                            // where its pieces end
        return floorWater(e[0], e[1], e[0] - x, e[1] - z);
    }

    private int routeGround(long[] route, int k) {
        return cellGround(unpackX(route[k]), unpackZ(route[k]), route[k + 1]);
    }

    /** Where the pieces of this grid point end: the next point downstream (for a lake: the first one of its way out). */
    private long pieceNext(int cx, int cz) {
        long next = down(cx, cz);
        if (next != NONE && isLink(cx, cz)) next = outlet(cx, cz).route[1];
        return next;
    }

    /**
     * Water level where the river of this grid point ends its pieces (half way to the next point). It is never
     * higher than the end of any river flowing in (so a river never runs uphill, also not over the grid points
     * where the pieces meet) and never higher than the ground on its way here. A lake's outflow starts at the
     * lake's water level. Worked out from upstream down, the same for every chunk that asks.
     */
    int junction(int cx, int cz) {
        Integer v = this.junctions.get(pack(cx, cz));
        if (v != null) return v;
        ArrayDeque<Long> stack = new ArrayDeque<>();
        stack.push(pack(cx, cz));
        while (!stack.isEmpty()) {
            long k = stack.peek();
            if (this.junctions.containsKey(k)) {
                stack.pop();
                continue;
            }
            int x = unpackX(k), z = unpackZ(k);
            List<Long> kids = new ArrayList<>();
            for (long c : children(x, z)) if ((stat(unpackX(c), unpackZ(c)) & 0xff) > 0) kids.add(c);
            boolean ready = true;
            for (long c : kids) {
                if (!this.junctions.containsKey(c)) {
                    stack.push(c);
                    ready = false;
                }
            }
            if (!ready) continue;
            int j;
            Lake lake = !sea(x, z) && isLink(x, z) ? wayOutLake(k) : null;
            if (sea(x, z)) {
                j = (int) SEA_Y - 1;
            } else if (lake != null) {
                j = lake.level;                                           // the outflow leaves at the lake's level
            } else {
                j = cellGround(x, z, pieceNext(x, z));
                for (long c : kids) {
                    Integer cj = this.junctions.get(c);
                    if (cj == null) cj = junction(unpackX(c), unpackZ(c));  // (cache was cleared meanwhile)
                    j = Math.min(j, routeEnd(unpackX(c), unpackZ(c), cj));
                }
            }
            if (this.junctions.size() > 200000) this.junctions.clear();
            this.junctions.put(k, Math.max((int) SEA_Y - 1, j));
            stack.pop();
        }
        Integer out = this.junctions.get(pack(cx, cz));
        return out != null ? out : junction(cx, cz);
    }

    /** Water level where the river from this grid point reaches the next one (through its lake's way out, if it has one). */
    int endJunction(int cx, int cz) {
        return routeEnd(cx, cz, junction(cx, cz));
    }

    private int routeEnd(int cx, int cz, int j) {
        if (!isLink(cx, cz)) return j;
        long[] route = outlet(cx, cz).route;
        int[] levels = routeLevels(route, j);
        return route.length > 2 ? levels[route.length - 2] : j;
    }

    /**
     * Water level at the end of each piece of a lake's way out (index k = piece around route[k]). It leaves at the
     * lake's level and keeps it while the way is still in the lake - the ground there is the lake bed under the
     * water and must not pull the river down. Once a piece ends outside the lake (or on ground at the lake level,
     * the saddle) the river follows the ground down (never up again).
     * <p>(Until 2026-10-17 the level was kept until a piece ended on ground at the lake level. Where the real saddle
     * lay between those points that never happened, and the way out ran on at the lake's level far down the slope -
     * up to 13 blocks over the ground, held by walls.)
     */
    private int[] routeLevels(long[] route, int start) {
        int[] out = new int[route.length];
        Lake lake = route.length > 0 ? wayOutLake(route[0]) : null;
        int j = start;
        boolean overSaddle = lake == null;                               // (a shallow dip: no lake to keep)
        for (int k = 1; k < route.length - 1; k++) {
            int g = routeGround(route, k);
            if (!overSaddle) {
                double[] e = meetPoint(route[k], route[k + 1]);          // where this piece ends
                overSaddle = g >= start - 1 || k >= route.length - 2 || !inLake(lake, e[0], e[1]);
            }
            if (overSaddle) j = Math.min(j, g);
            out[k] = j;
        }
        return out;
    }

    // ------------------------------------------------------------------ tiles

    private static final class Tile {
        final Map<Long, List<float[]>> byChunk = new HashMap<>();
        final List<Lake> lakes = new ArrayList<>();
    }

    private Tile tile(int tx, int tz) {
        Tile t = this.tiles.get(pack(tx, tz));
        if (t != null) return t;
        t = this.tiles.computeIfAbsent(pack(tx, tz), k -> buildTile(tx, tz));
        if (this.tiles.size() > 256) {
            this.tiles.clear();
            this.tiles.put(pack(tx, tz), t);
        }
        return t;
    }

    List<float[]> pointsNear(int chunkX, int chunkZ) {
        List<float[]> out = new ArrayList<>();
        int span = CELL * TILE_CELLS;
        long key = pack(chunkX, chunkZ);
        int m = REACH + 2 * CELL;
        int minX = chunkX * 16 - m, maxX = chunkX * 16 + 15 + m;
        int minZ = chunkZ * 16 - m, maxZ = chunkZ * 16 + 15 + m;
        for (int tx = Math.floorDiv(minX, span); tx <= Math.floorDiv(maxX, span); tx++) {
            for (int tz = Math.floorDiv(minZ, span); tz <= Math.floorDiv(maxZ, span); tz++) {
                List<float[]> pts = tile(tx, tz).byChunk.get(key);
                if (pts != null) out.addAll(pts);
            }
        }
        return out;
    }

    List<Lake> lakesNear(int chunkX, int chunkZ) {
        List<Lake> out = new ArrayList<>();
        int span = CELL * TILE_CELLS, m = 320;                           // lakes reach at most ~260 blocks
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        for (int tx = Math.floorDiv(x0 - m, span); tx <= Math.floorDiv(x0 + 15 + m, span); tx++) {
            for (int tz = Math.floorDiv(z0 - m, span); tz <= Math.floorDiv(z0 + 15 + m, span); tz++) {
                for (Lake l : tile(tx, tz).lakes) {
                    if (l.maxX >= x0 && l.minX <= x0 + 15 && l.maxZ >= z0 && l.minZ <= z0 + 15) out.add(l);
                }
            }
        }
        return out;
    }

    private Tile buildTile(int tx, int tz) {
        Tile tile = new Tile();
        int c0x = tx * TILE_CELLS, c0z = tz * TILE_CELLS;
        for (int i = 0; i < TILE_CELLS; i++) {
            for (int j = 0; j < TILE_CELLS; j++) {
                int cx = c0x + i, cz = c0z + j;
                int w = stat(cx, cz) & 0xff;
                if (w == 0) continue;
                long next = down(cx, cz);
                boolean link = next != NONE && isLink(cx, cz);
                if ((next == NONE && !sea(cx, cz))                        // ends in a hollow: a lake ...
                        || (link && outlet(cx, cz).depth >= MIN_LAKE_DEPTH)) { // ... or a lake that overflows
                    Lake lake = cachedLake(cx, cz);
                    if (lake != null) tile.lakes.add(lake);
                }
                cellPieces(tile, cx, cz);
            }
        }
        // lakes without a river: tarns in mountain hollows (often) and a few ponds in the lowland
        for (long p : pits(tx, tz)) {
            int x = unpackX(p), z = unpackZ(p);
            if (Math.floorDiv(x, TILE_CELLS) != tx || Math.floorDiv(z, TILE_CELLS) != tz) continue;
            if ((stat(x, z) & 0xff) != 0) continue;                       // (rivers' lakes are made above)
            double y = cellY(x, z);
            if (y < SEA_Y + 3) continue;
            if (hash01(x, z, 606) >= (y >= TARN_MIN_Y ? TARN_CHANCE : POND_CHANCE)) continue;
            if (outlet(x, z).depth < LONE_LAKE_MIN_DEPTH) continue;
            Lake lake = makeLake(x, z, LONE_LAKE_MAX_DEPTH);
            if (lake != null) tile.lakes.add(lake);
        }
        // lake outflows (hollow -> saddle -> exit): each tile draws the part of the route that lies in it
        for (long p : pits(tx, tz)) {
            int x = unpackX(p), z = unpackZ(p);
            if (!isLink(x, z)) continue;
            if ((stat(x, z) & 0xff) == 0) continue;
            routePieces(tile, tx, tz, x, z, junction(x, z));
        }
        return tile;
    }

    /**
     * The pieces of grid point (cx, cz) on to the next one: one from where each river flowing in meets it,
     * or from its source. (Its own method since 2026-10-18: cachedLake draws them once more to see where a river
     * reaches a lake.)
     */
    private void cellPieces(Tile tile, int cx, int cz) {
        long next = down(cx, cz);
        boolean link = next != NONE && isLink(cx, cz);
        double cxp = px(cx, cz), czp = pz(cx, cz);
        if (link) {                                               // the outflow: hollow -> saddle -> exit
            next = outlet(cx, cz).route[1];                       // (the outflow is drawn below)                                      // the rivers into the hollow aim there
        }
        double bx = cxp, bz = czp;
        if (next != NONE) {
            double[] m = meetPoint(pack(cx, cz), next);
            bx = m[0];
            bz = m[1];
        }
        int wm = drawnWidth(cx, cz);                              // (wider just before the sea)
        boolean any = false;
        // below the confluence no river is higher than the lowest one flowing in (their ways run side by
        // side there - at different levels the water would stand in steps across the river)
        List<Long> kids = children(cx, cz);
        int lowestIn = Integer.MAX_VALUE;
        for (long c : kids) {
            if ((stat(unpackX(c), unpackZ(c)) & 0xff) > 0) lowestIn = Math.min(lowestIn, endJunction(unpackX(c), unpackZ(c)));
        }
        for (long c : kids) {
            if ((stat(unpackX(c), unpackZ(c)) & 0xff) == 0) continue;
            any = true;
            long from = c;                                        // a lake outflow comes along its route
            int cw;                                               // (its width where it arrives)
            if (isLink(unpackX(c), unpackZ(c))) {
                long[] r = outlet(unpackX(c), unpackZ(c)).route;
                from = r[r.length - 2];
                cw = r.length > 2 ? routeDrawnWidth(unpackX(c), unpackZ(c), r.length - 2) : drawnWidth(unpackX(c), unpackZ(c));
            } else {
                cw = drawnWidth(unpackX(c), unpackZ(c));
            }
            double[] m = meetPoint(from, pack(cx, cz));
            double ax = m[0], az = m[1];
            piece(tile, ax, az, cxp, czp, bx, bz, cw, wm, -side(unpackX(from), unpackZ(from)), side(cx, cz),
                    endJunction(unpackX(c), unpackZ(c)), junction(cx, cz), lowestIn, null);
        }
        if (!any) piece(tile, cxp, czp, cxp, czp, bx, bz, wm, wm, 0.0, side(cx, cz),   // a source
                Integer.MAX_VALUE, junction(cx, cz), Integer.MAX_VALUE, null);
    }

    /**
     * Meander: on flat land each half piece bows out to one side (an arc between its ends, so the pieces
     * still meet); the halves swing left and right like a lowland stream. Wide rivers bend less (their
     * loops would need to be much longer than one grid step). Where the
     * land across the river is not flat (a valley, a slope) there is no bend - the valley decides the way.
     * Returns the bow height in blocks (0 = straight); the caller picks the side.
     */
    private double meander(double x0, double z0, double x1, double z1, int width) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        if (len < 8.0) return 0.0;
        double amp = Math.min(MEANDER_AMP, MEANDER_PER_LENGTH * len)
                * Math.min(1.0, MEANDER_FULL_WIDTH / (double) Math.max(1, width));
        double nx = -(z1 - z0) / len, nz = (x1 - x0) / len, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int q = 1; q <= 3; q++) {                            // land across the river at 1/4, 1/2, 3/4
            double mx = x0 + (x1 - x0) * q / 4.0, mz = z0 + (z1 - z0) * q / 4.0;
            for (int k = -1; k <= 1; k++) {
                double y = this.math.smoothSurfaceY(mx + nx * amp * k * 1.5, mz + nz * amp * k * 1.5);
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        }
        double flat = 1.0 - (hi - lo - MEANDER_FLAT) / MEANDER_FLAT;      // 1 = flat, 0 = twice as rough
        return flat <= 0.0 ? 0.0 : amp * Math.min(1.0, flat);
    }

    /**
     * The river out of a lake (or through a shallow dip) in hollow (hx, hz): curved pieces along the route up to the
     * exit, as wide as routeDrawnWidth says (it starts as wide as the hollow's pieces end).
     */
    private void routePieces(Tile tile, int tx, int tz, int hx, int hz, int start) {
        long[] route = outlet(hx, hz).route;
        int[] levels = routeLevels(route, start);
        Lake lake = wayOutLake(route[0]);                                 // (its bed must not pull the water down)
        for (int k = 1; k < route.length - 1; k++) {
            int x = unpackX(route[k]), z = unpackZ(route[k]);
            if (Math.floorDiv(x, TILE_CELLS) != tx || Math.floorDiv(z, TILE_CELLS) != tz) continue;   // another tile's part
            routePiece(tile, hx, hz, route, levels, lake, start, k);
        }
    }

    /**
     * Piece k of that way out: from the lake's level (start) or the end of the piece before, as wide as the
     * hollow's pieces end or the piece before. (Its own method since 2026-10-18, see cachedLake.)
     */
    private void routePiece(Tile tile, int hx, int hz, long[] route, int[] levels, Lake lake, int start, int k) {
        int x = unpackX(route[k]), z = unpackZ(route[k]);
        int pIn = k == 1 ? start : levels[k - 1], jOut = levels[k];
        int wa = k == 1 ? drawnWidth(hx, hz) : routeDrawnWidth(hx, hz, k - 1), wb = routeDrawnWidth(hx, hz, k);
        double cxp = px(x, z), czp = pz(x, z);
        double[] ma = meetPoint(route[k - 1], route[k]), mb = meetPoint(route[k], route[k + 1]);
        piece(tile, ma[0], ma[1], cxp, czp, mb[0], mb[1], wa, wb,
                -side(unpackX(route[k - 1]), unpackZ(route[k - 1])), side(x, z), pIn, jOut, Integer.MAX_VALUE, lake);
    }

    /** Which way the river bends just below this grid point (meanders): -1 or +1. */
    private double side(int cx, int cz) {
        return hash01(cx, cz, 505) < 0.5 ? -1.0 : 1.0;
    }

    /**
     * Samples one curved piece (quadratic curve a -> b around the cell point c) into the chunk buckets.
     * The water follows the real terrain under the curve: 1 block below the ground (the rim is at ground
     * height). It is only capped by the higher of the two ends, so a small bump is cut through.
     * Point = {x, z, waterY, halfWidth, width, gorge, angle downstream, slope, curvature}.
     * jCap: from the cell point on (the second half) the water is no higher than this (the lowest river flowing in
     * at a confluence); hold: a lake's way out - over that lake's bed the ground does not pull the water down.
     * wa, wb: width at a and at b (the drawn widths there, see shown) - in between it changes gradually.
     */
    private void piece(Tile tile, double ax, double az, double cx, double cz, double bx, double bz, int wa, int wb,
                       double sideA, double sideB, int jIn, int jOut, int jCap, Lake hold) {
        if (Math.hypot(cx - ax, cz - az) + Math.hypot(bx - cx, bz - cz) < 0.5) return;
        // the way down through the real terrain (findWay), then sampled every half block
        double[][] way = findWay(ax, az, cx, cz, bx, bz);
        double[] cum = new double[way.length];
        for (int k = 1; k < way.length; k++) cum[k] = cum[k - 1] + Math.hypot(way[k][0] - way[k - 1][0], way[k][1] - way[k - 1][1]);
        double len = cum[way.length - 1];
        if (len < 0.5) return;
        int steps = Math.max(2, (int) Math.ceil(len / 0.5));      // every half block: no gaps in thin streams
        double[] xs = new double[steps + 1], zs = new double[steps + 1], top = new double[steps + 1];
        int seg = 1;
        for (int s = 0; s <= steps; s++) {
            double d = len * s / steps;
            while (seg < way.length - 1 && cum[seg] < d) seg++;
            double sl = cum[seg] - cum[seg - 1], f = sl <= 1e-9 ? 1.0 : (d - cum[seg - 1]) / sl;
            xs[s] = way[seg - 1][0] + (way[seg][0] - way[seg - 1][0]) * f;
            zs[s] = way[seg - 1][1] + (way[seg][1] - way[seg - 1][1]) * f;
        }
        double[] off = new double[steps + 1];                       // (no extra sideways shift: the way is the valley)
        // meander bends: one arc on each half (a -> c and c -> b); each half's arc depends only on its own
        // end points, so all pieces through a grid point bend its downstream half the same way
        double bowA = meander(ax, az, cx, cz, wa) * sideA, bowB = meander(cx, cz, bx, bz, wb) * sideB;
        int mid = steps / 2;
        double[] sx = xs.clone(), sz = zs.clone();
        for (int s = 0; s <= steps; s++) {
            double along = Math.min(s, steps - s) * 0.5;            // blocks from the nearer end
            double taper = Math.min(1.0, along / VALLEY_TAPER);
            taper = taper * taper * (3.0 - 2.0 * taper);
            int a = Math.max(0, s - 2), b = Math.min(steps, s + 2);
            double tx = sx[b] - sx[a], tz = sz[b] - sz[a], tl = Math.hypot(tx, tz);
            if (tl < 1e-6) continue;
            double bend = s <= mid ? bowA * Math.sin(Math.PI * s / Math.max(1, mid))
                    : bowB * Math.sin(Math.PI * (s - mid) / Math.max(1, steps - mid));
            double o = off[s] * taper + bend;                                   // valley floor + meander bend
            xs[s] = sx[s] - tz / tl * o;
            zs[s] = sz[s] + tx / tl * o;
        }
        int prev = -1;
        for (int s = 0; s <= steps; s++) {                          // ground top block every block
            if (s % 2 != 0 && s != steps) continue;
            top[s] = Math.ceil(this.math.surfaceY(xs[s], zs[s])) - 1.0;
            if (prev >= 0) {                                       // the half-block points between: the lower one
                for (int k = prev + 1; k < s; k++) top[k] = Math.min(top[prev], top[s]);   // (the mean put the water
            }                                                      //  flush with the ground where it falls)
            prev = s;
        }
        // The water (see junction): starts at jIn, ends at jOut and never rises on the way. It follows the ground
        // 1 block below its top - also down steep mountain slopes, as steep as they are: where the ground drops, the
        // water drops with it (a waterfall), it is never held back and cut into the slope. Where the ground rises
        // again it keeps its level (the river cuts through - rare, findWay goes round rises).
        double[] ahead = new double[steps + 1];
        for (int s = 0; s <= steps; s++) ahead[s] = Math.round(top[s]) - 1.0;
        ahead[steps] = Math.min(ahead[steps], jOut);
        double[] level = new double[steps + 1];
        double run = jIn;
        for (int s = 0; s <= steps; s++) {
            if (hold == null || !inLake(hold, xs[s], zs[s])) run = Math.min(run, Math.floor(ahead[s]));
            if (s >= mid) run = Math.min(run, jCap);
            level[s] = Math.max(SEA_Y - 1, Math.max(jOut, run));
        }
        for (int s = 0; s <= steps; s++) {
            double wf = wa + (wb - wa) * (s / (double) steps);           // from the width at a to the one at b
            int width = (int) Math.round(wf);
            double water = level[s];
            double gorge = Math.max(0.0, Math.round(top[s]) - 1.0 - water);
            int a = Math.max(0, s - 2), b = Math.min(steps, s + 2);         // direction downstream here
            float angle = (float) Math.atan2(zs[b] - zs[a], xs[b] - xs[a]);
            // steepness: drop over the piece per block of length (mountain streams > ROCKY_SLOPE)
            float slope = (float) Math.max(0.0, (top[0] - top[steps]) / len);
            // curvature (turn per block, + = turning left in x/z; gravel bars and deep outer banks used it until
            // 2026-10-22, the bed is the same round shape everywhere now)
            int c0 = Math.max(0, s - 4), c1 = Math.min(steps, s + 4);
            float curve = 0.0F;
            if (s - c0 >= 2 && c1 - s >= 2) {
                double a1 = Math.atan2(zs[s] - zs[c0], xs[s] - xs[c0]), a2 = Math.atan2(zs[c1] - zs[s], xs[c1] - xs[s]);
                double turn = Math.atan2(Math.sin(a2 - a1), Math.cos(a2 - a1));
                curve = (float) (turn / Math.max(0.5, Math.hypot(xs[c1] - xs[c0], zs[c1] - zs[c0]) * 0.5));
            }
            float[] p = {(float) xs[s], (float) zs[s], (float) water, (float) halfWidth(wf), width, (float) gorge, angle, slope, curve};
            int r = REACH + (int) Math.ceil(halfWidth(wf));
            for (int chx = Math.floorDiv((int) Math.floor(xs[s]) - r, 16); chx <= Math.floorDiv((int) Math.floor(xs[s]) + r, 16); chx++) {
                for (int chz = Math.floorDiv((int) Math.floor(zs[s]) - r, 16); chz <= Math.floorDiv((int) Math.floor(zs[s]) + r, 16); chz++) {
                    tile.byChunk.computeIfAbsent(pack(chx, chz), k -> new ArrayList<>()).add(p);
                }
            }
        }
    }
}
