package net.icegiant.worldgen;

import java.util.Random;

/**
 * Pure maths of the Frozen Realm terrain (no Minecraft classes, so it can be tested on its own).
 *
 * <p>Built from rules measured in real elevation data of the Lyngen Alps (Kartverket DTM 10, CC BY 4.0):
 * <ul>
 *   <li>the height distribution of the land follows the real one (most land low, few high peaks) -
 *       a noise value E is mapped through {@link #HEIGHT_TABLE} to metres, so valleys come out wide
 *       and flat and slopes get steeper with altitude, like in the real data;</li>
 *   <li>big calm forms (massifs, long U-shaped troughs / fjords), sharp crests only high up;</li>
 *   <li>an erosion filter lays branching gullies down the slopes - strong high up, weak low down.</li>
 * </ul>
 * Scale: 1 block = ~3.2 m vertically (1833 m peak -> Y ~636), ~5 m horizontally.
 * The erosion filter idea follows R. S. Johansen's 2026 "erosion filter" article; code is our own.
 * Numbers marked TUNE are the knobs (tuned with a Python preview against the real data).
 */
public final class FrozenTerrainMath {

    // ---- TUNE: big forms ----
    static final double BIG_SCALE = 1700.0, BIG_PERSISTENCE = 0.47, BIG_WARP = 500.0;
    static final int BIG_OCTAVES = 5;
    static final double CREST_WEIGHT = 0.45, CREST_SCALE = 900.0;
    // ---- TUNE: mountain ranges as branching ridge networks (not plateaus) ----
    static final double BIG_WEIGHT = 1.1;
    static final double RANGE_SCALE = 6000;
    static final double RIDGE_SCALE = 2600;
    static final double RIDGE_WEIGHT = 2.0;
    static final double RIDGE_PERSISTENCE = 0.5;
    static final double RIDGE_GAIN = 2.0;
    static final int RIDGE_OCTAVES = 3;
    static final double BELT_EDGE = 0.55;
    static final double SPINE_WEIGHT = 0.2;
    static final double TROUGH_SCALE = 3000.0, TROUGH_WIDTH = 0.16, TROUGH_DEPTH = 0.15;
    static final double COAST_FADE = 0.2;
    // ---- TUNE: coast line follows the relief (mountains reach out as headlands and islands, low ground
    //      becomes bays) and gets a ragged edge from a few noise octaves ----
    static final double COAST_RELIEF = 0.06, COAST_RELIEF_METRES = 500.0, COAST_RELIEF_MID = 0.0;
    static final double COAST_NOISE = 0.06, COAST_SHIFT = -0.06;
    static final double[] COAST_NOISE_SIZE = {420.0, 160.0, 60.0}, COAST_NOISE_WEIGHT = {0.55, 0.3, 0.15};
    // ---- TUNE: erosion filter ----
    static final int BIG_GULLY_OCTAVES = 3;      // octaves (480/240/120 blocks) faded by their own-scale slope
    static final double BIG_SLOPE_REF = 0.22;  // own-scale slope from which a big gully is at full depth
    static final double GULLY_SIZE = 480, GULLY_WEIGHT = 0.10, SLOPE_REF = 0.5, STEER = 0.35;
    static final int GULLY_OCTAVES = 6;
    static final double LOW_GULLY = 0.25, GULLY_FULL_FROM_Y = 90.0, GULLY_FULL_AT_Y = 300.0;
    // ---- TUNE: light surface roughness (so slopes are not perfectly smooth) ----
    static final double ROUGH_AMP_1 = 1.6, ROUGH_SIZE_1 = 14.0, ROUGH_AMP_2 = 0.8, ROUGH_SIZE_2 = 6.0;
    // ---- scale ----
    static final double METRES_PER_BLOCK_Y = 3.2;
    static final double SEA_Y = 63.0;
    static final double SOFT_TOP_Y = 605.0, SOFT_TOP_RANGE = 30.0;   // peaks approach ~Y 630 smoothly
    // ---- TUNE: rolling hills in the lowland forests (like glacial drumlins: long, rounded, all
    //      lying in the direction the old ice flowed, plus small knolls) - only low down ----
    static final double HILL_AMP = 13.0, HILL_LENGTH = 240.0, HILL_WIDTH = 130.0;
    static final double KNOLL_AMP = 4.5, KNOLL_SIZE = 55.0;
    static final double HILL_REGION_SCALE = 1600.0, HILL_REGION_MIN = 0.3;   // some areas gentler, some hillier
    static final double HILL_FADE_FROM_Y = 105.0, HILL_FADE_TO_Y = 170.0;    // mountains above stay unchanged
    // ---- TUNE: the deep sea (Rubenbuben4, 2026-10-21: "tief, wie ein kalter Ozean", floor Y 0-15). Only water
    //      columns change and only away from the coast: near the shore the old shallow shelf stays, then the floor
    //      drops down a continental slope to a deep plain with hills, swells, seamounts and long trenches ----
    //      (2026-10-24, Rubenbuben4: "the ocean is 3 blocks deep again" - the shelf was hundreds of blocks of 3-5 deep
    //      water; now the slope starts close to the shore: was -0.04 +- 0.025, slope 0.07)
    static final double SHELF_EDGE = -0.012, SHELF_EDGE_NOISE = 0.012, SHELF_EDGE_SIZE = 900.0;   // continent value
    static final double SLOPE_WIDTH = 0.05;                         // the floor reaches the deep plain this much further out
    //      (2026-10-25, Rubenbuben4 again: shallow sea far out. Offline check: 8.4 % of the sea was 3-6 deep with land
    //      100-850 blocks away - the continent value stays near the shelf edge over huge areas. Now the floor also
    //      goes down with the distance to the nearest land, whichever is deeper)
    static final double SHELF_WIDTH = 20.0, SHELF_WIDTH_NOISE = 10.0;   // blocks of shallow shelf off the shore ...
    static final double SLOPE_BLOCKS = 100.0;                       // ... then the floor reaches the deep plain this far out
    static final int SHORE_GRID = 8, SHORE_PROBE_DIRS = 12;         // land distance: every 8 blocks, 12 directions
    static final double[] SHORE_PROBE_R = {8, 16, 26, 38, 52, 68, 86, 106, 128};
    static final double DEEP_FLOOR_Y = 10.0;                        // middle of the deep sea floor
    // (2026-10-21 evening, Rubenbuben4: "zu glatt, wie eine Ebene" -> much more relief: ridges, terraces, more mountains)
    static final double SWELL_AMP = 10.0, SWELL_SIZE = 1300.0;      // wide rises and basins
    static final double SEA_RIDGE_AMP = 15.0, SEA_RIDGE_SIZE = 420.0; // long underwater ridges with valleys between
    static final double ABYSS_HILL_AMP = 6.0, ABYSS_HILL_SIZE = 150.0, ABYSS_KNOLL_AMP = 3.0, ABYSS_KNOLL_SIZE = 50.0;
    static final double[] SEA_HILL_AMP = {9.0, 5.0, 2.5}, SEA_HILL_SIZE = {120.0, 60.0, 28.0}; // what you see under water
    static final double SEA_CAVE_FROM_DEPTH = 8.0, SEA_CAVE_FULL_DEPTH = 16.0;   // caves through the floor below this
    static final double SHELF_RELIEF_FROM = 1.0, SHELF_RELIEF_FULL = 9.0;  // shelf relief grows with the depth
    static final double SHELF_HILL_AMP = 3.5, SHELF_KNOB_AMP = 1.5, SHELF_ROCK_SHARE = 0.45;
    static final double OUTCROP_AMP = 13.0, OUTCROP_SIZE = 46.0, OUTCROP_REGION = 420.0;  // rocky crests in patches
    static final double SEAMOUNT_CELL = 900.0, SEAMOUNT_CHANCE = 0.55; // underwater mountains: one per cell at most
    static final double SEAMOUNT_MIN_R = 90.0, SEAMOUNT_MAX_R = 240.0, SEAMOUNT_MIN_H = 18.0, SEAMOUNT_MAX_H = 42.0;
    static final double TERRACE_STEP = 5.0, TERRACE_MAX = 0.75, TERRACE_REGION = 700.0;   // ledges and steps in places
    static final double TRENCH_SIZE = 2600.0, TRENCH_WIDTH = 0.045, TRENCH_DEPTH = 36.0;   // long winding trenches
    static final double SEA_ROUGH_AMP = 0.6;                         // small bumps on the deep floor
    // ---- TUNE: Kristallspitzen (crystal spires biome, Rubenbuben4 2026-10-22): rare, mid-sized patches of the
    //      lowland on hills, far from the mountains, with a ring of snowy plains around them ----
    static final double SPIRE_CELL = 2200.0;                        // at most one patch per cell ...
    static final int SPIRE_TRIES = 12;                              // ... (a few spots tried in each cell)
    static final double SPIRE_MIN_R = 170.0, SPIRE_MAX_R = 300.0;  // radius of the main lobe of a patch ...
    static final int SPIRE_MAX_LOBES = 3;                           // ... plus 1 - 3 smaller lobes grown onto it
    static final double SPIRE_ASPECT = 1.9;                         // stretched up to this much (in some direction)
    static final double SPIRE_WARP = 0.3, SPIRE_WARP_SIZE = 260.0;  // the whole shape bent (x main radius)
    static final double SPIRE_BUFFER = 100.0;                       // ring of snowy plains around it
    static final double SPIRE_EDGE = 0.22, SPIRE_EDGE_SIZE = 170.0; // wavy outline (x radius) ...
    static final double SPIRE_EDGE_FINE = 0.14, SPIRE_EDGE_FINE_SIZE = 55.0;  // ... and a finer wobble
    static final double SPIRE_JOIN = 90.0;                          // lobes flow into each other (no corners)
    static final double SPIRE_LOW_Y = 64.0, SPIRE_HIGH_Y = 135.0;   // only lowland in and around the patch ...
    static final double SPIRE_CLEAR = 250.0, SPIRE_MOUNTAIN_Y = 220.0;  // ... and no mountain this far out
    static final double SPIRE_HILL_AMP = 15.0, SPIRE_HILL_SIZE = 75.0, SPIRE_KNOB_SIZE = 32.0;   // its hills
    static final double SEA_FLOOR_MIN_Y = -40.0, SEA_FLOOR_MAX_DEPTH_TOP = 10.0;   // never deeper / never shallower than this below sea

