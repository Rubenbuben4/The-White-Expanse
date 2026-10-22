package net.icegiant.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ice spires of the Kristallspitzen biome (Rubenbuben4, 2026-10-22): the pointed, faceted ice spires of the
 * icebergs (Icebergs SHARDS), here standing on treeless hills in clusters that point in different directions - one
 * nearly upright in the middle, the others fanning out around it like a crystal druse. Packed ice inside, clear and
 * blue ice outside, veins of Glimmeis (glow ice) running up them, glowing tips, and short strands of Glimmeis hanging
 * under the leaning ones like cave vines. Between the clusters small shards stick out of the snow.
 * <p>
 * Pure function of the world seed and the block position like Icebergs (no Minecraft classes, test/CrystalSpiresTest):
 * the ground height and how much of the biome is where come in from the terrain maths.
 */
public final class CrystalSpires {

    // ---- TUNE: where ----
    static final double CELL = 22.0;                       // at most one cluster per cell ...
    static final double CHANCE = 0.72;                     // ... with this chance in the middle of the biome
    static final double SHARD_CELL = 7.0, SHARD_CHANCE = 0.22;   // small single shards between the clusters
    static final double SMALL_SHARE = 0.55, LARGE_SHARE = 0.1;  // (the rest: medium clusters)
    // ---- TUNE: size [small, medium, large] ----
    static final double[] MIN_H = {7, 14, 28}, MAX_H = {14, 28, 45};   // tallest spire of a cluster above the ground
    static final int[] MIN_SPIRES = {3, 4, 6}, MAX_SPIRES = {5, 7, 9};
    static final double R_MIN = 0.22, R_MAX = 0.3;         // foot radius (x height)
    static final double MAIN_TILT = 12.0, SIDE_TILT_MIN = 16.0, SIDE_TILT_MAX = 42.0;   // degrees from upright
    static final double SIDE_H_MIN = 0.35, SIDE_H_MAX = 0.85;  // side spires (x the middle one)
    static final double SINK = 3.0;                        // feet start this deep in the ground
    static final double FACETS = 0.22;                     // uneven, faceted faces
    // ---- TUNE: look ----
    static final double SHELL = 0.6;                       // outer part (rel. radius) with veins / clear ice
    static final double GLOW_VEINS = 0.2, BLUE_VEINS = 0.22;   // share of glowing / blue streaks up a spire
    static final double GLOW_TIP = 0.86;                   // the top 14 % of every spire glows
    static final double STRAND_CHANCE = 0.1;               // under an overhang: a hanging strand of glow ice ...
    static final int STRAND_MAX = 3;                       // ... 1 - 3 blocks long
    static final double SNOW_DUST = 0.2;                   // a little snow on the faces looking up

    public static final byte NONE = 0, PACKED_ICE = 1, BLUE_ICE = 2, ICE = 3, SNOW = 4, GLOW_ICE = 5;
    /** {@link #column} fills Y from {@link #columnMinY} on, this many blocks. */
    public static final int COLUMN_SIZE = 72;
    static final double MAX_REACH = 64.0, SHARD_REACH = 5.0;

    /** Ground height (terrain surface Y) and how much of the biome is here (0..1). */
    public interface Ground {
        double y(double x, double z);

        double core(double x, double z);
    }

    /** One spire: foot, size, lean (offset of the tip per block of height), cross-section. */
    record Spire(double x, double z, double y0, double r, double h, double leanX, double leanZ, int sides, double turn,
                 long salt) {
    }

    /** One cluster (or one small shard): its middle, its spires, how far its blocks can be, its Y range. */
    public record Cluster(double x, double z, int size, double ground, Spire[] spires, double reach, int bottomY,
                          int topY) {
    }

    private static final ConcurrentHashMap<Long, Cluster[]> CACHE = new ConcurrentHashMap<>();
    private static final Cluster[] EMPTY = new Cluster[0];

    private CrystalSpires() {
    }

    // ------------------------------------------------------------------ where

    /** All clusters and shards that may reach into the square x0..x1, z0..z1. */
    public static List<Cluster> near(long seed, int x0, int z0, int x1, int z1, Ground ground) {
        List<Cluster> out = new ArrayList<>(8);
        collect(seed, x0, z0, x1, z1, ground, CELL, MAX_REACH, 0, out);
        collect(seed, x0, z0, x1, z1, ground, SHARD_CELL, SHARD_REACH, 1, out);
        return out;
    }

