package net.icegiant.worldgen;

/**
 * Where a river runs into a lake (plain maths, no blocks - RiverFeature builds with it, and it can be checked
 * outside the game):
 * <ul>
 *   <li>{@link #lakeBed}: the river's bed goes on into the lake (as deep as the river - no shallow step or gravel
 *       bank at the mouth), and every lake column has at least 1 block of water (no dry gravel at the water line);</li>
 *   <li>{@link #inflowClear}: no ice near the river's water - its current pushes the ice away.</li>
 * </ul>
 * Also the shared hash / value noise and the ice rules of the lakes.
 */
public final class RiverMouths {

    // ---- TUNE ----
    static final int INFLOW_CLEAR = 6;                // no ice this close to the river's water
    static final double ICE_FRINGE = 0.45;            // share of calm, shallow (1 block) water edges that are frozen
    static final double TARN_FROM_Y = 115.0, TARN_FULL_Y = 175.0;   // lakes start to freeze .. are frozen over
    static final double TARN_ICE_SIZE = 7.0;                         // size of the ice sheets / open leads (blocks)

    private RiverMouths() {
    }

    /** Bed of a lake column whose ground lies at or below the lake level (riverDepth > 0: the river's channel). */
    public static int lakeBed(int top, int wl, int riverDepth) {
        return riverDepth > 0 ? Math.min(top, wl - riverDepth) : Math.min(top, wl - 1);
    }

    /**
     * Columns kept free of ice by the inflow: lake columns within {@value #INFLOW_CLEAR} blocks of river water (the
     * river itself or its channel on in the lake), and river columns within that distance of the lake. From the
     * river plan only, so the same in every chunk.
     */
    public static boolean[] inflowClear(RiverNetwork net, RiverNetwork.ChunkData d, int x0, int z0) {
        boolean[] out = new boolean[256];
        int r = INFLOW_CLEAR, r2 = r * r, cx = Math.floorDiv(x0, 16), cz = Math.floorDiv(z0, 16);
        for (int i = 0; i < 256; i++) {
            boolean lake = d.kind[i] == RiverNetwork.ChunkData.LAKE;
            if (!lake && d.kind[i] != RiverNetwork.ChunkData.CHANNEL) continue;
            int x = x0 + i / 16, z = z0 + i % 16;
            search:
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    if (a * a + b * b > r2) continue;
                    int px = x + a, pz = z + b;
                    int pcx = Math.floorDiv(px, 16), pcz = Math.floorDiv(pz, 16);
                    RiverNetwork.ChunkData nd = pcx == cx && pcz == cz ? d : net.chunk(pcx, pcz);
                    int n = Math.floorMod(px, 16) * 16 + Math.floorMod(pz, 16);
                    byte k = nd.kind[n];
                    boolean hit = lake ? k == RiverNetwork.ChunkData.CHANNEL || (k == RiverNetwork.ChunkData.LAKE && nd.depth[n] > 0)
                            : k == RiverNetwork.ChunkData.LAKE;
                    if (hit) {
                        out[i] = true;
                        break search;
                    }
                }
            }
        }
        return out;
    }

    static final int MOUTH_TAPER = 3;                 // beside the river's channel the lake bed rises 1 block per block

    /**
     * Depth the bed of each lake column must have at least: the river's channel depth inside it (d.depth), and right
     * beside the channel a slope up from it (1 block per block, {@value #MOUTH_TAPER} at most) - so the river's bed
     * runs on into the lake without a step. 0 = no river here (the lake's own ground).
     */
    public static int[] mouthDepth(RiverNetwork net, RiverNetwork.ChunkData d, int x0, int z0) {
        int[] out = new int[256];
        int r = MOUTH_TAPER, cx = Math.floorDiv(x0, 16), cz = Math.floorDiv(z0, 16);
        for (int i = 0; i < 256; i++) {
            if (d.kind[i] != RiverNetwork.ChunkData.LAKE) continue;
            int x = x0 + i / 16, z = z0 + i % 16, best = d.depth[i];
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    if (a == 0 && b == 0) continue;
                    int px = x + a, pz = z + b;
                    int pcx = Math.floorDiv(px, 16), pcz = Math.floorDiv(pz, 16);
                    RiverNetwork.ChunkData nd = pcx == cx && pcz == cz ? d : net.chunk(pcx, pcz);
                    int n = Math.floorMod(px, 16) * 16 + Math.floorMod(pz, 16);
                    byte k = nd.kind[n];
                    if (k != RiverNetwork.ChunkData.CHANNEL && !(k == RiverNetwork.ChunkData.LAKE && nd.depth[n] > 0)) continue;
                    int want = nd.depth[n] - (int) Math.ceil(Math.sqrt(a * a + b * b)) + 1;   // next to it: 1 less
                    if (want > best) best = want;
                }
            }
            out[i] = best;
        }
        return out;
    }

    static final int SHORE_SEARCH = 3;                // a lake shore looks this far for the lake's water

    /**
     * How many blocks a lake shore column lies above the water (RiverFeature#shore, since 2026-10-22): 0 right at the
     * water, also on the diagonal (a beach flush with it: one walks out), then 1 more per block. dist: blocks to the
     * nearest column of the lake's water, negative if there is none within {@value #SHORE_SEARCH}.
     */
    public static int shoreStep(double dist) {
        if (dist < 0.0) return SHORE_SEARCH;
        return Math.max(0, Math.min(SHORE_SEARCH, (int) Math.floor(dist + 0.5) - 1));
    }

    /** Thin ice on a calm lake edge only 1 block deep (some of them). */
    public static boolean fringeIce(int x, int wl, int z, int depth) {
        return depth <= 1 && hash(x >> 1, wl, z >> 1) < ICE_FRINGE;
    }

    /** 0 (low lakes: open) .. 1 (high tarns: frozen over). */
    public static double tarnFreeze(int wl) {
        return Math.max(0.0, Math.min(1.0, (wl - TARN_FROM_Y) / (TARN_FULL_Y - TARN_FROM_Y)));
    }

    /** Ice sheet (true) or open water at this spot of a lake at this height. */
    public static boolean frozenOver(int x, int wl, int z) {
        double t = tarnFreeze(wl);
        if (t <= 0.0) return false;
        double n = 0.75 * smoothNoise(x / TARN_ICE_SIZE, z / TARN_ICE_SIZE, 41)
                + 0.25 * smoothNoise(x / (TARN_ICE_SIZE * 0.35), z / (TARN_ICE_SIZE * 0.35), 42);
        return n <= t * 1.15;                                                    // else an open lead
    }

    public static double smoothNoise(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, salt, iz), b = hash(ix + 1, salt, iz), c = hash(ix, salt, iz + 1), d = hash(ix + 1, salt, iz + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    public static double hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ 0x2F1A5L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535.0;
    }
}