    private final Grad2[] big = new Grad2[BIG_OCTAVES];
    private final Grad2 crest, cont, cont2, warp, trough1, trough2, rough1, rough2, range, rwarp;
    private final Grad2[] ridge = new Grad2[RIDGE_OCTAVES];
    private final Grad2 hill, knoll, hillRegion;
    private final Grad2[] coastNoise = new Grad2[3];
    private final Grad2 shelfEdge, swell, abyssHill, abyssKnoll, trench, trenchWarp, seaRidge, seaRidge2, seaWarp, terraceRegion;
    private final Grad2[] seaHill = new Grad2[3];
    private final Grad2 spireEdge, spireHill, spireKnob, spireWarpA, spireWarpB, spireEdgeFine;
    private final long spireSeed;
    private final java.util.concurrent.ConcurrentHashMap<Long, double[]> spireSites = new java.util.concurrent.ConcurrentHashMap<>();
    private static final double[] NO_SITE = new double[0];
    private final double hillCos, hillSin;                                 // direction of the old ice flow
    private final long seamountSeed;

    public FrozenTerrainMath(long seed) {
        for (int i = 0; i < BIG_OCTAVES; i++) this.big[i] = new Grad2(seed * 31L + 200 + i);
        this.crest = new Grad2(seed * 31L + 6);
        this.cont = new Grad2(seed * 31L + 1);
        this.cont2 = new Grad2(seed * 31L + 2);
        this.warp = new Grad2(seed * 31L + 3);
        this.trough1 = new Grad2(seed * 31L + 4);
        this.trough2 = new Grad2(seed * 31L + 5);
        this.rough1 = new Grad2(seed * 31L + 7);
        this.rough2 = new Grad2(seed * 31L + 8);
        this.range = new Grad2(seed * 31L + 9);
        this.rwarp = new Grad2(seed * 31L + 10);
        for (int i = 0; i < RIDGE_OCTAVES; i++) this.ridge[i] = new Grad2(seed * 31L + 300 + i);
        this.hill = new Grad2(seed * 31L + 400);
        this.knoll = new Grad2(seed * 31L + 401);
        this.hillRegion = new Grad2(seed * 31L + 402);
        for (int i = 0; i < this.coastNoise.length; i++) this.coastNoise[i] = new Grad2(seed * 31L + 410 + i);
        this.shelfEdge = new Grad2(seed * 31L + 500);       // (new numbers: the land noises above stay as they were)
        this.swell = new Grad2(seed * 31L + 501);
        this.abyssHill = new Grad2(seed * 31L + 502);
        this.abyssKnoll = new Grad2(seed * 31L + 503);
        this.trench = new Grad2(seed * 31L + 504);
        this.trenchWarp = new Grad2(seed * 31L + 505);
        this.seamountSeed = seed * 31L + 506;
        this.seaRidge = new Grad2(seed * 31L + 507);
        this.seaRidge2 = new Grad2(seed * 31L + 508);
        this.seaWarp = new Grad2(seed * 31L + 509);
        this.terraceRegion = new Grad2(seed * 31L + 510);
        for (int i = 0; i < this.seaHill.length; i++) this.seaHill[i] = new Grad2(seed * 31L + 511 + i);
        this.spireEdge = new Grad2(seed * 31L + 520);
        this.spireHill = new Grad2(seed * 31L + 521);
        this.spireKnob = new Grad2(seed * 31L + 522);
        this.spireSeed = seed * 31L + 523;
        this.spireWarpA = new Grad2(seed * 31L + 524);
        this.spireWarpB = new Grad2(seed * 31L + 525);
        this.spireEdgeFine = new Grad2(seed * 31L + 526);
        double flow = new java.util.Random(seed * 31L + 403).nextDouble() * Math.PI;
        this.hillCos = Math.cos(flow);
        this.hillSin = Math.sin(flow);
    }

    // ------------------------------------------------------------------ helpers