    private static void collect(long seed, int x0, int z0, int x1, int z1, Ground ground, double cell, double reach,
                                int tag, List<Cluster> out) {
        int i0 = (int) Math.floor((x0 - reach) / cell), i1 = (int) Math.floor((x1 + reach) / cell);
        int j0 = (int) Math.floor((z0 - reach) / cell), j1 = (int) Math.floor((z1 + reach) / cell);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                for (Cluster c : cell(seed, i, j, tag, ground)) {
                    if (c.x + c.reach < x0 || c.x - c.reach > x1 + 1 || c.z + c.reach < z0 || c.z - c.reach > z1 + 1) continue;
                    out.add(c);
                }
            }
        }
    }

    private static Cluster[] cell(long seed, int i, int j, int tag, Ground ground) {
        long key = seed * 0x2545F4914F6CDD1DL ^ ((long) i << 32) ^ (j & 0xffffffffL) ^ ((long) tag << 62);
        Cluster[] v = CACHE.get(key);
        if (v != null) return v;
        v = make(seed, i, j, tag, ground);
        if (CACHE.size() > 65536) CACHE.clear();
        CACHE.put(key, v);
        return v;
    }

    static Cluster[] make(long seed, int i, int j, int tag, Ground ground) {
        Rand r = new Rand(seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (tag == 0 ? 0x5B1CE5L : 0x5A4D5L));
        double cell = tag == 0 ? CELL : SHARD_CELL;
        double x = (i + 0.1 + 0.8 * r.next()) * cell, z = (j + 0.1 + 0.8 * r.next()) * cell;
        double core = ground.core(x, z);
        if (core <= 0.0 || r.next() >= (tag == 0 ? CHANCE : SHARD_CHANCE) * core) return EMPTY;
        double gy = ground.y(x, z);
        if (gy < 64.0) return EMPTY;                       // (not in the water)
        if (tag == 1) return new Cluster[]{shard(r, x, z, gy)};
        double k = r.next();
        int size = k < LARGE_SHARE * core ? 2 : k < LARGE_SHARE * core + SMALL_SHARE ? 0 : 1;
        double height = r.range(MIN_H[size], MAX_H[size]);
        int count = MIN_SPIRES[size] + (int) Math.floor(r.next() * (MAX_SPIRES[size] - MIN_SPIRES[size] + 1));
        Spire[] spires = new Spire[count];
        double spin = r.next() * Math.PI * 2.0;
        double reach = 0.0;
        int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (int s = 0; s < count; s++) {
            // the middle one nearly upright, the others around it pointing outwards in their own directions
            double az = s == 0 ? r.next() * Math.PI * 2.0 : spin + (s - 1) * (Math.PI * 2.0 / (count - 1)) + r.range(-0.5, 0.5);
            double tilt = Math.toRadians(s == 0 ? r.range(0.0, MAIN_TILT) : r.range(SIDE_TILT_MIN, SIDE_TILT_MAX));
            double hs = s == 0 ? height : height * r.range(SIDE_H_MIN, SIDE_H_MAX);
            double rad = Math.max(1.7, hs * r.range(R_MIN, R_MAX));
            double off = s == 0 ? r.range(0.0, 1.0) : r.range(0.5, 1.1) * (rad + spires[0].r);   // feet close together
            double px = x + Math.cos(az) * off, pz = z + Math.sin(az) * off;
            double y0 = gy - SINK - rad * Math.sin(tilt);
            double len = (hs + SINK + rad * Math.sin(tilt)) / Math.cos(tilt);        // along the spire
            double lean = Math.tan(tilt);
            spires[s] = new Spire(px, pz, y0, rad, len * Math.cos(tilt), lean * Math.cos(az), lean * Math.sin(az),
                    4 + (int) Math.floor(r.next() * 4), r.next() * Math.PI * 2.0, r.nextLong());
            reach = Math.max(reach, off + rad * 1.3 + len * Math.sin(tilt) + 2.0);
            bottom = Math.min(bottom, (int) Math.floor(y0) - 1);
            top = Math.max(top, (int) Math.ceil(y0 + len * Math.cos(tilt)) + 1);
        }
        return new Cluster[]{new Cluster(x, z, size, gy, spires, Math.min(reach, MAX_REACH), bottom, top)};
    }

    /** A small single shard, 1 - 4 blocks out of the snow. */
    private static Cluster shard(Rand r, double x, double z, double gy) {
        double hs = r.range(1.5, 4.5), rad = r.range(0.7, 1.3), az = r.next() * Math.PI * 2.0;
        double tilt = Math.toRadians(r.range(0.0, 40.0)), lean = Math.tan(tilt);
        double y0 = gy - 1.5;
        Spire p = new Spire(x, z, y0, rad, hs + 1.5, lean * Math.cos(az), lean * Math.sin(az), 4 + (int) Math.floor(r.next() * 3),
                r.next() * Math.PI * 2.0, r.nextLong());
        return new Cluster(x, z, 0, gy, new Spire[]{p}, Math.min(SHARD_REACH, rad * 1.3 + (hs + 1.5) * lean + 1.0),
                (int) Math.floor(y0) - 1, (int) Math.ceil(y0 + hs + 1.5) + 1);
    }

    // ------------------------------------------------------------------ shape

    /** What the cluster has at block (x, y, z), NONE outside it. */
    public static byte blockAt(Cluster c, int x, int y, int z) {
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        double dx = cx - c.x, dz = cz - c.z;
        if (dx * dx + dz * dz > c.reach * c.reach || y < c.bottomY || y > c.topY) return NONE;
        for (Spire p : c.spires) {
            double t = (cy - p.y0) / p.h;
            if (t < 0.0 || t > 1.0) continue;
            double ex = cx - (p.x + p.leanX * t * p.h), ez = cz - (p.z + p.leanZ * t * p.h);
            if (ex * ex + ez * ez > p.r * p.r * 1.7) continue;             // (quick: far outside it)
            double phi = Math.atan2(ez, ex);
            double rad = p.r * Math.pow(1.0 - t, 0.8) * (1.0 + 0.12 * Math.sin(Math.PI * t));
            rad *= 1.0 + FACETS * (Icebergs.vnoise3(p.salt, Math.cos(phi) * 1.4, Math.sin(phi) * 1.4, cy / 3.0) - 0.5);
            if (rad < 0.35) continue;
            double d = Icebergs.polygon(ex, ez, p.sides, p.turn);
            if (d > rad) continue;
            double rel = d / rad;
            if (t > GLOW_TIP) return GLOW_ICE;                              // glowing tips
            if (rel < SHELL && rad > 1.6) return PACKED_ICE;                // inside
            int band = (int) Math.floor((phi + Math.PI + 1.1 * t) / (Math.PI * 2.0) * 10.0);
            double vein = Icebergs.hash(p.salt, Math.floorMod(band, 10), (int) Math.floor(cy / 20.0), 4);
            if (vein < GLOW_VEINS) return GLOW_ICE;                         // glowing veins up the spire
            if (vein < GLOW_VEINS + BLUE_VEINS) return BLUE_ICE;
            return Icebergs.vnoise3(p.salt + 7, cx / 3.0, cy / 4.0, cz / 3.0) > 0.68 ? PACKED_ICE : ICE;
        }
        return NONE;
    }

    /** First Y of {@link #column}'s array for a column whose ground is at groundY. */
    public static int columnMinY(double groundY) {
        return (int) Math.floor(groundY) - 8;
    }

    /**
     * All clusters of one column together: out[y - columnMinY(ground)] gets the block at Y. Then a little snow on the
     * faces looking up, and under some overhangs a strand of glow ice hanging down (never down to the ground).
     * Returns false if nothing is in this column.
     */
    public static boolean column(List<Cluster> clusters, int x, int z, double groundY, byte[] out) {
        java.util.Arrays.fill(out, NONE);
        int y0 = columnMinY(groundY);
        boolean any = false;
        for (Cluster c : clusters) {
            double dx = x + 0.5 - c.x, dz = z + 0.5 - c.z;
            if (dx * dx + dz * dz > c.reach * c.reach) continue;
            int lo = Math.max(y0, c.bottomY), hi = Math.min(y0 + COLUMN_SIZE - 1, c.topY);
            for (int y = lo; y <= hi; y++) {
                byte m = blockAt(c, x, y, z);
                if (m == NONE) continue;
                int i = y - y0;
                if (out[i] == NONE || m == GLOW_ICE) out[i] = m;            // the glow shows where two meet
                any = true;
            }
        }
        if (!any) return false;
        int groundI = (int) Math.floor(groundY) - y0;
        for (int i = COLUMN_SIZE - 1; i > 0; i--) {
            if (out[i] == NONE) continue;
            int y = y0 + i;
            boolean openAbove = i + 1 >= COLUMN_SIZE || out[i + 1] == NONE;
            if (openAbove && out[i] != GLOW_ICE && Icebergs.vnoise3(0xC0FFEEL, x / 3.0, y / 3.0, z / 3.0) < SNOW_DUST) out[i] = SNOW;
            if (out[i - 1] == NONE && i - 1 > groundI + 2) {                // an overhang: a hanging strand?
                double h = Icebergs.hash(0x51A4DL, x, y, z);
                if (h < STRAND_CHANCE) {
                    int len = 1 + (int) Math.floor(h / STRAND_CHANCE * STRAND_MAX);
                    int k = 1;
                    for (; k <= len && i - k > groundI + 1 && out[i - k] == NONE; k++) out[i - k] = GLOW_ICE;
                    i -= k - 1;                                                 // (go on below the strand)
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** A small random stream (SplitMix64). */
    private static final class Rand {
        private long s;

        Rand(long seed) {
            this.s = seed;
        }

        long nextLong() {
            long z = (this.s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        double next() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        double range(double a, double b) {
            return a + (b - a) * next();
        }
    }
}
