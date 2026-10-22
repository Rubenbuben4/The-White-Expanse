package net.icegiant.worldgen;

/**
 * Ice floes on the Frozen Realm's sea: hexagonal plates, each turned at its own angle, in three sizes,
 * lying in loose clusters with open water between the plates and wide open water between the clusters.
 * <p>
 * Pure function of the world seed and the block column (no chunk access), so floes that cross chunk borders
 * are always whole. How it works: three layers of slanted grids (large, medium, small floes), each turned
 * differently. Each grid cell may hold one floe anywhere inside it; where two floes of the same size would
 * touch, a random one of them stays; a smaller floe is only kept if it does not touch a bigger one.
 * A large-scale noise decides where the clusters are (many floes) and where the sea is open.
 */
public final class IceFloes {

    // ---- TUNE ----
    /** Per size layer: grid cell (blocks), min and max radius (centre to corner, blocks). */
    static final double[] CELL = {22.0, 11.0, 6.0};
    static final double[] MIN_R = {6.0, 2.5, 1.2};
    static final double[] MAX_R = {12.5, 5.2, 2.4};
    static final double GAP = 1.2;                    // open water between two floes (blocks)
    static final double[] GRID_TURN = {0.31, 0.83, 1.27};   // each size uses its own slanted grid (radians)
    static final double CLUSTER_SCALE = 220.0;        // size of the floe clusters / open water areas
    static final double CLUSTER_FROM = 0.42, CLUSTER_TO = 0.68;   // noise -> how full a cluster is
    static final double MAX_FILL = 1.0, MIN_FILL = 0.0;           // chance a cell has a floe (cluster / open)

    /** Result for a column: no ice, floe edge (clear ice) or floe inside (packed ice, may get snow). */
    public static final int NONE = 0, EDGE = 1, INSIDE = 2;

    private IceFloes() {
    }

    public static int at(long seed, int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        for (int layer = 0; layer < CELL.length; layer++) {
            double c = CELL[layer], ang = GRID_TURN[layer];
            double u = px * Math.cos(ang) + pz * Math.sin(ang), v = -px * Math.sin(ang) + pz * Math.cos(ang);
            int gi = (int) Math.floor(u / c), gj = (int) Math.floor(v / c);
            for (int i = gi - 2; i <= gi + 2; i++) {
                for (int j = gj - 2; j <= gj + 2; j++) {
                    double[] f = raw(seed, layer, i, j);
                    if (f == null) continue;
                    double d = hexDistance(px - f[0], pz - f[1], f[3]);
                    double apothem = f[2] * 0.8660254;
                    if (d > apothem || !kept(seed, layer, i, j, f)) continue;
                    return d >= apothem - 1.0 ? EDGE : INSIDE;
                }
            }
        }
        return NONE;
    }

    /**
     * A candidate floe of a grid cell (anywhere inside its cell, so no rows): {x, z, radius, angle, priority},
     * or null if this part of the sea has no floe there.
     */
    static double[] raw(long seed, int layer, int i, int j) {
        double c = CELL[layer], ang = GRID_TURN[layer];
        double r = MIN_R[layer] + (MAX_R[layer] - MIN_R[layer]) * hash(seed, layer, i, j, 1);
        double u = (i + hash(seed, layer, i, j, 2)) * c, v = (j + hash(seed, layer, i, j, 3)) * c;
        double cx = u * Math.cos(ang) - v * Math.sin(ang), cz = u * Math.sin(ang) + v * Math.cos(ang);
        if (hash(seed, layer, i, j, 4) >= cluster(seed, cx, cz)) return null;
        return new double[]{cx, cz, r, hash(seed, layer, i, j, 5) * Math.PI / 3.0, hash(seed, layer, i, j, 6)};
    }

    /** Kept if no bigger floe and no stronger floe of the same size touches it (gap included). */
    static boolean kept(long seed, int layer, int i, int j, double[] f) {
        for (int a = i - 2; a <= i + 2; a++) {                              // same size: the stronger one stays
            for (int b = j - 2; b <= j + 2; b++) {
                if (a == i && b == j) continue;
                double[] o = raw(seed, layer, a, b);
                if (o != null && o[4] > f[4] && Math.hypot(f[0] - o[0], f[1] - o[1]) < f[2] + o[2] + GAP) return false;
            }
        }
        for (int big = 0; big < layer; big++) {                             // smaller floes keep clear of bigger ones
            double c = CELL[big], ang = GRID_TURN[big];
            double u = f[0] * Math.cos(ang) + f[1] * Math.sin(ang), v = -f[0] * Math.sin(ang) + f[1] * Math.cos(ang);
            int gi = (int) Math.floor(u / c), gj = (int) Math.floor(v / c);
            for (int a = gi - 2; a <= gi + 2; a++) {
                for (int b = gj - 2; b <= gj + 2; b++) {
                    double[] o = raw(seed, big, a, b);
                    if (o != null && Math.hypot(f[0] - o[0], f[1] - o[1]) < f[2] + o[2] + GAP && kept(seed, big, a, b, o)) return false;
                }
            }
        }
        return true;
    }

    /** Chance of a floe here: high inside clusters, about zero on the open sea between them. */
    static double cluster(long seed, double x, double z) {
        double n = 0.65 * valueNoise(seed, x / CLUSTER_SCALE, z / CLUSTER_SCALE, 11)
                + 0.35 * valueNoise(seed, x / (CLUSTER_SCALE * 0.4), z / (CLUSTER_SCALE * 0.4), 12);
        double t = Math.max(0.0, Math.min(1.0, (n - CLUSTER_FROM) / (CLUSTER_TO - CLUSTER_FROM)));
        return MIN_FILL + (MAX_FILL - MIN_FILL) * t * t * (3.0 - 2.0 * t);
    }

    /** Distance measure of a hexagon (equals the apothem on its edges), rotated by angle. */
    static double hexDistance(double dx, double dz, double angle) {
        double cs = Math.cos(angle), sn = Math.sin(angle);
        double u = Math.abs(dx * cs + dz * sn), v = Math.abs(-dx * sn + dz * cs);
        return Math.max(v, u * 0.8660254 + v * 0.5);
    }

    private static double valueNoise(long seed, double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(seed, salt, ix, iz, 0), b = hash(seed, salt, ix + 1, iz, 0);
        double c = hash(seed, salt, ix, iz + 1, 0), d = hash(seed, salt, ix + 1, iz + 1, 0);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    private static double hash(long seed, int layer, int i, int j, int k) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ ((long) layer << 40) ^ ((long) k << 52);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }
}
