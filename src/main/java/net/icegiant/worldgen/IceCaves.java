package net.icegiant.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * The Eishöhlen (ice caves) biome of the Frozen Realm (Rubenbuben4, 2026-10-24): huge caverns in the upper underground
 * (where vanilla puts the lush caves), their walls partly packed and blue ice, full of the Kristallspitzen ice spires -
 * standing on the floor and hanging from the ceiling.
 * <p>
 * Pure function of the world seed and the block position (no Minecraft classes) like CrystalSpires: whether a block is
 * cavern, what a wall block becomes, and where the spires stand. How much of the biome is around a block (0..1, from
 * the biome grid, smooth) comes in from IceCavesFeature, so the caverns shrink towards the biome's edge and stop there.
 */
public final class IceCaves {

    // ---- TUNE: caverns ----
    static final double BIG_XZ = 54.0, BIG_Y = 22.0;      // size of the big chambers (blocks)
    static final double SMALL_XZ = 16.0, SMALL_Y = 8.0;   // the rough walls
    static final double BIG_SHARE = 0.74;
    static final double OPEN = 0.5;                       // cavern where the noise is above this ...
    static final double EDGE = 0.5;                       // ... plus this much at the biome's edge (closes them there)
    public static final int ROOF = 14;                    // rock kept under the ground surface (rivers dig ~10)
    // ---- TUNE: walls ----
    static final double BLUE_WALL = 0.16, PACKED_WALL = 0.5;   // (noise tail) wall blocks: blue ice / packed ice / rest stone
    // ---- TUNE: spires ----
    static final double FLOOR_CELL = 6.0, FLOOR_CHANCE = 0.85;  // standing spires: at most one cluster per cell
    static final double CEIL_CELL = 6.0, CEIL_CHANCE = 0.75;   // hanging spires
    static final double FLOOR_MIN_H = 3.0, FLOOR_MAX_H = 26.0, FLOOR_ROOM = 0.65;  // height; at most this x the room
    static final double CEIL_MIN_H = 3.0, CEIL_MAX_H = 18.0, CEIL_ROOM = 0.5;
    static final int MIN_ROOM = 5;                        // only in rooms at least this high
    public static final double REACH = 22.0;              // spires reach at most this far from their middle

    public static final byte STONE = 0, PACKED_ICE = 1, BLUE_ICE = 2;

    private IceCaves() {
    }

    /** The cavern noise, 0..1. */
    static double noise(long seed, double x, double y, double z) {
        double big = Icebergs.vnoise3(seed ^ 0x1CECA7E5L, x / BIG_XZ, y / BIG_Y, z / BIG_XZ);
        double small = Icebergs.vnoise3(seed ^ 0x5EA1EDL, x / SMALL_XZ, y / SMALL_Y, z / SMALL_XZ);
        return BIG_SHARE * big + (1.0 - BIG_SHARE) * small;
    }

    /** Is block (x, y, z) cavern? {@code biome}: how much of the Eishöhlen biome is here (0 = none, 1 = inside). */
    public static boolean cavern(long seed, int x, int y, int z, double biome) {
        if (biome <= 0.0) return false;
        return noise(seed, x + 0.5, y + 0.5, z + 0.5) > OPEN + EDGE * (1.0 - biome);
    }

    /** What a rock block next to the cavern becomes. */
    public static byte wall(long seed, int x, int y, int z) {
        if (Icebergs.vnoise3(seed ^ 0xB1E1CEL, x / 5.0, y / 4.0, z / 5.0) > 1.0 - BLUE_WALL) return BLUE_ICE;   // patches
        return Icebergs.vnoise3(seed ^ 0x1CEA11L, x / 6.0, y / 4.0, z / 6.0) > 1.0 - PACKED_WALL ? PACKED_ICE : STONE;
    }

    // ------------------------------------------------------------------ spires

    /** Which blocks of a column are cavern - from the caller (it knows the biome and the ground surface). */
    public interface Column {
        /** Cavern at Y? Only asked for Y in [minY, maxY]. */
        boolean cavern(int x, int y, int z);

        int minY();

        int maxY(int x, int z);
    }

    /** A spire cluster in a cave: standing on a floor, or hanging from a ceiling (then mirrored at {@code mirror}). */
    public record CaveCluster(CrystalSpires.Cluster cluster, boolean hanging, int mirror) {
        /** The Y range it can have blocks in. */
        public int bottomY() {
            return this.hanging ? this.mirror - this.cluster.topY() : this.cluster.bottomY();
        }

