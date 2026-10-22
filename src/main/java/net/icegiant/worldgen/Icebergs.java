package net.icegiant.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icebergs on the Frozen Realm's open sea.
 * <p>
 * v1 (Rubenbuben4, 2026-10-21, after his two pictures): lone icebergs, a lumpy base with tall pointed spires that all
 * lean the same way like a frozen wave.
 * <p>
 * v2 (Rubenbuben4, 2026-10-21: "mach die eher wie in einem biome wo ganz viele von ihnen sind und auch in einander
 * sind und schoene"): iceberg FIELDS - big patches of the deep sea (about a third of it) crowded with icebergs that
 * grow into each other, with small lumps of ice (growlers) floating between them. Outside the fields only now and
 * then a lone one. Four kinds:
 * <ul>
 * <li>SHARDS - pointed, faceted spires leaning like a frozen wave (his first picture),</li>
 * <li>PILLARS - rounded columns of different heights with snow caps (his second picture),</li>
 * <li>TABLE - flat ice tables with steep walls and cracks in the top, or rounded domes,</li>
 * <li>GROWLER - the small lumps between them.</li>
 * </ul>
 * Ice in coherent layers: packed ice inside, wavy tilted bands of blue ice in the walls and roots, blue streaks up the
 * spires, snow on everything that faces the sky, clear ice under overhangs.
 * <p>
 * Pure function of the world seed and the block position (no chunk access): every chunk builds its own columns
 * ({@link #column}) and icebergs that cross chunk borders are whole. The sea floor comes in from outside (the terrain
 * maths), so this class has no Minecraft classes and test/IcebergsTest can check it.
 */
public final class Icebergs {

    // ---- TUNE: where ----
    static final double FIELD_SIZE = 760.0, FIELD_DETAIL = 260.0; // iceberg fields: patches of this size ...
    static final double FIELD_FROM = 0.56, FIELD_FULL = 0.64;     // ... where the field noise is above this
    static final double CELL = 44.0;                              // at most one iceberg (and a twin) per cell
    static final double FIELD_CHANCE = 0.85, LONE_CHANCE = 0.03;  // chance per cell inside / outside a field
    static final double TWIN_CHANCE = 0.45;                       // in a field: a second one grown into the first
    static final double GROWLER_CELL = 13.0, GROWLER_CHANCE = 0.3;  // small lumps of ice (only in the fields)
    static final double[] MIN_DEPTH = {14.0, 22.0, 32.0};         // small / medium / giant: water this deep below ...
    static final double CLEAR_WATER = 6.0;                        // ... and this deep all around (no coast, no shallows)
    // ---- TUNE: which ----
    public static final int SHARDS = 0, PILLARS = 1, TABLE = 2, GROWLER = 3;
    static final double[] FIELD_KINDS = {0.42, 0.30, 0.28};       // shards / pillars / tables in a field
    static final double[] LONE_KINDS = {0.55, 0.30, 0.15};        // ... and alone
    static final double FIELD_GIANT = 0.06, FIELD_SMALL = 0.38, LONE_GIANT = 0.10, LONE_SMALL = 0.25;
    // ---- TUNE: size per kind [kind][small, medium, giant] ----
    static final double[][] MIN_H = {{9, 16, 40}, {8, 15, 32}, {3, 5, 9}};     // highest point above the water
    static final double[][] MAX_H = {{15, 30, 60}, {14, 28, 48}, {5, 9, 15}};
    static final double[][] MIN_R = {{5, 8, 14}, {4, 7, 11}, {7, 11, 18}};     // radius of the base at the water line
    static final double[][] MAX_R = {{8, 13, 21}, {7, 11, 16}, {10, 17, 28}};
    static final double[] MIN_BASE_H = {1, 2, 3}, MAX_BASE_H = {3, 4, 6};       // base of shards / pillars above water
    static final int[] MIN_SPIRES = {3, 4, 6}, MAX_SPIRES = {5, 7, 10};         // spires of the shards
    static final int[] MIN_COLUMNS = {3, 4, 6}, MAX_COLUMNS = {5, 7, 10};       // columns of the pillars
    // ---- TUNE: shape ----
    static final double MAIN_R = 0.46, SIDE_R_MIN = 0.2, SIDE_R_MAX = 0.34;     // spire radius (x base radius)
    static final double SIDE_H_MIN = 0.35, SIDE_H_MAX = 0.9;                    // side spires (x height of the main one)
    static final double LEAN_MIN = 18.0, LEAN_MAX = 36.0;                       // lean of the spires (degrees)
    static final double CURVE_MIN = 0.3, CURVE_MAX = 1.1;                       // spires bend over more at the top
    static final double FACETS = 0.22;                                          // spires: uneven, faceted faces
    static final double COLUMN_R_MIN = 0.24, COLUMN_R_MAX = 0.42;               // pillar columns (x base radius)
    static final double COLUMN_LEAN_MAX = 7.0;                                  // (degrees, nearly upright)
    static final double ROOT_DEPTH = 0.85, TABLE_ROOT_DEPTH = 2.0;              // root below the water (x height)
    static final double ROOT_WIDER = 1.25;                                      // the root bulges out under the water
    static final double TABLE_FLAT_SHARE = 0.6;                                 // tables: flat (rest: domes)
    static final double CRACK_DEPTH = 4.0, CRACK_WIDTH = 0.045;                 // cracks in the flat tables
    // ---- TUNE: look ----
    static final double SHELL = 0.62;                     // outer part of a spire (rel. radius) that shows streaks / snow
    static final double SNOW_FACE = 0.15;                 // spire faces looking this much into the lean get snow
    static final double STREAK_SHARE = 0.3;               // share of blue streaks up the spires and columns
    static final double STRATA_STEP = 7.0, STRATA_BLUE = 0.2;   // bands of blue ice in the walls and roots
    static final double CLEAR_PATCH = 0.8;                // clear ice in patches where a 3D noise is above this

    public static final int SEA_Y = 63;                   // first air block above the sea
    public static final byte NONE = 0, PACKED_ICE = 1, BLUE_ICE = 2, ICE = 3, SNOW = 4;
    private static final byte STEEP = 0x10;               // (flag: a spire's face - only dusted with snow, see column)
    static final double DUST = 0.45;                      // share of the spires' sky faces that get snow
    /** {@link #column} fills Y COLUMN_MIN_Y .. COLUMN_MIN_Y + COLUMN_SIZE - 1. */
    public static final int COLUMN_MIN_Y = SEA_Y - 1 - 66, COLUMN_SIZE = 66 + 1 + 66;

    static final double MAX_REACH = 135.0;                // farthest any block of a cell's icebergs can be from the cell
    static final double GROWLER_REACH = 12.0;

    /** The sea floor (surface Y of the terrain) at a column. */
    public interface Floor {
        double y(double x, double z);
    }

    /**
     * One spire or column: foot, size, lean (offset of the tip per block of height), cross-section, curve, and cap
     * (0: a pointed shard; more: a rounded column with a cap this high).
     */
    record Spire(double x, double z, double y0, double r, double h, double leanX, double leanZ,
                 int sides, double turn, double curve, double cap) {
    }

    /**
     * One iceberg; reach = how far from (x, z) any of its blocks can be. along / across: the outline's half axes
     * (x base radius), lumps: its waves; profile: how flat the top of the base is (small = flat with steep walls).
     */
    public record Berg(double x, double z, int kind, int size, double height, double baseR, double baseH,
                       double profile, double rootDepth, double dirX, double dirZ, double along, double across,
                       double lumpAmp, double[] lumps, double tiltX, double tiltZ, Spire[] spires, double reach,
                       long salt) {

        public int bottomY() {
            return (int) Math.floor(SEA_Y - 1 - this.rootDepth) - 1;
        }

        public int topY() {
            return (int) Math.ceil(SEA_Y + this.height) + 2;
        }
    }

    private static final ConcurrentHashMap<Long, Berg[]> CACHE = new ConcurrentHashMap<>();
    private static final Berg[] EMPTY = new Berg[0];

    private Icebergs() {
    }

    // ------------------------------------------------------------------ where

    /** 0..1: how much of an iceberg field is here (1 = in the middle of one). */
    public static double field(long seed, double x, double z) {
        double n = 0.7 * vnoise(seed + 71, x / FIELD_SIZE, z / FIELD_SIZE)
                + 0.3 * vnoise(seed + 72, x / FIELD_DETAIL, z / FIELD_DETAIL);
        return smooth(FIELD_FROM, FIELD_FULL, n);
    }

    /** All icebergs (and growlers) that may reach into the square x0..x1, z0..z1. */
    public static List<Berg> near(long seed, int x0, int z0, int x1, int z1, Floor floor) {
        List<Berg> out = new ArrayList<>(8);
        collect(seed, x0, z0, x1, z1, floor, CELL, MAX_REACH, 0, out);
        collect(seed, x0, z0, x1, z1, floor, GROWLER_CELL, GROWLER_REACH, 1, out);
        return out;
    }

    private static void collect(long seed, int x0, int z0, int x1, int z1, Floor floor, double cell, double reach,
                                int tag, List<Berg> out) {
        int i0 = (int) Math.floor((x0 - reach) / cell), i1 = (int) Math.floor((x1 + reach) / cell);
        int j0 = (int) Math.floor((z0 - reach) / cell), j1 = (int) Math.floor((z1 + reach) / cell);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                for (Berg b : cell(seed, i, j, tag, floor)) {
                    if (b.x + b.reach < x0 || b.x - b.reach > x1 + 1 || b.z + b.reach < z0 || b.z - b.reach > z1 + 1) continue;
                    out.add(b);
                }
            }
        }
    }

    private static Berg[] cell(long seed, int i, int j, int tag, Floor floor) {
        long key = seed * 0x632BE59BD9B4E019L ^ ((long) i << 32) ^ (j & 0xffffffffL) ^ ((long) tag << 62);
        Berg[] v = CACHE.get(key);
        if (v != null) return v;
        v = tag == 0 ? make(seed, i, j, floor) : growler(seed, i, j, floor);
        if (CACHE.size() > 65536) CACHE.clear();
        CACHE.put(key, v);
        return v;
    }

    /** The icebergs of a cell: none, one, or one with a twin grown into it. */
    static Berg[] make(long seed, int i, int j, Floor floor) {
        Rand r = new Rand(seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ 0x1CEBE46L);
        double x = (i + 0.1 + 0.8 * r.next()) * CELL, z = (j + 0.1 + 0.8 * r.next()) * CELL;
        double f = field(seed, x, z);
        if (r.next() >= LONE_CHANCE + (FIELD_CHANCE - LONE_CHANCE) * f) return EMPTY;
        int size = pickSize(r.next(), f);
        int kind = pickKind(r.next(), f);
        Berg a = build(r, kind, size, x, z, floor);
        if (a == null) return EMPTY;
        if (r.next() >= TWIN_CHANCE * f) return new Berg[]{a};
        // the twin: another one (smaller or as big) whose base runs into the first one's
        int size2 = Math.min(size, pickSize(r.next(), f));
        int kind2 = r.next() < 0.5 ? kind : pickKind(r.next(), f);
        double ang = r.next() * Math.PI * 2.0;
        double guessR = 0.5 * (MIN_R[kind2][size2] + MAX_R[kind2][size2]);
        double d = (a.baseR * outlineScale(a) * 0.8 + guessR) * r.range(0.45, 0.75);
        Berg b = build(r, kind2, size2, x + d * Math.cos(ang), z + d * Math.sin(ang), floor);
        return b == null ? new Berg[]{a} : new Berg[]{a, b};
    }

    private static int pickSize(double k, double f) {
        double giant = lerp(LONE_GIANT, FIELD_GIANT, f), small = lerp(LONE_SMALL, FIELD_SMALL, f);
        return k < giant ? 2 : k < giant + small ? 0 : 1;
    }

    private static int pickKind(double k, double f) {
        double shards = lerp(LONE_KINDS[0], FIELD_KINDS[0], f), pillars = lerp(LONE_KINDS[1], FIELD_KINDS[1], f);
        return k < shards ? SHARDS : k < shards + pillars ? PILLARS : TABLE;
    }

    /** One iceberg of this kind and size at (x, z), or null if the water there is too shallow / too close to land. */
    private static Berg build(Rand r, int kind, int size, double x, double z, Floor floor) {
        double height = r.range(MIN_H[kind][size], MAX_H[kind][size]);
        double baseR = r.range(MIN_R[kind][size], MAX_R[kind][size]);
        double ang = r.next() * Math.PI * 2.0;
        double dirX = Math.cos(ang), dirZ = Math.sin(ang);
        double[] lumps = new double[3];
        for (int n = 0; n < 3; n++) lumps[n] = r.next();
        double tilt = r.range(0.0, 0.3), tiltAng = r.next() * Math.PI * 2.0;
        long salt = r.nextLong();
        double along, across, lumpAmp, baseH, profile, rootDepth;
        if (kind == TABLE) {
            along = r.range(1.1, 1.6);
            across = 1.0 / Math.pow(along, 0.7);
            lumpAmp = 1.35;                                   // notched, broken-off edges
            baseH = height;
            profile = r.next() < TABLE_FLAT_SHARE ? r.range(0.08, 0.16) : r.range(0.55, 0.85);
            rootDepth = TABLE_ROOT_DEPTH * height * r.range(0.8, 1.2);
        } else {
            along = 1.25;
            across = 0.85;
            lumpAmp = 1.0;
            baseH = r.range(MIN_BASE_H[size], MAX_BASE_H[size]);
            profile = 0.6;
            rootDepth = ROOT_DEPTH * height * r.range(0.85, 1.15);
        }
        Berg shape = new Berg(x, z, kind, size, height, baseR, baseH, profile, rootDepth, dirX, dirZ, along, across,
                lumpAmp, lumps, tilt * Math.cos(tiltAng), tilt * Math.sin(tiltAng), new Spire[0], 0.0, salt);
        Spire[] spires = kind == SHARDS ? shards(r, shape, ang) : kind == PILLARS ? pillars(r, shape) : new Spire[0];
        double reach = baseR * outlineScale(shape) * ROOT_WIDER;
        for (Spire p : spires) {
            double lean = Math.hypot(p.leanX, p.leanZ);
            reach = Math.max(reach, Math.hypot(p.x - x, p.z - z) + p.r * 1.3 + p.h * lean + 2.0);
        }

        // only in deep, open water: deep below the middle, no shallows or land anywhere near
        double floorY = floor.y(x, z);
        if (floorY > SEA_Y - 1 - MIN_DEPTH[size]) return null;
        for (int n = 0; n < 12; n++) {
            double a = n * Math.PI / 6.0, rr = reach + 8.0;
            if (floor.y(x + rr * Math.cos(a), z + rr * Math.sin(a)) > SEA_Y - 1 - CLEAR_WATER) return null;
            if (floor.y(x + 0.5 * rr * Math.cos(a), z + 0.5 * rr * Math.sin(a)) > SEA_Y - 1 - CLEAR_WATER) return null;
        }
        rootDepth = Math.min(rootDepth, SEA_Y - 1 - floorY - 3.0);   // (the root never stands on the floor)
        return new Berg(x, z, kind, size, height, baseR, baseH, profile, rootDepth, dirX, dirZ, along, across,
                lumpAmp, lumps, shape.tiltX, shape.tiltZ, spires, reach, salt);
    }

    /** Pointed spires in a fan: the main one at the back, the others beside and in front of it, lower further out. */
    private static Spire[] shards(Rand r, Berg b, double ang) {
        int size = b.size;
        int count = MIN_SPIRES[size] + (int) Math.floor(r.next() * (MAX_SPIRES[size] - MIN_SPIRES[size] + 1));
        Spire[] spires = new Spire[count];
        double leanAll = r.range(LEAN_MIN, LEAN_MAX), curveAll = r.range(CURVE_MIN, CURVE_MAX);
        for (int s = 0; s < count; s++) {
            double side = s == 0 ? 0.0 : (s % 2 == 1 ? 1 : -1) * (0.25 + 0.75 * ((s + 1) / 2) / (double) Math.max(1, count / 2));
            double u = s == 0 ? -0.3 : r.range(-0.5, 0.3);
            double w = s == 0 ? r.range(-0.08, 0.08) : side * r.range(0.55, 0.95);
            double far = Math.min(1.0, Math.hypot(u + 0.3, w) / 0.9);
            double hs = s == 0 ? b.height : b.height * lerp(SIDE_H_MAX, SIDE_H_MIN, Math.min(1.0, far * 0.8 + 0.35 * r.next()));
            double px = b.x + (u * b.dirX - w * b.dirZ) * b.baseR, pz = b.z + (u * b.dirZ + w * b.dirX) * b.baseR;
            double rr = b.baseR * (s == 0 ? MAIN_R * r.range(0.9, 1.1) : r.range(SIDE_R_MIN, SIDE_R_MAX));
            rr = Math.min(rr, hs * 0.38);                     // slim enough to be a spire
            double lean = Math.toRadians(leanAll + 8.0 * (r.next() - 0.5));
            double turnDir = ang + Math.toRadians(24.0 * (r.next() - 0.5));
            double t = Math.tan(lean);
            int sides = 4 + (int) Math.floor(r.next() * 4);
            double y0 = baseTop(b, px, pz) - 2.0;
            spires[s] = new Spire(px, pz, y0, rr, SEA_Y + hs - y0, t * Math.cos(turnDir), t * Math.sin(turnDir), sides,
                    r.next() * Math.PI * 2.0, curveAll * r.range(0.8, 1.2), 0.0);
        }
        return spires;
    }

    /** Rounded columns of different heights standing close together, the highest near the middle. */
    private static Spire[] pillars(Rand r, Berg b) {
        int size = b.size;
        int count = MIN_COLUMNS[size] + (int) Math.floor(r.next() * (MAX_COLUMNS[size] - MIN_COLUMNS[size] + 1));
        Spire[] cols = new Spire[count];
        for (int s = 0; s < count; s++) {
            double a = r.next() * Math.PI * 2.0, d = s == 0 ? 0.0 : 0.2 + 0.45 * Math.sqrt(r.next());
            double px = b.x + Math.cos(a) * d * b.baseR * b.along, pz = b.z + Math.sin(a) * d * b.baseR * b.across;
            double hs = s == 0 ? b.height : b.height * r.range(0.4, 0.95) * (1.0 - 0.4 * d);
            double rr = Math.min(b.baseR * r.range(COLUMN_R_MIN, COLUMN_R_MAX), hs * 0.45);
            double lean = Math.tan(Math.toRadians(r.range(0.0, COLUMN_LEAN_MAX))), turnDir = r.next() * Math.PI * 2.0;
            double y0 = baseTop(b, px, pz) - 2.0;
            cols[s] = new Spire(px, pz, y0, rr, SEA_Y + hs - y0, lean * Math.cos(turnDir), lean * Math.sin(turnDir), 0,
                    r.next() * Math.PI * 2.0, 0.0, rr * r.range(0.6, 1.0));
        }
        return cols;
    }

    /** The small lumps of ice that float between the icebergs of a field. */
    static Berg[] growler(long seed, int i, int j, Floor floor) {
        Rand r = new Rand(seed ^ (i * 0xC2B2AE3D27D4EB4FL) ^ (j * 0x9E3779B97F4A7C15L) ^ 0x6F0A1E5L);
        double x = (i + 0.1 + 0.8 * r.next()) * GROWLER_CELL, z = (j + 0.1 + 0.8 * r.next()) * GROWLER_CELL;
        double f = field(seed, x, z);
        if (f <= 0.0 || r.next() >= GROWLER_CHANCE * f) return EMPTY;
        double baseR = r.range(1.5, 4.5), baseH = r.range(0.3, 2.5);
        double ang = r.next() * Math.PI * 2.0;
        double[] lumps = {r.next(), r.next(), r.next()};
        double along = r.range(0.9, 1.4);
        Berg g = new Berg(x, z, GROWLER, 0, baseH, baseR, baseH, 0.5, 1.8 * (baseH + 1.0) + 0.5 * baseR,
                Math.cos(ang), Math.sin(ang), along, 0.9, 1.0, lumps, 0.0, 0.0, new Spire[0], 0.0, r.nextLong());
        double reach = baseR * outlineScale(g) * ROOT_WIDER;
        if (floor.y(x, z) > SEA_Y - 1 - 8.0) return EMPTY;
        for (int n = 0; n < 6; n++) {
            double a = n * Math.PI / 3.0;
            if (floor.y(x + (reach + 3.0) * Math.cos(a), z + (reach + 3.0) * Math.sin(a)) > SEA_Y - 1 - CLEAR_WATER) return EMPTY;
        }
        return new Berg[]{new Berg(x, z, GROWLER, 0, baseH, baseR, baseH, 0.5, g.rootDepth, g.dirX, g.dirZ, along, 0.9,
                1.0, lumps, 0.0, 0.0, g.spires, reach, g.salt)};
    }

    // ------------------------------------------------------------------ shape

    /** Widest outline of the base (x base radius). */
    static double outlineScale(Berg b) {
        return Math.max(b.along, b.across) * (1.0 + 0.27 * b.lumpAmp);
    }

    /** Outline radius of the base in the direction (dx, dz) from its middle. */
    private static double outline(Berg b, double dx, double dz) {
        double u = dx * b.dirX + dz * b.dirZ, v = -dx * b.dirZ + dz * b.dirX;
        double phi = Math.atan2(v, u);
        double[] l = b.lumps;
        double lump = b.lumpAmp * (0.12 * Math.sin(2 * phi + l[0] * 6.283) + 0.09 * Math.sin(3 * phi + l[1] * 6.283)
                + 0.06 * Math.sin(5 * phi + l[2] * 6.283));
        double c = Math.cos(phi) / b.along, s = Math.sin(phi) / b.across;
        return b.baseR / Math.sqrt(c * c + s * s) * (1.0 + lump);
    }

    /** Top of the base (Y, may be below the water) at (px, pz). */
    private static double baseTop(Berg b, double px, double pz) {
        double dx = px - b.x, dz = pz - b.z;
        double q = Math.hypot(dx, dz) / outline(b, dx, dz);
        if (q >= 1.0) return SEA_Y - 2;
        return SEA_Y - 1 + b.baseH * Math.pow(1.0 - q * q, b.profile);
    }

    /** What the iceberg has at block (x, y, z), NONE outside it. */
    public static byte blockAt(Berg b, int x, int y, int z) {
        return (byte) (raw(b, x, y, z) & ~STEEP);
    }

    private static byte raw(Berg b, int x, int y, int z) {
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        double dx = cx - b.x, dz = cz - b.z;
        if (dx * dx + dz * dz > b.reach * b.reach) return NONE;
        double dist = Math.hypot(dx, dz);

        // spires and columns first (they stand on the base; the spires lean out over the water)
        for (int s = 0; s < b.spires.length; s++) {
            Spire p = b.spires[s];
            double t = (cy - p.y0) / p.h;
            if (t < 0.0 || t > 1.0) continue;
            double bend = (t + p.curve * t * t) / (1.0 + p.curve) * p.h;  // leans more towards the top (curved)
            double ex = cx - (p.x + p.leanX * bend), ez = cz - (p.z + p.leanZ * bend);
            if (ex * ex + ez * ez > p.r * p.r * 1.7) continue;           // (quick: far outside it)
            double len = Math.hypot(ex, ez), phi = Math.atan2(ez, ex);
            long salt = b.salt + s * 131L;
            double rad, d, fromTop = (1.0 - t) * p.h;
            if (p.cap <= 0.0) {                                           // a shard: chunky, faceted, sharp tip
                rad = p.r * Math.pow(1.0 - t, 0.75) * (1.0 + 0.15 * Math.sin(Math.PI * t));
                rad *= 1.0 + FACETS * (vnoise3(salt, Math.cos(phi) * 1.4, Math.sin(phi) * 1.4, cy / 3.5) - 0.5);
                d = polygon(ex, ez, p.sides, p.turn);
            } else {                                                      // a column: round, fluted, rounded top
                rad = p.r * (1.0 - 0.16 * t);
                if (fromTop < p.cap) {
                    double k = 1.0 - fromTop / p.cap;
                    rad *= Math.sqrt(Math.max(0.0, 1.0 - k * k));
                }
                d = len / (1.0 + 0.1 * Math.sin(3 * phi + p.turn) + 0.05 * Math.sin(5 * phi + 2 * p.turn));
            }
            if (rad < 0.35 || d > rad) continue;
            double rel = d / rad;
            if (rel < SHELL && rad > 1.6) return PACKED_ICE;              // inside
            return p.cap <= 0.0 ? (byte) (shardShell(salt, ex, ez, cy, t, p.leanX, p.leanZ, rel, phi) | STEEP)
                    : columnShell(salt, cx, cy, cz, fromTop, p.cap, phi);
        }

        // the base above the water and the root below it
        if (dist > b.baseR * outlineScale(b) * ROOT_WIDER) return NONE;  // (quick: outside the widest outline)
        double out = outline(b, dx, dz);
        if (cy >= SEA_Y - 1) {
            double q = dist / out;
            if (q >= 1.0) return NONE;
            double top = SEA_Y - 1 + b.baseH * Math.pow(1.0 - q * q, b.profile) + 1.2 * (vnoise(b.salt, x / 7.0, z / 7.0) - 0.5);
            if (b.kind == TABLE && b.profile < 0.3) {                     // cracks running over the flat tables
                double c = Math.abs(vnoise(b.salt + 41, x / 15.0, z / 15.0) - 0.5);
                if (c < CRACK_WIDTH) top = Math.max(SEA_Y - 0.5, top - CRACK_DEPTH * (1.0 - c / CRACK_WIDTH));
            }
            if (cy > top) return NONE;
            double snow = b.kind == TABLE ? 2.0 : 1.0;
            if (cy > top - snow) return vnoise(b.salt + 3, x / 3.0, z / 3.0) < 0.8 ? SNOW : PACKED_ICE;   // snow on top
            return strata(b, x, y, z, dx, dz);
        }
        double q = dist / (out * ROOT_WIDER);
        if (q >= 1.0) return NONE;
        double bottom = SEA_Y - 1 - b.rootDepth * Math.pow(1.0 - Math.pow(q, 1.6), 0.8);
        if (cy < bottom) return NONE;
        return strata(b, x, y, z, dx, dz);
    }

    /** Outer blocks of a shard: snow on the faces looking up (into the lean), long blue streaks up the sides. */
    private static byte shardShell(long salt, double ex, double ez, double cy, double t, double leanX, double leanZ,
                                   double rel, double phi) {
        double len = Math.hypot(ex, ez), lean = Math.hypot(leanX, leanZ);
        double up = len < 1e-6 || lean < 1e-6 ? 0.0 : (ex * leanX + ez * leanZ) / (len * lean);
        double grain = vnoise3(salt + 5, ex * 0.7, cy * 0.35, ez * 0.7);
        if (t > 0.9) return grain < 0.65 ? SNOW : PACKED_ICE;                       // snowy tips
        if (rel > 0.78 && up > SNOW_FACE && grain < 0.5 + 0.45 * up) return SNOW;    // upper face of the leaning spire
        int band = (int) Math.floor((phi + Math.PI + 0.9 * t) / (Math.PI * 2.0) * 9.0);
        if (hash(salt, Math.floorMod(band, 9), (int) Math.floor(cy / 18.0), 1) < STREAK_SHARE) return BLUE_ICE;
        return grain > CLEAR_PATCH ? ICE : PACKED_ICE;
    }

    /** Outer blocks of a column: a snow cap, blue flutes running down, clear ice in patches. */
    private static byte columnShell(long salt, double cx, double cy, double cz, double fromTop, double cap, double phi) {
        if (fromTop < cap * 0.9 + 0.6) return SNOW;
        int band = (int) Math.floor((phi + Math.PI) / (Math.PI * 2.0) * 11.0);
        if (hash(salt, Math.floorMod(band, 11), 0, 3) < STREAK_SHARE) return BLUE_ICE;
        return vnoise3(salt + 9, cx / 4.0, cy / 5.0, cz / 4.0) > CLEAR_PATCH ? ICE : PACKED_ICE;
    }

    /** The base and root: packed ice with wavy, tilted bands of blue ice and patches of clear ice. */
    private static byte strata(Berg b, int x, int y, int z, double dx, double dz) {
        double f = (y + 3.0 * vnoise(b.salt + 23, x / 13.0, z / 13.0) + b.tiltX * dx + b.tiltZ * dz) / STRATA_STEP;
        if (f - Math.floor(f) < STRATA_BLUE) return BLUE_ICE;
        return vnoise3(b.salt + 29, x / 4.0, y / 4.0, z / 4.0) > CLEAR_PATCH ? ICE : PACKED_ICE;
    }

    /** Distance measure of a regular polygon with this many sides (equals the inner radius on its edges). */
    static double polygon(double ex, double ez, int sides, double turn) {
        double phi = Math.atan2(ez, ex) - turn;
        double seg = Math.PI * 2.0 / sides;
        double a = phi - seg * Math.floor(phi / seg) - seg / 2.0;
        return Math.hypot(ex, ez) * Math.cos(a) / Math.cos(seg / 2.0);
    }

    // ------------------------------------------------------------------ a whole column

    /**
     * All icebergs of one column together (they grow into each other): out[y - COLUMN_MIN_Y] gets the block at Y.
     * Ice wins over snow where two meet; then every face that looks at the sky gets snow and the underside of
     * every overhang clear ice. Returns false if no iceberg has a block in this column.
     */
    public static boolean column(List<Berg> bergs, int x, int z, byte[] out) {
        java.util.Arrays.fill(out, NONE);
        boolean any = false;
        for (Berg b : bergs) {
            double dx = x + 0.5 - b.x, dz = z + 0.5 - b.z;
            if (dx * dx + dz * dz > b.reach * b.reach) continue;
            int y0 = Math.max(COLUMN_MIN_Y, b.bottomY()), y1 = Math.min(COLUMN_MIN_Y + COLUMN_SIZE - 1, b.topY());
            for (int y = y0; y <= y1; y++) {
                byte m = raw(b, x, y, z);
                if (m == NONE) continue;
                int i = y - COLUMN_MIN_Y;
                int was = out[i] & ~STEEP;
                if (was == NONE || (was == SNOW && (m & ~STEEP) != SNOW)) out[i] = m;
                any = true;
            }
        }
        if (!any) return false;
        for (int i = COLUMN_SIZE - 1; i >= 0; i--) {
            if (out[i] == NONE) continue;
            int y = COLUMN_MIN_Y + i;
            boolean steep = (out[i] & STEEP) != 0;
            byte m = (byte) (out[i] & ~STEEP);
            if (y < SEA_Y) {
                out[i] = m == SNOW ? PACKED_ICE : m;                              // no snow in the water
                continue;
            }
            boolean openAbove = i + 1 >= COLUMN_SIZE || out[i + 1] == NONE;
            boolean openBelow = i > 0 && out[i - 1] == NONE;
            if (openAbove && (!steep || vnoise3(0x5A0BL, x / 3.0, y / 3.0, z / 3.0) < DUST)) m = SNOW;   // faces the sky
            else if (openBelow) m = ICE;                                          // under an overhang
            out[i] = m;
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double e0, double e1, double v) {
        double t = (v - e0) / (e1 - e0);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Smooth value noise 0..1. */
    static double vnoise(long salt, double fx, double fz) {
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = hash(salt, ix, iz, 5), b = hash(salt, ix + 1, iz, 5), c = hash(salt, ix, iz + 1, 5), d = hash(salt, ix + 1, iz + 1, 5);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    /** Smooth 3D value noise 0..1. */
    static double vnoise3(long salt, double fx, double fy, double fz) {
        int iy = (int) Math.floor(fy);
        double ty = fy - iy;
        ty = ty * ty * (3 - 2 * ty);
        double lo = vnoise(salt + iy * 0x5851F42D4C957F2DL, fx, fz), hi = vnoise(salt + (iy + 1) * 0x5851F42D4C957F2DL, fx, fz);
        return lo + (hi - lo) * ty;
    }

    static double hash(long seed, int i, int j, int k) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ ((long) k * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A small random stream (SplitMix64) - the same numbers for the same cell every time. */
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