    public static double smooth(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3.0 - 2.0 * t);
    }

    static double softAbs(double v, double e) {
        return Math.sqrt(v * v + e * e) - e;
    }

    static double softMin(double a, double b, double k) {
        double h = Math.max(0.0, Math.min(1.0, 0.5 + 0.5 * (b - a) / k));
        return b + (a - b) * h - k * h * (1.0 - h);
    }

    /** 2D gradient noise, about -1..1. */
    static final class Grad2 {
        private final int[] p = new int[512];
        private final double[] gx = new double[256], gz = new double[256];

        Grad2(long seed) {
            Random r = new Random(seed);
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
            }
            for (int i = 0; i < 512; i++) this.p[i] = perm[i & 255];
            for (int i = 0; i < 256; i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                this.gx[i] = Math.cos(a);
                this.gz[i] = Math.sin(a);
            }
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private double g(int ix, int iz, double dx, double dz) {
            int h = this.p[this.p[ix] + iz];
            return this.gx[h] * dx + this.gz[h] * dz;
        }

        double value(double x, double z) {
            double fx = Math.floor(x), fz = Math.floor(z);
            int xi = ((int) (long) fx) & 255, zi = ((int) (long) fz) & 255;
            double xf = x - fx, zf = z - fz;
            double u = fade(xf), v = fade(zf);
            double n00 = g(xi, zi, xf, zf), n10 = g(xi + 1, zi, xf - 1, zf);
            double n01 = g(xi, zi + 1, xf, zf - 1), n11 = g(xi + 1, zi + 1, xf - 1, zf - 1);
            return ((n00 * (1 - u) + n10 * u) * (1 - v) + (n01 * (1 - u) + n11 * u) * v) * 1.4;
        }
    }

    // ------------------------------------------------------------------ layers

    /** Continents: &lt; 0 ocean, &gt; 0 land. */
    public double continent(double x, double z) {
        double cx = x + 1500.0 * this.warp.value(x / 9000.0 + 11.1, z / 9000.0);
        double cz = z + 1500.0 * this.warp.value(x / 9000.0, z / 9000.0 + 23.3);
        return 0.8 * this.cont.value(cx / 12000.0, cz / 12000.0) + 0.3 * this.cont2.value(cx / 3500.0, cz / 3500.0) + 0.05;
    }

    /** Relief value E (only its rank matters - it is mapped to real heights). */
    public double relief(double x, double z) {
        double wx = x + BIG_WARP * this.warp.value(x / 2500.0, z / 2500.0);
        double wz = z + BIG_WARP * this.warp.value(x / 2500.0 + 7.7, z / 2500.0 - 3.3);
        double a = 0.0, b = 1.0, s = BIG_SCALE;
        for (int i = 0; i < BIG_OCTAVES; i++) {
            a += b * this.big[i].value(wx / s, wz / s);
            b *= BIG_PERSISTENCE;
            s *= 0.5;
        }
        a *= BIG_WEIGHT;                                                       // gentle large undulation only
        // mountain ranges: where they are (large scale) x a branching ridge network (ridged multifractal:
        // one main crest per range, side ridges branching off it, valleys in between)
        // ranges are long winding belts (main spine = belt centre), not round blobs
        double bx = x + 1500.0 * this.rwarp.value(x / 7000.0 + 1.3, z / 7000.0);
        double bz = z + 1500.0 * this.rwarp.value(x / 7000.0, z / 7000.0 - 4.4);
        double belt = 1.0 - softAbs(this.range.value(bx / RANGE_SCALE, bz / RANGE_SCALE), 0.05);
        double rangeMask = smooth(BELT_EDGE, 1.0, belt) * smooth(-0.35, 0.15, this.cont2.value(x / 9000.0 + 3.3, z / 9000.0 - 1.7));
        if (rangeMask > 0.0) {
            double rx = x + 900.0 * this.rwarp.value(x / 4000.0, z / 4000.0);
            double rz = z + 900.0 * this.rwarp.value(x / 4000.0 + 3.7, z / 4000.0 - 8.1);
            double sum = 0.0, norm = 0.0, amp = 1.0, f = 1.0 / RIDGE_SCALE, weight = 1.0;
            for (int i = 0; i < RIDGE_OCTAVES; i++) {
                double r = 1.0 - softAbs(this.ridge[i].value(rx * f, rz * f), 0.04);
                r = r * r * r * weight;
                weight = Math.max(0.0, Math.min(1.0, r * RIDGE_GAIN));      // smaller ridges grow on bigger ones
                sum += r * amp;
                norm += amp;
                amp *= RIDGE_PERSISTENCE;
                f *= 2.0;
            }
            a += rangeMask * (SPINE_WEIGHT * rangeMask + RIDGE_WEIGHT * (sum / norm));   // spine + branching ridges
        }
        double vx = x + 400.0 * this.warp.value(x / 2000.0, z / 2000.0);
        double vz = z + 400.0 * this.warp.value(x / 2000.0 + 5.2, z / 2000.0 - 3.1);
        // smooth minimum + smooth abs: no sharp crease lines where the two trough systems meet
        double v = softMin(softAbs(this.trough1.value(vx / TROUGH_SCALE, vz / (TROUGH_SCALE * 2.2)), 0.02),
                softAbs(this.trough2.value(vz / TROUGH_SCALE, vx / (TROUGH_SCALE * 2.2)), 0.02), 0.08);
        a -= TROUGH_DEPTH * (1.0 - smooth(0.0, TROUGH_WIDTH, v));                 // U-troughs / fjords
        return a;
    }

    /** Real-world height in metres above sea for relief value e (land only). */
    public static double metres(double e) {
        double t = (e - TABLE_MIN) / (TABLE_MAX - TABLE_MIN) * (HEIGHT_TABLE.length - 1);
        if (t <= 0) return HEIGHT_TABLE[0];
        // The real height distribution flattens out at the very top (only a few small summits are that
        // high), which squeezed the broad cores of our mountain belts onto one height = plateaus.
        // Above the knee the heights keep rising at the slope they had there; the soft top in
        // surfaceY() keeps the highest peaks below the build limit.
        if (t > TOP_KNEE) return HEIGHT_TABLE[TOP_KNEE] + (t - TOP_KNEE) * TOP_SLOPE;
        int i = (int) t;
        double f = t - i;
        return HEIGHT_TABLE[i] * (1 - f) + HEIGHT_TABLE[i + 1] * f;
    }

    /**
     * Continent value moved by the relief (high ground pushes the coast out to sea, low ground lets the
     * sea in) and by ragged noise - so the coast line is no longer one smooth curve.
     */
    double coast(double x, double z, double m) {
        double c = continent(x, z);
        c += COAST_RELIEF * (smooth(0.0, COAST_RELIEF_METRES, m) - COAST_RELIEF_MID);
        double n = 0.0;
        for (int i = 0; i < this.coastNoise.length; i++) {
            n += COAST_NOISE_WEIGHT[i] * this.coastNoise[i].value(x / COAST_NOISE_SIZE[i], z / COAST_NOISE_SIZE[i]);
        }
        // noise only ever pushes the coast outwards (0..1), so it makes capes, skerries and islands
        // but never punches lakes into the land
        return c + COAST_NOISE * (0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, n))) + COAST_SHIFT;
    }

    /** Surface Y before the erosion filter; out (may be null) gets {continent, metres}. */
    public double baseY(double x, double z, double[] out) {
        double m = metres(relief(x, z));
        double c = coast(x, z, m);
        double hm = m * smooth(0.0, COAST_FADE, c);
        if (out != null) {
            out[0] = c;
            out[1] = hm;
        }
        if (hm < 1.0) {
            return SEA_Y - 1.0 - (3.0 + 40.0 * smooth(0.0, -0.4, c)) * smooth(1.0, 0.0, hm);
        }
        return SEA_Y + hm / METRES_PER_BLOCK_Y;
    }

    // ------------------------------------------------------------------ beaches

    /** Beaches reach at most this many blocks inland from the water line. */
    static final double SHORE_WIDTH = 32.0;
    /** Erosion value inside the snowy_beach range of the dimension's biome list. */
    static final double BEACH_EROSION = 0.0, INLAND_MIN_EROSION = 0.1;
    private static final int SHORE_DIRS = 8;

    /**
     * Erosion value for the biome list. Our coasts are very flat, so a beach chosen by height alone
     * was 100-250 blocks wide. Now a land column is only beach (erosion 0) when water is within
     * SHORE_WIDTH blocks; all other land gets at least INLAND_MIN_EROSION (inland biomes). Water
     * keeps the continent value, so the ocean / deep ocean / river split is unchanged.
     */
    public double shoreErosion(double x, double z) {
        double[] info = new double[2];
        if (baseY(x, z, info) < SEA_Y) return Math.min(info[0], -0.06);  // water: ocean (rivers come later)
        for (int ring = 1; ring <= 2; ring++) {
            double r = SHORE_WIDTH * ring / 2.0;
            for (int i = 0; i < SHORE_DIRS; i++) {
                double a = (i + 0.5 * ring) * (Math.PI * 2.0 / SHORE_DIRS);
                if (baseY(x + r * Math.cos(a), z + r * Math.sin(a), null) < SEA_Y) return BEACH_EROSION;
            }
        }
        return Math.max(info[0], INLAND_MIN_EROSION);
    }

    // ------------------------------------------------------------------ per-column cache

    /**
     * Biomes are sampled every 4 blocks in all three directions, and for each sample the column
     * functions above were computed again (the vanilla 2D cache only remembers the last column, and
     * the biome loop jumps between columns). This small per-thread cache remembers recent columns.
     */
    private static final class ColumnCache {
        final long[] keys = new long[512];
        final double[] values = new double[512];

        ColumnCache() {
            java.util.Arrays.fill(this.keys, Long.MIN_VALUE);
        }
    }

    private final ThreadLocal<ColumnCache> surfaceCache = ThreadLocal.withInitial(ColumnCache::new);
    private final ThreadLocal<ColumnCache> shoreCache = ThreadLocal.withInitial(ColumnCache::new);

    public double cachedSurfaceY(int x, int z) {
        return cached(this.surfaceCache.get(), x, z, true);
    }

    public double cachedShoreErosion(int x, int z) {
        return cached(this.shoreCache.get(), x, z, false);
    }

    /**
     * 0..1: how far the noise caves may come up here - 1 where the sea floor lies {@value #SEA_CAVE_FULL_DEPTH} or
     * more blocks under the water (caves may break through the floor there, Rubenbuben4 2026-10-21), 0 on land, at
     * the shore and in shallow water (there they stay under the ground as before).
     */
    public double seaCaveOpening(int x, int z) {
        return smooth(SEA_Y - SEA_CAVE_FROM_DEPTH, SEA_Y - SEA_CAVE_FULL_DEPTH, cachedSurfaceY(x, z));
    }

    private double cached(ColumnCache cache, int x, int z, boolean surface) {
        long key = ((long) x << 32) | (z & 0xffffffffL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 55);   // top 9 bits -> 0..511
        if (cache.keys[slot] == key) return cache.values[slot];
        double v = surface ? surfaceY(x, z) : shoreErosion(x, z);
        cache.keys[slot] = key;
        cache.values[slot] = v;
        return v;
    }

    // ------------------------------------------------------------------ erosion filter

    private static double hash(long ix, long iz, int salt) {
        long h = (ix * 374761393L + iz * 668265263L + salt * 2147483647L) & 0xffffffffL;
        h = ((h ^ (h >>> 13)) * 1274126177L) & 0xffffffffL;
        return (h & 0xffffL) / 65535.0;
    }

    /** Directional stripes across (px,pz): returns {cos, sin} of the blended stripe phase, length ~1. */
    private static void stripes(double x, double z, double px, double pz, double s, int salt, double[] cs) {
        long cx = (long) Math.floor(x / s), cz = (long) Math.floor(z / s);
        double c = 0, sn = 0, w = 0;
        // the kernel reaches 1.5 cells, so the pivots of the cells two steps away must be included,
        // otherwise the pattern jumps at every cell border (= straight lines through the landscape)
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                long ix = cx + i, iz = cz + j;
                double ox = (ix + hash(ix, iz, salt)) * s, oz = (iz + hash(ix, iz, salt + 1)) * s;
                double dx = x - ox, dz = z - oz;
                double d2 = (dx * dx + dz * dz) / (s * s);
                double k = 1.0 - d2 / 2.25;
                if (k <= 0) continue;
                double wt = k * k * k;
                double ph = (dx * px + dz * pz) / s * (Math.PI * 2.0);
                c += wt * Math.cos(ph);
                sn += wt * Math.sin(ph);
                w += wt;
            }
        }
        if (w <= 0) { cs[0] = 0; cs[1] = 0; return; }
        c /= w; sn /= w;
        double mag = Math.sqrt(c * c + sn * sn) + 1e-9;
        double scale = Math.min(1.0, 2.0 * mag) / mag;
        cs[0] = c * scale;
        cs[1] = sn * scale;
    }

    /** Final surface Y of a column. */
    /** Surface Y without the small roughness (what the rivers follow). */
    public double smoothSurfaceY(double x, double z) {
        double h = erodedY(x, z);
        h += hills(x, z, h);
        if (h > SOFT_TOP_Y) h = SOFT_TOP_Y + SOFT_TOP_RANGE * Math.tanh((h - SOFT_TOP_Y) / SOFT_TOP_RANGE);
        return h;
    }

    public double surfaceY(double x, double z) {
        double h = erodedY(x, z);
        h += hills(x, z, h);
        double land = smooth(SEA_Y + 1.0, SEA_Y + 4.0, h);
        if (h > SOFT_TOP_Y) h = SOFT_TOP_Y + SOFT_TOP_RANGE * Math.tanh((h - SOFT_TOP_Y) / SOFT_TOP_RANGE);   // no flat cut-off at the build limit
        return h + land * (ROUGH_AMP_1 * this.rough1.value(x / ROUGH_SIZE_1, z / ROUGH_SIZE_1)
                + ROUGH_AMP_2 * this.rough2.value(x / ROUGH_SIZE_2, z / ROUGH_SIZE_2));
    }

    /**
     * Rolling hills on top of the lowland (always raising the ground, so no new lakes appear).
     * Full strength from just above the beach up to Y {@value #HILL_FADE_FROM_Y}, gone by
     * Y {@value #HILL_FADE_TO_Y} - the mountains are not touched.
     */
    double hills(double x, double z, double h) {
        double f = smooth(SEA_Y + 0.5, SEA_Y + 2.5, h) * (1.0 - smooth(HILL_FADE_FROM_Y, HILL_FADE_TO_Y, h));
        if (f <= 0.0) return 0.0;
        double u = x * this.hillCos + z * this.hillSin, v = -x * this.hillSin + z * this.hillCos;
        double a = 0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, this.hill.value(u / HILL_LENGTH, v / HILL_WIDTH)));
        double b = 0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, this.knoll.value(x / KNOLL_SIZE, z / KNOLL_SIZE)));
        double region = HILL_REGION_MIN + (1.0 - HILL_REGION_MIN)
                * smooth(-0.4, 0.4, this.hillRegion.value(x / HILL_REGION_SCALE, z / HILL_REGION_SCALE));
        return f * region * (HILL_AMP * a + KNOLL_AMP * b) + spireHills(x, z, h);
    }

    // ------------------------------------------------------------------ Kristallspitzen (crystal spires)

    /**
     * Where the crystal spires biome is (Rubenbuben4 2026-10-22: no circles): a patch is a main lobe with 1 - 3
     * smaller lobes grown onto it, stretched in some direction, bent as a whole and with a wavy outline. out gets the
     * patch nearest to (x, z): {site x, site z, radius of the nearest lobe there, distance / that radius (below 1:
     * inside)}. Returns false if no patch is near.
     */
    public boolean spireSite(double x, double z, double[] out) {
        int ci = (int) Math.floor(x / SPIRE_CELL), cj = (int) Math.floor(z / SPIRE_CELL);
        double best = Double.MAX_VALUE;
        double[] lobe = new double[2];
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                double[] site = spireCell(i, j);
                if (site.length == 0) continue;
                double dx = x - site[0], dz = z - site[1];
                if (dx * dx + dz * dz > (site[2] + SPIRE_BUFFER + 1.0) * (site[2] + SPIRE_BUFFER + 1.0)) continue;
                double e = spireEdgeDistance(site, x, z, lobe);
                if (e < best) {
                    best = e;
                    out[0] = site[0];
                    out[1] = site[1];
                    out[2] = lobe[0];
                    out[3] = lobe[1];
                }
            }
        }
        return best < Double.MAX_VALUE;
    }

    /** Blocks from (x, z) to the patch's outline (negative inside); lobe gets {its radius here, distance / radius}. */
    private double spireEdgeDistance(double[] site, double x, double z, double[] lobe) {
        double r0 = site[6 + 2];
        double w = SPIRE_WARP * r0;
        double wx = x + w * this.spireWarpA.value(x / SPIRE_WARP_SIZE, z / SPIRE_WARP_SIZE) - site[0];
        double wz = z + w * this.spireWarpB.value(x / SPIRE_WARP_SIZE + 7.3, z / SPIRE_WARP_SIZE - 2.9) - site[1];
        double wobble = 1.0 + SPIRE_EDGE * this.spireEdge.value(x / SPIRE_EDGE_SIZE, z / SPIRE_EDGE_SIZE)
                + SPIRE_EDGE_FINE * this.spireEdgeFine.value(x / SPIRE_EDGE_FINE_SIZE, z / SPIRE_EDGE_FINE_SIZE);
        double cr = site[3], sr = site[4], aspect = site[5];
        double e = Double.MAX_VALUE, nearest = Double.MAX_VALUE;
        for (int k = 0, n = (site.length - 6) / 3; k < n; k++) {
            double rx = wx - site[6 + 3 * k], rz = wz - site[7 + 3 * k];
            double u = rx * cr + rz * sr, v = -rx * sr + rz * cr;
            double r = site[8 + 3 * k] * wobble;
            double ek = Math.sqrt(u * u / aspect + v * v * aspect) - r;
            if (ek < nearest) {
                nearest = ek;
                lobe[0] = r;
            }
            if (k == 0) {
                e = ek;
            } else {                                                     // smooth union: rounded joins
                double hk = Math.max(SPIRE_JOIN - Math.abs(e - ek), 0.0) / SPIRE_JOIN;
                e = Math.min(e, ek) - hk * hk * SPIRE_JOIN * 0.25;
            }
        }
        lobe[1] = 1.0 + e / lobe[0];
        return e;
    }

    /** 1 inside the crystal spires patch, 0.5 in the ring of snowy plains around it, -1 elsewhere (for the biomes). */
    public double spireBiome(double x, double z) {
        double[] s = new double[4];
        if (!spireSite(x, z, s)) return -1.0;
        if (s[3] < 1.0) return 1.0;
        return s[3] * s[2] < s[2] + SPIRE_BUFFER ? 0.5 : -1.0;
    }

    /** 0..1: how much of the crystal spires patch is here (1 well inside, fading out over its edge). */
    public double spireCore(double x, double z) {
        double[] s = new double[4];
        if (!spireSite(x, z, s)) return 0.0;
        return smooth(1.0, 0.85, s[3]);
    }

    /** The extra hills of the crystal spires patch (fading out over the ring around it; always raising, land only). */
    double spireHills(double x, double z, double h) {
        double[] s = new double[4];
        if (!spireSite(x, z, s)) return 0.0;
        double d = s[3] * s[2];
        double w = smooth(s[2] + SPIRE_BUFFER * 0.8, s[2] * 0.9, d) * smooth(SEA_Y + 0.5, SEA_Y + 2.5, h);
        if (w <= 0.0) return 0.0;
        double n = 0.7 * this.spireHill.value(x / SPIRE_HILL_SIZE, z / SPIRE_HILL_SIZE)
                + 0.3 * this.spireKnob.value(x / SPIRE_KNOB_SIZE, z / SPIRE_KNOB_SIZE);
        return SPIRE_HILL_AMP * w * (0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, n)));
    }

    /**
     * The patch of a cell: {x, z, extent (farthest its outline can reach), cos and sin of its direction, stretch,
     * then per lobe: offset x, offset z, radius}, or none (no luck, or no spot far enough from mountains and sea).
     */
    private double[] spireCell(int i, int j) {
        long key = ((long) i << 32) | (j & 0xffffffffL);
        double[] v = this.spireSites.get(key);
        if (v != null) return v;
        v = NO_SITE;
        for (int t = 0; t < SPIRE_TRIES && v == NO_SITE; t++) {
            int k = 16 * t;
            double px = (i + 0.2 + 0.6 * spireHash(i, j, k)) * SPIRE_CELL;
            double pz = (j + 0.2 + 0.6 * spireHash(i, j, k + 1)) * SPIRE_CELL;
            double r0 = SPIRE_MIN_R + (SPIRE_MAX_R - SPIRE_MIN_R) * spireHash(i, j, k + 2);
            double rot = spireHash(i, j, k + 3) * Math.PI;
            double aspect = 1.0 + (SPIRE_ASPECT - 1.0) * spireHash(i, j, k + 4);
            int lobes = 1 + 1 + (int) Math.floor(spireHash(i, j, k + 5) * SPIRE_MAX_LOBES);
            double[] site = new double[6 + 3 * lobes];
            site[0] = px;
            site[1] = pz;
            site[3] = Math.cos(rot);
            site[4] = Math.sin(rot);
            site[5] = aspect;
            site[8] = r0;                                               // the main lobe in the middle
            double extent = r0;
            for (int l = 1; l < lobes; l++) {
                double a = spireHash(i, j, k + 6 + 3 * l) * Math.PI * 2.0;
                double off = r0 * (0.55 + 0.45 * spireHash(i, j, k + 7 + 3 * l));
                double lr = r0 * (0.45 + 0.35 * spireHash(i, j, k + 8 + 3 * l));
                site[6 + 3 * l] = Math.cos(a) * off;
                site[7 + 3 * l] = Math.sin(a) * off;
                site[8 + 3 * l] = lr;
                extent = Math.max(extent, off + lr);
            }
            // farthest point: the stretch, the wobble and the bend on top
            site[2] = extent * Math.sqrt(aspect) * (1.0 + 1.1 * (SPIRE_EDGE + SPIRE_EDGE_FINE)) + SPIRE_WARP * r0 * 1.1
                    + SPIRE_JOIN * 0.25;
            if (spireSpotOk(site)) v = site;
        }
        if (this.spireSites.size() > 4096) this.spireSites.clear();
        this.spireSites.put(key, v);
        return v;
    }

    /** Lowland all over the patch and its ring (no sea, no high ground), and no mountain within SPIRE_CLEAR. */
    private boolean spireSpotOk(double[] site) {
        double reach = site[2] + SPIRE_BUFFER + SPIRE_CLEAR, step = 64.0;
        double[] lobe = new double[2];
        for (double dx = -reach; dx <= reach; dx += step) {
            for (double dz = -reach; dz <= reach; dz += step) {
                double x = site[0] + dx, z = site[1] + dz;
                double e = spireEdgeDistance(site, x, z, lobe);
                if (e > SPIRE_BUFFER + SPIRE_CLEAR) continue;
                double y = baseY(x, z, null);
                if (e <= SPIRE_BUFFER ? (y < SPIRE_LOW_Y || y > SPIRE_HIGH_Y) : y > SPIRE_MOUNTAIN_Y) return false;
            }
        }
        return true;
    }

    private double spireHash(int i, int j, int k) {
        long h = this.spireSeed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ ((long) k << 40);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    private double erodedY(double x, double z) {
        double[] info = new double[2];
        double h = baseY(x, z, info);
        double amp = (LOW_GULLY + (1 - LOW_GULLY) * smooth(GULLY_FULL_FROM_Y, GULLY_FULL_AT_Y, h)) * smooth(SEA_Y, SEA_Y + 3.0, h);
        if (amp <= 0) return h < SEA_Y ? seaFloor(x, z, h, info[0]) : h;
        double e = 96;   // slope measured over this distance (large = follow the big slopes)
        double gdx = (baseY(x + e, z, null) - baseY(x - e, z, null)) / (2 * e);
        double gdz = (baseY(x, z + e, null) - baseY(x, z - e, null)) / (2 * e);
        double s = GULLY_SIZE, a = GULLY_WEIGHT * s * amp, out = h, ft = 0.0, mask = 1.0;
        double[] cs = new double[2];
        // slope at the scale of the big gullies themselves (over half their width): on a real mountain
        // flank it stays large, on lumpy gentle ground the little bumps cancel out -> big gullies fade there
        double[] scaleSlope = new double[BIG_GULLY_OCTAVES];
        for (int o = 0; o < BIG_GULLY_OCTAVES; o++) {
            double eo = GULLY_SIZE / (2 << o);
            double sx = (baseY(x + eo, z, null) - baseY(x - eo, z, null)) / (2 * eo);
            double sz = (baseY(x, z + eo, null) - baseY(x, z - eo, null)) / (2 * eo);
            scaleSlope[o] = Math.sqrt(sx * sx + sz * sz);
        }
        for (int o = 0; o < GULLY_OCTAVES; o++) {
            double sl = Math.sqrt(gdx * gdx + gdz * gdz) + 1e-9;
            double px = -gdz / sl, pz = gdx / sl;                  // across the slope
            // wiggle the gullies a little so long even walls do not get ruler-straight flutes
            double wx = x + s * 0.35 * this.rough1.value(x / (s * 2.7), z / (s * 2.7));
            double wz = z + s * 0.35 * this.rough2.value(x / (s * 2.7) + 5.1, z / (s * 2.7) - 2.3);
            stripes(wx, wz, px, pz, s, 17 + o * 3, cs);
            double t = Math.min(1.0, sl / SLOPE_REF);
            double f = (1 - (1 - t) * (1 - t)) * mask;             // fade out on flat ground (peaks, valley floors)
            double fo = f;
            if (o < BIG_GULLY_OCTAVES) {
                double tc = Math.min(1.0, scaleSlope[o] / BIG_SLOPE_REF);
                fo *= tc * tc;                                          // only this octave - finer ones keep their texture
            }
            out += a * (ft + (cs[0] - ft) * fo);
            double k = Math.PI * 2.0 / s;
            gdx += a * STEER * (-cs[1]) * k * px;                  // later octaves follow the new slopes
            gdz += a * STEER * (-cs[1]) * k * pz;
            ft = ft + (cs[0] - ft) * fo;                          // continue from what was applied (no cracks where the slope flips)
            mask *= f;
            s *= 0.5;
            a *= 0.5;
        }
        return out;
    }

    // ------------------------------------------------------------------ the deep sea

    /**
     * The floor of a water column. Only here the sea gets deep - baseY (which the gullies, beaches and rivers look
     * at) keeps the old shallow sea, so the land and the coast stay exactly as they were. Near the shore (continent
     * value above the shelf edge) nothing changes; beyond it the floor goes down a slope to the deep plain.
     *
     * @param h the old floor (baseY), c the continent value there (&lt; 0 at sea)
     */
    double seaFloor(double x, double z, double h, double c) {
        double edge = SHELF_EDGE + SHELF_EDGE_NOISE * this.shelfEdge.value(x / SHELF_EDGE_SIZE, z / SHELF_EDGE_SIZE);
        double deep = smooth(edge, edge - SLOPE_WIDTH, c);                  // 0 on the shelf ... 1 on the deep plain
        double shelf = SHELF_WIDTH + SHELF_WIDTH_NOISE * this.shelfEdge.value(x / SHELF_EDGE_SIZE + 4.4, z / SHELF_EDGE_SIZE - 2.2);
        deep = Math.max(deep, smooth(shelf, shelf + SLOPE_BLOCKS, landDistance(x, z)));   // far from land: deep too
        double near = smooth(SHELF_RELIEF_FROM, SHELF_RELIEF_FULL, SEA_Y - 1.0 - h);   // 0 at the shore line
        if (near > 0.0) {                                                    // the shelf: low hills, rocks, hollows
            double bumps = SHELF_HILL_AMP * this.seaHill[1].value(x / 70.0 + 5.5, z / 70.0 - 1.9)
                    + SHELF_KNOB_AMP * this.seaHill[2].value(x / 24.0 - 3.1, z / 24.0 + 8.8)
                    + SHELF_ROCK_SHARE * pinnacles(x, z);
            h = Math.min(h + near * bumps, Math.max(h, SEA_Y - 2.0));       // never above the water near the shore
        }
        if (deep <= 0.0) return h;
        double plain = DEEP_FLOOR_Y
                + SWELL_AMP * this.swell.value(x / SWELL_SIZE, z / SWELL_SIZE)
                + SEA_RIDGE_AMP * seaRidges(x, z)
                + ABYSS_HILL_AMP * this.abyssHill.value(x / ABYSS_HILL_SIZE, z / ABYSS_HILL_SIZE)
                + ABYSS_KNOLL_AMP * this.abyssKnoll.value(x / ABYSS_KNOLL_SIZE, z / ABYSS_KNOLL_SIZE)
                + seaHills(x, z)
                + seamount(x, z)
                + pinnacles(x, z);
        plain = terrace(x, z, plain)                                         // ledges in some places
                - TRENCH_DEPTH * trench(x, z) * smooth(0.6, 1.0, deep)        // trenches only out on the plain
                + SEA_ROUGH_AMP * this.rough1.value(x / ROUGH_SIZE_1, z / ROUGH_SIZE_1);
        plain = Math.max(SEA_FLOOR_MIN_Y, Math.min(SEA_Y - SEA_FLOOR_MAX_DEPTH_TOP, plain));
        return h + (plain - h) * deep;                                      // (no step where the slope begins)
    }

    private final ThreadLocal<ColumnCache> landCache = ThreadLocal.withInitial(ColumnCache::new);

    /**
     * Blocks to the nearest land (old surface baseY at or above the water) - measured on a grid every
     * {@value #SHORE_GRID} blocks and blended between the grid points, so it is smooth and the same in every chunk.
     * At most the last probe radius (128): further out it does not matter any more.
     */
    double landDistance(double x, double z) {
        double gx = x / SHORE_GRID, gz = z / SHORE_GRID;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        ColumnCache cache = this.landCache.get();
        double a = gridLandDistance(cache, ix, iz), b = gridLandDistance(cache, ix + 1, iz);
        double c = gridLandDistance(cache, ix, iz + 1), d = gridLandDistance(cache, ix + 1, iz + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private double gridLandDistance(ColumnCache cache, int ix, int iz) {
        long key = ((long) ix << 32) | (iz & 0xffffffffL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 55);
        if (cache.keys[slot] == key) return cache.values[slot];
        double v = probeLand(ix * (double) SHORE_GRID, iz * (double) SHORE_GRID);
        cache.keys[slot] = key;
        cache.values[slot] = v;
        return v;
    }

    /** Rings of probes around (x, z); on the first ring that hits land, the exact distance is narrowed down. */
    private double probeLand(double x, double z) {
        if (baseY(x, z, null) >= SEA_Y) return 0.0;
        double inner = 0.0;
        for (double r : SHORE_PROBE_R) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < SHORE_PROBE_DIRS; i++) {
                double ang = (i + (r % 2 == 0 ? 0.0 : 0.5)) * (Math.PI * 2.0 / SHORE_PROBE_DIRS);
                double cx = Math.cos(ang), cz = Math.sin(ang);
                if (baseY(x + r * cx, z + r * cz, null) < SEA_Y) continue;
                double lo = inner, hi = r;                                    // water at lo, land at hi
                for (int s = 0; s < 4; s++) {
                    double mid = 0.5 * (lo + hi);
                    if (baseY(x + mid * cx, z + mid * cz, null) >= SEA_Y) hi = mid; else lo = mid;
                }
                best = Math.min(best, hi);
            }
            if (best < Double.MAX_VALUE) return best;
            inner = r;
        }
        return SHORE_PROBE_R[SHORE_PROBE_R.length - 1];
    }

    /** About -0.5..0.5: branching ridges (sharp crests, wide valleys), winding, two sizes. */
    double seaRidges(double x, double z) {
        double wx = x + 260.0 * this.seaWarp.value(x / 900.0, z / 900.0);
        double wz = z + 260.0 * this.seaWarp.value(x / 900.0 + 3.7, z / 900.0 - 5.9);
        double a = 1.0 - Math.abs(this.seaRidge.value(wx / SEA_RIDGE_SIZE, wz / SEA_RIDGE_SIZE));
        double b = 1.0 - Math.abs(this.seaRidge2.value(wx / (SEA_RIDGE_SIZE * 0.45), wz / (SEA_RIDGE_SIZE * 0.45)));
        a = Math.max(0.0, Math.min(1.0, a));
        b = Math.max(0.0, Math.min(1.0, b));
        return a * a * 0.8 + b * b * a * 0.45 - 0.55;                       // small ridges grow on the big ones
    }

    /** Rounded hills and hollows at the sizes you see under water (30 - 120 blocks), stronger in some places. */
    double seaHills(double x, double z) {
        double region = 0.55 + 0.45 * this.terraceRegion.value(x / 1900.0 + 7.7, z / 1900.0 - 2.2);
        double sum = 0.0;
        for (int i = 0; i < this.seaHill.length; i++) {
            double n = this.seaHill[i].value(x / SEA_HILL_SIZE[i], z / SEA_HILL_SIZE[i]);
            sum += SEA_HILL_AMP[i] * (i == 0 ? n : Math.signum(n) * n * n * 1.6);   // smaller ones a bit knobbly
        }
        return sum * (0.25 + 0.75 * smooth(0.0, 1.0, region));             // (smooth: no crease lines)
    }

    /** Rocky crests and knobs in patches: sharp, winding, broken (they show stone faces). */
    double pinnacles(double x, double z) {
        double region = smooth(0.15, 0.55, this.seaWarp.value(x / OUTCROP_REGION - 11.3, z / OUTCROP_REGION + 4.4));
        if (region <= 0.0) return 0.0;
        double wx = x + 18.0 * this.seaHill[2].value(x / 37.0 + 2.2, z / 37.0), wz = z + 18.0 * this.seaHill[2].value(x / 37.0, z / 37.0 - 6.1);
        double r = 1.0 - Math.abs(this.seaRidge2.value(wx / OUTCROP_SIZE + 31.7, wz / OUTCROP_SIZE - 17.3));
        r = Math.max(0.0, Math.min(1.0, r));
        double broken = 0.5 + 0.5 * this.seaHill[1].value(x / 23.0 - 9.9, z / 23.0 + 3.3);   // gaps along the crest
        return OUTCROP_AMP * region * r * r * r * r * Math.max(0.0, Math.min(1.0, broken * 1.6 - 0.2));
    }

    /** In some regions the floor goes up in steps (ledges with short steep faces), elsewhere it stays smooth. */
    double terrace(double x, double z, double y) {
        double mix = TERRACE_MAX * smooth(-0.1, 0.5, this.terraceRegion.value(x / TERRACE_REGION, z / TERRACE_REGION));
        if (mix <= 0.0) return y;
        double f = y / TERRACE_STEP, i = Math.floor(f), t = f - i;
        double t3 = t * t * t, u3 = (1 - t) * (1 - t) * (1 - t);
        double stepped = (i + t3 / (t3 + u3)) * TERRACE_STEP;
        return y + (stepped - y) * mix;
    }

    /** Height of the underwater mountains here: round cones with a soft top, at most one per cell. */
    double seamount(double x, double z) {
        int ci = (int) Math.floor(x / SEAMOUNT_CELL), cj = (int) Math.floor(z / SEAMOUNT_CELL);
        double best = 0.0;
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                if (cellHash(i, j, 0) >= SEAMOUNT_CHANCE) continue;
                double mx = (i + 0.15 + 0.7 * cellHash(i, j, 1)) * SEAMOUNT_CELL;
                double mz = (j + 0.15 + 0.7 * cellHash(i, j, 2)) * SEAMOUNT_CELL;
                double r = SEAMOUNT_MIN_R + (SEAMOUNT_MAX_R - SEAMOUNT_MIN_R) * cellHash(i, j, 3);
                double top = SEAMOUNT_MIN_H + (SEAMOUNT_MAX_H - SEAMOUNT_MIN_H) * cellHash(i, j, 4);
                double d = Math.hypot(x - mx, z - mz) / r;
                if (d >= 1.0) continue;
                double f = 1.0 - d * d;
                double hgt = top * f * f * (1.0 + 0.25 * this.abyssKnoll.value(x / 50.0, z / 50.0));
                if (cellHash(i, j, 5) < 0.4) hgt = Math.min(hgt, top * 0.62);  // some with a flat top (guyot)
                best = Math.max(best, hgt);
            }
        }
        return best;
    }

    /** 0..1: inside one of the long winding trenches (1 = trench bottom). */
    double trench(double x, double z) {
        double wx = x + 700.0 * this.trenchWarp.value(x / 1800.0, z / 1800.0);
        double wz = z + 700.0 * this.trenchWarp.value(x / 1800.0 + 4.1, z / 1800.0 - 2.6);
        double v = Math.abs(this.trench.value(wx / TRENCH_SIZE, wz / TRENCH_SIZE));
        double along = this.trenchWarp.value(x / 700.0 - 9.3, z / 700.0 + 6.2);  // width and depth change along it
        double t = 1.0 - smooth(0.0, TRENCH_WIDTH * (1.0 + 0.45 * along), v);
        return t * Math.sqrt(t) * (0.75 + 0.35 * along);                   // steep walls, flat-ish bottom
    }

    private double cellHash(int i, int j, int k) {
        long h = this.seamountSeed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ ((long) k << 48);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    // ------------------------------------------------------------------ relief -> metres table
    // Generated from the real Lyngen height distribution (Kartverket DTM 10) and the distribution of
    // relief() over land (sampled with this class). See tools: TerrainTableTool.
    static final double TABLE_MIN = -1.1051511064965318, TABLE_MAX = 2.698891158452583;
    /** Relief value (uniform steps from TABLE_MIN to TABLE_MAX) -> metres above sea; stored in decimetres. */
    static final double[] HEIGHT_TABLE = parseTable(
            "22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,22,23,23,23,23,23,23," +
            "23,23,23,23,23,23,23,23,23,23,23,23,23,23,23,23,23,24,24,24,24,24,24,24,24,24,24,24,24,24,24,24,25,25,25,25," +
            "25,25,25,25,25,25,25,25,25,26,26,26,26,26,26,26,26,26,26,26,27,27,27,27,27,27,27,27,27,28,28,28,28,28,28,28," +
            "29,29,29,29,29,29,30,30,30,30,30,30,31,31,31,31,31,32,32,32,32,33,33,33,33,34,34,34,34,35,35,35,35,36,36,36," +
            "37,37,37,38,38,38,39,39,39,40,40,41,41,41,42,42,43,43,43,44,44,45,45,46,46,47,47,48,48,49,49,50,50,51,51,52," +
            "53,53,54,54,55,56,56,57,57,58,59,59,60,61,61,62,63,64,64,65,66,66,67,68,69,69,70,71,72,72,73,74,75,76,77,77," +
            "78,79,80,81,82,83,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,109,110," +
            "111,112,113,114,116,117,118,119,121,122,123,124,126,127,128,129,131,132,133,135,136,137,139,140,142,143,144," +
            "146,147,149,150,152,153,155,156,158,159,161,162,164,165,167,168,169,171,172,174,175,177,178,180,181,183,184," +
            "186,187,189,190,192,194,195,197,198,200,202,203,205,207,209,210,212,214,216,218,220,222,224,226,228,230,232," +
            "235,237,239,241,244,246,248,251,253,256,258,261,263,266,268,271,273,276,279,281,284,286,289,292,295,298,300," +
            "303,306,309,312,315,318,321,324,327,330,333,336,340,343,346,349,353,356,359,362,366,369,373,376,379,383,386," +
            "390,394,397,401,404,408,412,415,419,423,427,430,434,438,442,446,450,453,457,461,465,469,473,477,481,485,489," +
            "493,497,502,506,510,514,518,522,527,531,535,539,544,548,552,557,561,565,570,574,579,583,588,592,597,602,606," +
            "611,616,621,626,630,635,640,645,650,656,661,666,671,677,682,687,693,698,704,709,715,721,727,732,738,744,750," +
            "756,762,768,774,780,786,793,799,805,811,818,824,830,837,843,850,856,863,869,876,883,889,896,903,910,917,924," +
            "930,937,944,952,959,966,973,980,988,995,1003,1010,1018,1025,1033,1041,1049,1056,1064,1072,1080,1088,1096,1104," +
            "1112,1120,1128,1136,1144,1152,1161,1169,1177,1185,1194,1202,1210,1219,1227,1236,1244,1253,1262,1270,1279,1287," +
            "1296,1305,1313,1322,1331,1340,1348,1357,1366,1375,1383,1392,1401,1410,1419,1428,1437,1446,1455,1464,1473,1481," +
            "1490,1499,1508,1517,1525,1534,1542,1551,1559,1568,1576,1585,1593,1602,1610,1619,1628,1637,1646,1655,1664,1673," +
            "1683,1692,1702,1711,1721,1730,1740,1750,1760,1769,1779,1789,1799,1809,1819,1829,1839,1849,1859,1869,1879,1889," +
            "1899,1910,1920,1930,1940,1950,1960,1970,1981,1991,2001,2011,2021,2031,2041,2051,2061,2071,2081,2091,2101,2110," +
            "2120,2130,2140,2150,2160,2169,2179,2189,2199,2208,2218,2228,2238,2248,2258,2268,2278,2288,2298,2308,2318,2328," +
            "2338,2348,2358,2369,2379,2389,2399,2409,2419,2429,2439,2449,2459,2470,2480,2490,2500,2510,2519,2529,2539,2549," +
            "2558,2568,2577,2586,2596,2605,2614,2623,2633,2642,2651,2661,2670,2680,2690,2699,2709,2719,2729,2739,2748,2758," +
            "2768,2778,2788,2798,2808,2818,2828,2838,2848,2858,2869,2879,2889,2899,2910,2920,2930,2940,2950,2961,2971,2981," +
            "2991,3000,3010,3020,3031,3041,3051,3061,3071,3081,3092,3102,3112,3123,3133,3143,3153,3164,3174,3184,3194,3204," +
            "3215,3225,3235,3246,3256,3267,3277,3288,3299,3309,3320,3331,3342,3352,3363,3374,3385,3395,3406,3417,3428,3438," +
            "3449,3460,3471,3482,3492,3503,3514,3525,3536,3547,3558,3569,3580,3591,3602,3613,3624,3635,3646,3657,3667,3679," +
            "3690,3701,3712,3723,3734,3745,3756,3767,3777,3788,3799,3810,3821,3832,3843,3854,3865,3876,3887,3899,3910,3921," +
            "3932,3943,3954,3964,3975,3986,3997,4008,4019,4029,4040,4051,4062,4073,4084,4095,4106,4117,4127,4138,4149,4160," +
            "4171,4182,4193,4204,4215,4226,4237,4248,4259,4270,4281,4292,4304,4315,4326,4337,4348,4359,4370,4381,4392,4403," +
            "4413,4424,4435,4446,4457,4467,4478,4489,4500,4510,4521,4532,4543,4553,4564,4575,4586,4596,4607,4618,4629,4640," +
            "4650,4661,4672,4683,4695,4706,4717,4728,4739,4750,4761,4773,4784,4795,4806,4817,4828,4840,4851,4862,4873,4885," +
            "4896,4907,4918,4929,4941,4952,4963,4974,4985,4996,5007,5018,5029,5040,5051,5062,5073,5083,5094,5105,5115,5126," +
            "5136,5147,5158,5168,5179,5190,5200,5211,5221,5232,5243,5253,5264,5274,5285,5295,5306,5316,5327,5337,5347,5357," +
            "5368,5378,5388,5398,5408,5418,5428,5438,5448,5458,5468,5477,5487,5497,5507,5517,5526,5536,5546,5556,5565,5575," +
            "5585,5595,5604,5614,5624,5634,5644,5654,5663,5673,5683,5693,5703,5713,5724,5734,5744,5754,5764,5774,5785,5795," +
            "5805,5815,5826,5836,5846,5857,5867,5877,5887,5898,5908,5918,5929,5939,5949,5960,5970,5980,5990,6001,6011,6021," +
            "6031,6041,6052,6062,6072,6082,6092,6103,6113,6123,6134,6144,6155,6165,6176,6186,6197,6207,6218,6229,6239,6250," +
            "6261,6271,6282,6293,6303,6314,6324,6335,6345,6356,6366,6376,6387,6397,6407,6418,6428,6438,6448,6459,6469,6479," +
            "6489,6499,6509,6520,6530,6540,6550,6560,6570,6580,6590,6600,6610,6621,6631,6641,6651,6661,6671,6681,6692,6702," +
            "6712,6722,6732,6741,6751,6761,6771,6780,6790,6799,6809,6818,6828,6837,6847,6856,6866,6875,6885,6894,6903,6913," +
            "6922,6932,6941,6950,6960,6969,6978,6987,6997,7006,7015,7025,7034,7043,7052,7062,7071,7080,7090,7099,7108,7118," +
            "7127,7136,7146,7155,7164,7174,7183,7192,7202,7211,7221,7230,7239,7249,7258,7268,7277,7287,7296,7306,7315,7325," +
            "7335,7345,7354,7364,7374,7384,7394,7403,7413,7423,7432,7442,7452,7461,7471,7480,7490,7500,7509,7519,7529,7539," +
            "7548,7558,7568,7578,7588,7598,7607,7617,7627,7637,7647,7657,7667,7677,7687,7697,7707,7717,7727,7737,7747,7756," +
            "7766,7776,7786,7796,7806,7816,7825,7835,7845,7855,7866,7876,7886,7896,7906,7917,7927,7937,7947,7958,7968,7978," +
            "7989,7999,8009,8019,8030,8040,8050,8061,8071,8081,8091,8101,8112,8122,8132,8142,8152,8162,8172,8183,8193,8203," +
            "8213,8224,8234,8244,8255,8265,8276,8286,8296,8307,8317,8328,8338,8348,8359,8369,8379,8389,8400,8410,8420,8430," +
            "8440,8451,8461,8471,8481,8491,8501,8511,8521,8531,8541,8551,8561,8571,8581,8591,8601,8611,8621,8631,8641,8651," +
            "8661,8672,8682,8692,8702,8712,8723,8733,8743,8753,8764,8774,8784,8795,8805,8815,8826,8836,8846,8857,8867,8877," +
            "8888,8898,8908,8919,8929,8940,8950,8960,8971,8981,8991,9002,9012,9022,9033,9043,9053,9064,9074,9085,9095,9106," +
            "9116,9126,9137,9147,9157,9168,9178,9189,9199,9209,9220,9230,9241,9251,9261,9272,9282,9293,9303,9313,9324,9334," +
            "9344,9355,9365,9375,9386,9396,9406,9417,9427,9437,9448,9458,9469,9479,9490,9500,9511,9522,9532,9543,9553,9564," +
            "9575,9586,9596,9607,9618,9628,9639,9650,9660,9671,9682,9693,9703,9714,9725,9736,9746,9757,9768,9779,9790,9801," +
            "9812,9822,9833,9844,9855,9866,9878,9889,9900,9911,9922,9933,9944,9955,9966,9977,9988,9999,10010,10021,10032," +
            "10042,10053,10064,10075,10086,10097,10107,10118,10129,10140,10151,10162,10173,10183,10194,10205,10216,10227," +
            "10238,10249,10260,10272,10283,10294,10305,10316,10328,10339,10350,10362,10373,10385,10397,10408,10420,10432," +
            "10444,10455,10467,10479,10491,10503,10515,10527,10540,10552,10564,10576,10588,10600,10613,10625,10637,10649," +
            "10661,10674,10686,10698,10710,10722,10734,10746,10758,10770,10783,10795,10807,10819,10831,10843,10855,10867," +
            "10880,10892,10904,10916,10928,10941,10953,10965,10977,10990,11002,11015,11027,11039,11052,11064,11077,11090," +
            "11102,11115,11128,11141,11153,11166,11179,11192,11205,11218,11231,11244,11256,11269,11282,11295,11308,11321," +
            "11334,11347,11360,11373,11386,11399,11413,11426,11440,11453,11467,11480,11494,11507,11521,11534,11548,11562," +
            "11575,11589,11602,11615,11629,11642,11656,11669,11682,11696,11709,11722,11735,11749,11762,11775,11789,11802," +
            "11815,11829,11842,11856,11869,11883,11897,11910,11924,11938,11952,11966,11980,11995,12009,12023,12038,12052," +
            "12067,12081,12096,12111,12126,12140,12155,12170,12185,12200,12214,12229,12244,12259,12274,12289,12304,12319," +
            "12335,12350,12366,12382,12398,12414,12430,12447,12464,12480,12497,12515,12532,12549,12566,12584,12601,12619," +
            "12636,12653,12671,12688,12705,12722,12739,12756,12772,12789,12806,12822,12839,12856,12872,12889,12906,12923," +
            "12940,12956,12973,12990,13007,13024,13041,13058,13075,13092,13109,13126,13143,13160,13177,13194,13211,13228," +
            "13245,13262,13279,13296,13313,13330,13347,13365,13382,13399,13416,13433,13451,13468,13486,13504,13521,13539," +
            "13557,13575,13593,13612,13630,13648,13667,13686,13704,13723,13742,13761,13781,13800,13819,13839,13858,13877," +
            "13897,13916,13935,13954,13973,13991,14010,14028,14047,14065,14083,14100,14118,14135,14152,14170,14186,14203," +
            "14220,14237,14253,14270,14286,14303,14319,14336,14353,14370,14386,14403,14420,14437,14453,14470,14487,14503," +
            "14520,14536,14552,14568,14585,14601,14617,14633,14650,14666,14683,14699,14715,14732,14748,14765,14781,14797," +
            "14813,14829,14845,14860,14876,14891,14907,14922,14937,14952,14967,14982,14996,15011,15025,15039,15054,15068," +
            "15082,15095,15109,15123,15137,15151,15165,15179,15193,15207,15221,15235,15249,15262,15276,15290,15303,15316," +
            "15329,15343,15355,15368,15381,15393,15406,15418,15431,15443,15455,15467,15480,15492,15504,15516,15527,15539," +
            "15550,15562,15573,15584,15595,15606,15617,15627,15638,15648,15658,15668,15678,15688,15697,15707,15716,15726," +
            "15735,15744,15753,15763,15772,15781,15790,15799,15808,15817,15825,15834,15843,15851,15860,15868,15877,15885," +
            "15893,15901,15908,15916,15924,15931,15939,15946,15953,15961,15968,15976,15983,15991,15998,16005,16013,16020," +
            "16027,16034,16041,16048,16055,16062,16068,16075,16081,16087,16093,16099,16105,16111,16117,16123,16129,16135," +
            "16141,16146,16152,16157,16163,16168,16174,16179,16184,16189,16194,16199,16203,16208,16213,16217,16222,16227," +
            "16231,16236,16240,16245,16249,16254,16258,16263,16267,16272,16276,16280,16285,16289,16293,16297,16302,16306," +
            "16310,16314,16318,16322,16325,16329,16333,16337,16340,16344,16347,16350,16354,16357,16361,16364,16367,16371," +
            "16374,16378,16381,16385,16388,16392,16395,16399,16402,16405,16409,16412,16415,16418,16422,16425,16428,16431," +
            "16433,16436,16439,16442,16445,16447,16450,16452,16455,16458,16460,16463,16465,16468,16470,16473,16475,16478," +
            "16480,16483,16485,16488,16490,16493,16495,16498,16500,16502,16505,16507,16509,16511,16513,16515,16517,16519," +
            "16521,16523,16524,16526,16528,16530,16532,16534,16536,16538,16540,16541,16543,16545,16546,16548,16549,16550," +
            "16552,16553,16554,16555,16557,16558,16559,16560,16562,16563,16565,16566,16568,16569,16570,16572,16573,16575," +
            "16576,16577,16579,16580,16581,16582,16583,16585,16586,16587,16588,16589,16590,16591,16592,16593,16594,16595," +
            "16596,16597,16598,16600,16601,16602,16603,16604,16605,16606,16607,16608,16609,16610,16611,16612,16613,16614," +
            "16615,16616,16617,16618,16619,16621,16622,16623,16624,16625,16626,16627,16628,16628,16629,16630,16630,16631," +
            "16631,16632,16632,16632,16632,16633");

    /** Table step where the real height distribution starts to flatten, and the slope (metres per
     *  step) it had there - computed once from the table, so it stays right when the table is refit. */
    static final int TOP_KNEE;
    static final double TOP_SLOPE;

    static {
        int n = HEIGHT_TABLE.length, w = 7;
        double[] d = new double[n];
        for (int i = w; i < n - 1 - w; i++) d[i] = (HEIGHT_TABLE[i + w] - HEIGHT_TABLE[i - w]) / (2.0 * w);
        int pk = 0;
        for (int i = 0; i < n; i++) if (d[i] > d[pk]) pk = i;
        int k = pk;
        while (k < n - 2 - w && d[k] > 0.75 * d[pk]) k++;
        TOP_KNEE = k;
        TOP_SLOPE = d[k];
    }

    private static double[] parseTable(String s) {
        String[] t = s.split(",");
        double[] v = new double[t.length];
        for (int i = 0; i < t.length; i++) v[i] = Integer.parseInt(t[i].trim()) / 10.0;
        return v;
    }
}