        public int topY() {
            return this.hanging ? this.mirror - this.cluster.bottomY() : this.cluster.topY();
        }

        /** CrystalSpires material at (x, y, z), CrystalSpires.NONE outside. */
        public byte blockAt(int x, int y, int z) {
            return CrystalSpires.blockAt(this.cluster, x, this.hanging ? this.mirror - y : y, z);
        }
    }

    /** All cave spire clusters that may reach into the square x0..x1, z0..z1. */
    public static List<CaveCluster> near(long seed, int x0, int z0, int x1, int z1, Column column) {
        List<CaveCluster> out = new ArrayList<>(16);
        collect(seed, x0, z0, x1, z1, column, false, out);
        collect(seed, x0, z0, x1, z1, column, true, out);
        return out;
    }

    private static void collect(long seed, int x0, int z0, int x1, int z1, Column column, boolean hanging,
                                List<CaveCluster> out) {
        double cell = hanging ? CEIL_CELL : FLOOR_CELL;
        int i0 = (int) Math.floor((x0 - REACH) / cell), i1 = (int) Math.floor((x1 + REACH) / cell);
        int j0 = (int) Math.floor((z0 - REACH) / cell), j1 = (int) Math.floor((z1 + REACH) / cell);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                CaveCluster c = cell(seed, i, j, hanging, column);
                if (c == null) continue;
                CrystalSpires.Cluster k = c.cluster();
                if (k.x() + k.reach() < x0 || k.x() - k.reach() > x1 + 1 || k.z() + k.reach() < z0 || k.z() - k.reach() > z1 + 1) continue;
                out.add(c);
            }
        }
    }

    private static CaveCluster cell(long seed, int i, int j, boolean hanging, Column column) {
        long salt = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (hanging ? 0xCE11L : 0xF100L);
        if (Icebergs.hash(salt, 1, 2, 3) >= (hanging ? CEIL_CHANCE : FLOOR_CHANCE)) return null;
        double cell = hanging ? CEIL_CELL : FLOOR_CELL;
        int x = (int) Math.floor((i + 0.15 + 0.7 * Icebergs.hash(salt, 4, 5, 6)) * cell);
        int z = (int) Math.floor((j + 0.15 + 0.7 * Icebergs.hash(salt, 7, 8, 9)) * cell);
        // the rooms of this column, from the top: a floor (cavern above rock) or a ceiling (cavern under rock)
        int top = column.maxY(x, z), bottom = column.minY();
        List<int[]> rooms = new ArrayList<>(4);                // {floorY (first cavern block), ceilingY (first rock above)}
        int roomTop = Integer.MIN_VALUE;
        boolean above = false;
        for (int y = top; y >= bottom; y--) {
            boolean open = column.cavern(x, y, z);
            if (open && !above) roomTop = y + 1;
            if (!open && above) rooms.add(new int[]{y + 1, roomTop});
            above = open;
        }
        rooms.removeIf(r -> r[1] - r[0] < MIN_ROOM || r[1] > top);
        if (rooms.isEmpty()) return null;
        int[] room = rooms.get((int) Math.floor(Icebergs.hash(salt, 10, 11, 12) * rooms.size()));
        int high = room[1] - room[0];
        double k = Math.pow(Icebergs.hash(salt, 13, 14, 15), 1.4);      // more small ones than big ones
        int count = 1 + (int) Math.floor(Icebergs.hash(salt, 16, 17, 18) * 4.0);
        if (hanging) {
            double h = Math.min(high * CEIL_ROOM, CEIL_MIN_H + k * (CEIL_MAX_H - CEIL_MIN_H));
            if (h < CEIL_MIN_H) return null;
            // mirrored: y' = mirror - y puts the ceiling's first rock block (room[1]) just under the "ground" at room[1]
            int mirror = 2 * room[1] - 1;
            return new CaveCluster(CrystalSpires.caveCluster(salt, x + 0.5, z + 0.5, room[1], h, count), true, mirror);
        }
        double h = Math.min(high * FLOOR_ROOM, FLOOR_MIN_H + k * (FLOOR_MAX_H - FLOOR_MIN_H));
        if (h < FLOOR_MIN_H) return null;
        return new CaveCluster(CrystalSpires.caveCluster(salt, x + 0.5, z + 0.5, room[0], h, count), false, 0);
    }
}
