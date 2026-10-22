package net.icegiant.worldgen.camp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Lays out a Gnoll camp - plain maths, no blocks or Minecraft classes, so a camp can be planned and checked
 * outside the game (GnollCampStructure builds it in the world).
 * <p>
 * The camp, the way Rubenbuben4 wants it:
 * <ul>
 *   <li>an open square (with a campfire, log benches round it) in the middle, flattened;</li>
 *   <li>the huts close round it, every entrance turned towards the square (each hut's real entrance, found in the
 *       build), a short trodden path from every entrance to the square;</li>
 *   <li>one way out of the camp through the widest gap, marked by two skull poles;</li>
 *   <li>camp life: lantern posts where the paths meet the square, barrels, hay bales and wood piles beside the huts,
 *       in a large camp the chief's banner pole in front of his hut;</li>
 *   <li>every hut stands on the middle height of the ground under it (not just under its centre), on ground that is
 *       not too steep and not wet - otherwise it moves further out or is left out.</li>
 * </ul>
 * A <b>huge</b> camp (rare, about twice a large one): the main square with the chief's and the shaman's hut and two
 * more big buildings round it, and two or three districts - each a smaller square of its own with small huts round
 * it, at the end of a broad street from the main square that goes on through the district and out of the camp (a
 * gate with skull poles), lantern posts along the streets.
 */
public final class CampPlanner {

    // ---- TUNE ----
    static final int PLAZA_LARGE = 8, PLAZA_SMALL = 5;   // radius of the square
    static final int PLAZA_HUGE = 10, PLAZA_DISTRICT = 6;   // huge camp: its main square, a district's square
    static final float THREE_DISTRICTS = 0.35F;          // share of huge camps with three districts (else two)
    static final int DISTRICT_STEP = 8;                  // a district's square at most this much above/below the main one
    static final int DISTRICT_SLOPE = 5;                 // the ground under a district's square may vary at most this much
    static final int STREET_LANTERNS = 12;               // a lantern post every this many blocks along a street
    static final int REACH = 120;                        // nothing of a camp further than this from its middle (x or z):
                                                         //     Minecraft builds a structure only up to 8 chunks round its start
    static final int PATH_MIN = 2;                       // shortest path from a small hut's entrance to the square
    static final int BIG_PATH_MIN = 7;                   // ... from a big building's: they stand back, the small huts
                                                         //     fill the gaps between them nearer the square
    static final int PATH_MAX = 14;                      // a hut is moved out at most this far to find room
    static final int HUT_GAP = 2;                        // free ground between two huts
    static final int MAX_HUT_SLOPE = 4;                  // the ground under a hut may vary at most this much
    static final int MAX_STEP_TO_PLAZA = 2;              // a hut may stand at most this much above/below its square
                                                         //     (both 6 until 2026-10-18: the terrain adaptation cut
                                                         //     rounded cliffs up to 7 high round the huts - 4/2 keep
                                                         //     them at most 4, mostly 2; Rubenbuben4's choice)
    static final int EXIT_LENGTH = 12;                   // the way out of the camp

    static final List<String> SMALL_HUTS = List.of("hut_1", "hut_2", "hut_3", "diagonal_hut");
    /** The big buildings besides chief and shaman. (Until 2026-10-21 also the fish-head hut - it has a pond in it.) */
    static final List<String> EXTRA_BIG = List.of("bar", "stacked_hut");
    private static final double[] SHIFTS = {0.0, 0.15, -0.15, 0.3, -0.3, 0.45, -0.45};

    /** How big a camp is. */
    public enum Size { SMALL, LARGE, HUGE }

    /** The ground of the world: top solid block at a column, and whether there is water there. */
    public interface Ground {
        int height(int x, int z);

        boolean wet(int x, int z);
    }

    /** A building as it lies in its template (unturned): size and the cell just outside its entrance. */
    public record Hut(String name, int w, int l, int doorX, int doorZ) {
    }

    /**
     * A placed building: turn (0 none, 1 clockwise 90, 2 180, 3 counter-clockwise 90 - Minecraft's Rotation order),
     * its footprint (min corner and size in the world), the height its ground layer lies at, the entrance cell, and
     * the square it belongs to (index in Plan.squares, 0 = the main square).
     */
    public record Placed(String name, int rot, int minX, int minZ, int sizeX, int sizeZ, int y, int doorX, int doorZ,
                         int square) {
    }

    /** An open square: middle, height, radius, and how far its part of the camp reaches (trees keep out). */
    public record Square(int x, int z, int y, int r, int reach) {
    }

    /** A path from (x0, z0) to (x1, z1), this wide. */
    public record Path(int x0, int z0, int x1, int z1, int width) {
    }

    /** A small thing of camp life at (x, z); dir: 0 = along x, 1 = along z (benches, logs). */
    public record Prop(String type, int x, int z, int dir) {
    }

    /** A planned camp; squares: the main one first (= cx, cz, plazaY, plazaR), then a huge camp's districts. */
    public record Plan(int cx, int cz, int plazaY, int plazaR, int radius, List<Placed> huts, List<Path> paths,
                       List<Prop> props, List<Square> squares, Size size) {
    }

    private CampPlanner() {
    }

    // ------------------------------------------------------------------ turning

    /** Template cell (x, z) of a w x l template, turned: its place relative to the turned footprint's min corner. */
    public static int[] turn(int x, int z, int rot, int w, int l) {
        return switch (rot & 3) {
            case 1 -> new int[]{l - 1 - z, x};
            case 2 -> new int[]{w - 1 - x, l - 1 - z};
            case 3 -> new int[]{z, w - 1 - x};
            default -> new int[]{x, z};
        };
    }

    /** The turn that points the entrance most nearly along (dx, dz). */
    static int rotationTowards(Hut h, double dx, double dz) {
        int best = 0;
        double bestDot = -2.0;
        for (int r = 0; r < 4; r++) {
            int sx = (r & 1) == 0 ? h.w() : h.l(), sz = (r & 1) == 0 ? h.l() : h.w();
            int[] d = turn(h.doorX(), h.doorZ(), r, h.w(), h.l());
            double vx = d[0] - (sx - 1) * 0.5, vz = d[1] - (sz - 1) * 0.5, len = Math.hypot(vx, vz);
            if (len < 1e-6) continue;
            double dot = (vx * dx + vz * dz) / (len * Math.hypot(dx, dz));
            if (dot > bestDot) {
                bestDot = dot;
                best = r;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the camp

    /** The buildings of a camp: large with chief and shaman, small without (no mages). */
    public static List<String> buildings(boolean large, Random random) {
        List<String> small = new ArrayList<>(SMALL_HUTS);
        Collections.shuffle(small, random);
        List<String> out = new ArrayList<>();
        if (large) {
            out.add("chief_hut");
            out.add("shaman_hut");
            List<String> extra = new ArrayList<>(EXTRA_BIG);                                       // one or two of
            Collections.shuffle(extra, random);                                                    // the big ones
            out.add(extra.get(0));
            if (random.nextFloat() < 0.3F && extra.size() > 1) out.add(extra.get(1));
            int n = 3 + random.nextInt(2);
            for (int i = 0; i < n; i++) out.add(small.get(i));
        } else {
            int n = 3 + random.nextInt(2);
            for (int i = 0; i < n; i++) out.add(small.get(i));
            if (random.nextFloat() < 0.35F) out.add("bar");
        }
        return out;
    }

    public static Plan plan(int cx, int cz, boolean large, Random random, Ground ground, Map<String, Hut> templates) {
        return plan(cx, cz, large ? Size.LARGE : Size.SMALL, random, ground, templates);
    }

    /**
     * Plans a camp round (cx, cz). Returns null if there is no room for it (tried with a few turns of the whole camp):
     * fewer than two huts find a place, a large or huge camp has no room for its chief's or shaman's hut, a huge camp
     * not for two districts.
     */
    public static Plan plan(int cx, int cz, Size size, Random random, Ground ground, Map<String, Hut> templates) {
        Ground g = cached(ground);
        int attempts = size == Size.SMALL ? 2 : size == Size.LARGE ? 6 : 4;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Plan p = size == Size.HUGE ? planHuge(cx, cz, random, g, templates)
                    : planCamp(cx, cz, size == Size.LARGE, random, g, templates);
            if (p == null) continue;
            if (size != Size.SMALL && !hasLeaders(p)) continue;
            return p;
        }
        return null;
    }

    static boolean hasLeaders(Plan p) {
        return p.huts().stream().anyMatch(h -> h.name().equals("chief_hut"))
                && p.huts().stream().anyMatch(h -> h.name().equals("shaman_hut"));
    }

    /** A camp being laid out. */
    private static final class Work {
        final int cx, cz;
        final Random random;
        final Ground ground;
        final Map<String, Hut> templates;
        final List<Placed> huts = new ArrayList<>();
        final List<Path> paths = new ArrayList<>();
        final List<Prop> props = new ArrayList<>();
        final List<Square> squares = new ArrayList<>();
        final List<String> smallPool = new ArrayList<>();
        int lanterns;

        Work(int cx, int cz, Random random, Ground ground, Map<String, Hut> templates) {
            this.cx = cx;
            this.cz = cz;
            this.random = random;
            this.ground = ground;
            this.templates = templates;
        }

        int[] mark() {
            return new int[]{this.huts.size(), this.paths.size(), this.props.size(), this.squares.size()};
        }

        void undo(int[] m) {
            trim(this.huts, m[0]);
            trim(this.paths, m[1]);
            trim(this.props, m[2]);
            trim(this.squares, m[3]);
        }

        private static void trim(List<?> l, int n) {
            while (l.size() > n) l.remove(l.size() - 1);
        }

        /** The next n small huts - all four kinds before one comes again. */
        List<String> smallHuts(int n) {
            List<String> out = new ArrayList<>();
            while (out.size() < n) {
                if (this.smallPool.isEmpty()) {
                    this.smallPool.addAll(SMALL_HUTS);
                    Collections.shuffle(this.smallPool, this.random);
                }
                out.add(this.smallPool.remove(this.smallPool.size() - 1));
            }
            return out;
        }
    }

    /** A small or large camp: one square, the huts round it, the way out through the widest gap. */
    private static Plan planCamp(int cx, int cz, boolean large, Random random, Ground ground, Map<String, Hut> templates) {
        Work w = new Work(cx, cz, random, ground, templates);
        int plazaR = large ? PLAZA_LARGE : PLAZA_SMALL;
        int plazaY = medianHeight(ground, cx - plazaR, cz - plazaR, 2 * plazaR + 1, 2 * plazaR + 1, true);
        w.squares.add(new Square(cx, cz, plazaY, plazaR, 0));
        List<String> names = buildings(large, random);
        names.removeIf(nm -> !templates.containsKey(nm));
        // the big buildings first, spread evenly round the square; then every small hut where it fits closest to the
        // square (and furthest from the others' directions) - a tight camp, no long spokes
        List<String> big = new ArrayList<>(), smallOnes = new ArrayList<>();
        for (String nm : names) {
            Hut h = templates.get(nm);
            (h.w() * h.l() >= 400 ? big : smallOnes).add(nm);
        }
        double a0 = random.nextDouble() * Math.PI * 2.0;
        double[] bigA = new double[big.size()];
        for (int b = 0; b < big.size(); b++) bigA[b] = a0 + b * Math.PI * 2.0 / big.size();
        // the way out (between two big buildings) is kept free while the huts are placed - otherwise the ring of huts
        // may close and the way would run into one
        int width = large ? 3 : 2;
        double exitA = a0 + Math.PI / Math.max(1, big.size());
        w.paths.add(ray(cx, cz, exitA, plazaR - 1, REACH, width + 2));
        List<Double> angles = new ArrayList<>(List.of(exitA));
        cluster(w, 0, big, bigA, smallOnes, angles, width);
        w.paths.remove(0);
        if (w.huts.size() < 2) return null;
        squareProps(w, w.squares.get(0), large ? 4 : 3, 3.0);
        w.paths.add(exitPath(w, w.squares.get(0), exitA, width));               // out past the huts,
        skullPoles(w, cx, cz, exitA, plazaR + 2);                                // two skull poles at its start
        return finish(w, large ? Size.LARGE : Size.SMALL);
    }

    /** The way out of a camp from square s along angle a: at least EXIT_LENGTH, on until the huts beside it are behind. */
    private static Path exitPath(Work w, Square s, double a, int width) {
        int far = s.r() + EXIT_LENGTH;
        for (Placed p : w.huts) far = Math.max(far, farCorner(p, s.x(), s.z()) + 2);
        double ux = Math.cos(a), uz = Math.sin(a);
        int end = s.r() + EXIT_LENGTH;
        for (int t = s.r() + 1; t <= far; t++) {
            if (hutDistance(w.huts, s.x() + ux * t, s.z() + uz * t) < 8.0) end = Math.max(end, t + 4);
        }
        return ray(s.x(), s.z(), a, s.r() - 1, end, width);
    }

    /** Distance from the point (x, z) to the nearest hut's footprint. */
    static double hutDistance(List<Placed> huts, double x, double z) {
        double best = Double.MAX_VALUE;
        for (Placed p : huts) {
            double dx = Math.max(Math.max(p.minX() - x, 0.0), x - (p.minX() + p.sizeX()));
            double dz = Math.max(Math.max(p.minZ() - z, 0.0), z - (p.minZ() + p.sizeZ()));
            best = Math.min(best, Math.hypot(dx, dz));
        }
        return best;
    }

    /**
     * A huge camp: the main square with the chief's and the shaman's hut and two more big buildings round it, the
     * streets to the districts between them (kept free while those are placed); then each district - a smaller square
     * beyond the main square's huts at the end of its street, small huts round it (now and then a third big
     * building, if EXTRA_BIG has one more - not since the fish-head hut is left out), the street going on out of the
     * camp through a gate.
     */
    private static Plan planHuge(int cx, int cz, Random random, Ground ground, Map<String, Hut> templates) {
        Work w = new Work(cx, cz, random, ground, templates);
        int plazaY = medianHeight(ground, cx - PLAZA_HUGE, cz - PLAZA_HUGE, 2 * PLAZA_HUGE + 1, 2 * PLAZA_HUGE + 1, true);
        w.squares.add(new Square(cx, cz, plazaY, PLAZA_HUGE, 0));
        List<String> extra = new ArrayList<>(EXTRA_BIG);
        Collections.shuffle(extra, random);
        List<String> big = new ArrayList<>(List.of("chief_hut", "shaman_hut"));
        big.addAll(extra.subList(0, Math.min(2, extra.size())));               // two more big buildings round it
        Collections.shuffle(big, random);
        big.removeIf(nm -> !templates.containsKey(nm));
        String spare = extra.size() > 2 && random.nextFloat() < 0.5F && templates.containsKey(extra.get(2)) ? extra.get(2) : null;
        int districts = random.nextFloat() < THREE_DISTRICTS ? 3 : 2;
        // directions round the main square, evenly: the big buildings' and, spread among them, the streets'
        int n = big.size() + districts;
        double a0 = random.nextDouble() * Math.PI * 2.0;
        double[] bigA = new double[big.size()], streetA = new double[districts];
        for (int k = 0, bi = 0, si = 0; k < n; k++) {
            double a = a0 + k * Math.PI * 2.0 / n;
            if ((k + 1) * districts / n > k * districts / n) streetA[si++] = a;
            else bigA[bi++] = a;
        }
        List<Path> keepFree = new ArrayList<>();
        List<Double> angles = new ArrayList<>();
        for (double a : streetA) {
            keepFree.add(ray(cx, cz, a, PLAZA_HUGE - 1, REACH, 3));
            angles.add(a);
        }
        w.paths.addAll(keepFree);
        cluster(w, 0, big, bigA, w.smallHuts(2), angles, 3);
        w.paths.subList(0, keepFree.size()).clear();                     // (they were the first paths)
        if (w.huts.size() < 3) return null;
        int extent = PLAZA_HUGE + 16;
        for (Placed p : w.huts) extent = Math.max(extent, farCorner(p, cx, cz));
        int made = 0;
        for (int s = 0; s < districts; s++) {
            double a = streetA[s], ux = Math.cos(a), uz = Math.sin(a);
            int from = Math.max(PLAZA_HUGE + 24, extent - 8);
            int to = Math.min(extent + 18, REACH - PLAZA_DISTRICT - EXIT_LENGTH - 2);
            for (int d = from; d <= to; d += 3) {
                int x = cx + (int) Math.round(ux * d), z = cz + (int) Math.round(uz * d);
                if (!districtFree(w, x, z, a, d)) continue;
                int[] range = new int[2];
                int y = medianHeight(ground, x - PLAZA_DISTRICT, z - PLAZA_DISTRICT, 2 * PLAZA_DISTRICT + 1,
                        2 * PLAZA_DISTRICT + 1, false, range);
                if (y == Integer.MIN_VALUE || range[1] - range[0] > DISTRICT_SLOPE || Math.abs(y - plazaY) > DISTRICT_STEP) continue;
                int[] mark = w.mark();
                int lanterns = w.lanterns;
                w.squares.add(new Square(x, z, y, PLAZA_DISTRICT, 0));
                int sq = w.squares.size() - 1;
                w.paths.add(ray(cx, cz, a, PLAZA_HUGE - 1, d - PLAZA_DISTRICT + 1, 3));                     // street
                w.paths.add(ray(cx, cz, a, d + PLAZA_DISTRICT - 1, d + PLAZA_DISTRICT + EXIT_LENGTH, 3));   // gate
                List<Double> taken = new ArrayList<>(List.of(a + Math.PI, a));
                List<String> districtBig = s == 0 && spare != null ? List.of(spare) : List.of();
                if (cluster(w, sq, districtBig, null, w.smallHuts(3 + random.nextInt(2)), taken, 2) < 2) {
                    w.undo(mark);                                               // not room enough here: further out
                    w.lanterns = lanterns;
                    continue;
                }
                squareProps(w, w.squares.get(sq), 3, 3.0);
                skullPoles(w, cx, cz, a, d + PLAZA_DISTRICT + 2);
                int side = 1;
                for (int r = PLAZA_HUGE + 6; r <= d - PLAZA_DISTRICT - 4; r += STREET_LANTERNS) {
                    w.props.add(new Prop("lantern_post", cx + (int) Math.round(ux * r - uz * 4.0 * side),
                            cz + (int) Math.round(uz * r + ux * 4.0 * side), 0));
                    side = -side;
                }
                made++;
                break;
            }
        }
        if (made < 2) return null;
        squareProps(w, w.squares.get(0), 5, 4.0);
        return finish(w, Size.HUGE);
    }

    /**
     * Room for a district's square at (x, z), d along its street at angle a: no hut on or near it, away from the
     * other squares, and neither the street to it nor the gate beyond it runs through a hut.
     */
    private static boolean districtFree(Work w, int x, int z, double a, int d) {
        for (Placed p : w.huts) if (rectNearDisc(p.minX(), p.minZ(), p.sizeX(), p.sizeZ(), x, z, PLAZA_DISTRICT + 3)) return false;
        for (Square s : w.squares) if (Math.hypot(s.x() - x, s.z() - z) < s.r() + PLAZA_DISTRICT + 24) return false;
        Path street = ray(w.cx, w.cz, a, PLAZA_HUGE - 1, d - PLAZA_DISTRICT + 1, 3);
        Path gate = ray(w.cx, w.cz, a, d + PLAZA_DISTRICT - 1, d + PLAZA_DISTRICT + EXIT_LENGTH, 3);
        return !segmentBlocked(street, w.huts) && !segmentBlocked(gate, w.huts);
    }

    /**
     * Huts round square number sq: the big ones (at the given angles, each the first spot that fits - turned a little
     * if needed - or, without angles, like the small ones), then every other hut where it fits closest to the square
     * and furthest from the directions already taken (angles: paths leading in, the huts placed - added to). Each
     * gets a path to the square and a lantern post where it meets it. Returns how many found a place.
     */
    private static int cluster(Work w, int sq, List<String> big, double[] bigAngles, List<String> small,
                               List<Double> angles, int pathWidth) {
        Square s = w.squares.get(sq);
        List<String> order = new ArrayList<>(big);
        order.addAll(small);
        double base = w.random.nextDouble() * Math.PI * 2.0;
        int count = 0;
        for (int b = 0; b < order.size(); b++) {
            Hut h = w.templates.get(order.get(b));
            if (h == null) continue;
            boolean isBig = b < big.size(), fixed = isBig && bigAngles != null;
            List<Double> tries = new ArrayList<>();
            if (fixed) {
                for (double shift : SHIFTS) tries.add(bigAngles[b] + shift);
            } else {
                for (int k = 0; k < 24; k++) tries.add(base + (k + w.random.nextDouble() * 0.5) * Math.PI * 2.0 / 24);
            }
            Placed p = null;
            double a = 0.0, bestScore = Double.MAX_VALUE;
            for (double t : tries) {
                Placed c = place(w, h, t, sq, isBig ? BIG_PATH_MIN : PATH_MIN);
                if (c == null) continue;
                double dist = Math.hypot(c.doorX() - s.x(), c.doorZ() - s.z());
                double near = Math.PI;                                          // angle to the nearest taken direction
                for (double o : angles) near = Math.min(near, Math.abs(Math.atan2(Math.sin(t - o), Math.cos(t - o))));
                double score = dist - near * 4.0;
                if (score < bestScore) {
                    bestScore = score;
                    p = c;
                    a = t;
                }
                if (fixed) break;                                               // big ones: the first that fits
            }
            if (p == null) continue;
            w.huts.add(p);
            angles.add(a);
            count++;
            // path: entrance -> edge of the square
            double ex = p.doorX() - s.x(), ez = p.doorZ() - s.z(), el = Math.max(1e-6, Math.hypot(ex, ez));
            int px = s.x() + (int) Math.round(ex / el * (s.r() - 1)), pz = s.z() + (int) Math.round(ez / el * (s.r() - 1));
            w.paths.add(new Path(p.doorX(), p.doorZ(), px, pz, pathWidth));
            // a lantern post where the path meets the square (beside it)
            double sx = -ez / el, sz = ex / el;
            int side = (w.lanterns++ & 1) == 0 ? 1 : -1;
            w.props.add(new Prop("lantern_post", s.x() + (int) Math.round(ex / el * (s.r() + 1) + sx * 2.5 * side),
                    s.z() + (int) Math.round(ez / el * (s.r() + 1) + sz * 2.5 * side), 0));
        }
        return count;
    }

    /** A square's campfire and the log benches round it. */
    private static void squareProps(Work w, Square s, int benches, double benchR) {
        w.props.add(new Prop("campfire", s.x(), s.z(), 0));
        double ba = w.random.nextDouble() * Math.PI;
        for (int k = 0; k < benches; k++) {
            double a = ba + k * Math.PI * 2.0 / benches;
            int bx = s.x() + (int) Math.round(Math.cos(a) * benchR), bz = s.z() + (int) Math.round(Math.sin(a) * benchR);
            int dir = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? 1 : 0;      // along the circle
            w.props.add(new Prop("bench", bx, bz, dir));
        }
    }

    /** Two skull poles either side of a way out, r from (cx, cz) along angle a. */
    private static void skullPoles(Work w, int cx, int cz, double a, int r) {
        double ux = Math.cos(a), uz = Math.sin(a);
        for (int s = -1; s <= 1; s += 2) {
            w.props.add(new Prop("skull_pole", cx + (int) Math.round(ux * r - uz * 2.5 * s), cz + (int) Math.round(uz * r + ux * 2.5 * s), 0));
        }
    }

    /** Barrels, hay and wood piles beside the huts, the chief's banner pole; how far everything reaches. */
    private static Plan finish(Work w, Size size) {
        Random random = w.random;
        String[] stuff = {"barrels", "hay", "woodpile", "barrels", "cauldron"};
        for (Placed p : w.huts) {
            if (random.nextFloat() > (size == Size.SMALL ? 0.6F : 0.8F)) continue;
            Square s = w.squares.get(p.square());
            double hx = p.minX() + p.sizeX() * 0.5 - s.x(), hz = p.minZ() + p.sizeZ() * 0.5 - s.z(), hl = Math.max(1e-6, Math.hypot(hx, hz));
            int side = random.nextBoolean() ? 1 : -1;
            double half = Math.max(p.sizeX(), p.sizeZ()) * 0.5 + 1.5;
            int x = (int) Math.round(s.x() + hx - hz / hl * half * side - hx / hl * 2.0);
            int z = (int) Math.round(s.z() + hz + hx / hl * half * side - hz / hl * 2.0);
            if (!insideAny(w.huts, x, z, 0)) w.props.add(new Prop(stuff[random.nextInt(stuff.length)], x, z, random.nextInt(2)));
        }
        for (Placed p : w.huts) {                                               // the chief's banner pole
            if (!p.name().equals("chief_hut")) continue;
            Square s = w.squares.get(p.square());
            double ex = p.doorX() - s.x(), ez = p.doorZ() - s.z(), el = Math.max(1e-6, Math.hypot(ex, ez));
            w.props.add(new Prop("banner_pole", (int) Math.round(p.doorX() - ex / el * 2 - ez / el * 3),
                    (int) Math.round(p.doorZ() - ez / el * 2 + ex / el * 3), 0));
        }
        w.props.removeIf(pr -> !pr.type().equals("campfire") && !pr.type().equals("bench") && insideAny(w.huts, pr.x(), pr.z(), 1));
        // how far it all reaches: the whole camp, and each square's part of it
        int radius = 0;
        int[] reach = new int[w.squares.size()];
        for (int i = 0; i < reach.length; i++) {
            Square s = w.squares.get(i);
            reach[i] = s.r() + EXIT_LENGTH;
            radius = Math.max(radius, (int) Math.ceil(Math.hypot(s.x() - w.cx, s.z() - w.cz)) + s.r());
        }
        for (Placed p : w.huts) {
            Square s = w.squares.get(p.square());
            reach[p.square()] = Math.max(reach[p.square()], farCorner(p, s.x(), s.z()));
            radius = Math.max(radius, farCorner(p, w.cx, w.cz));
        }
        for (Path p : w.paths) {
            radius = Math.max(radius, (int) Math.ceil(Math.hypot(p.x0() - w.cx, p.z0() - w.cz)));
            radius = Math.max(radius, (int) Math.ceil(Math.hypot(p.x1() - w.cx, p.z1() - w.cz)));
        }
        radius += 3;
        List<Square> squares = new ArrayList<>();
        for (int i = 0; i < reach.length; i++) {
            Square s = w.squares.get(i);
            squares.add(new Square(s.x(), s.z(), s.y(), s.r(), reach.length == 1 ? radius : reach[i] + 3));
        }
        Square main = squares.get(0);
        return new Plan(w.cx, w.cz, main.y(), main.r(), radius, w.huts, w.paths, w.props, squares, size);
    }

    /** One building at angle a round square sq, as close as it fits; null if it does not fit anywhere. */
    private static Placed place(Work w, Hut h, double a, int sq, int minPath) {
        Square s = w.squares.get(sq);
        double ux = Math.cos(a), uz = Math.sin(a);
        int rot = rotationTowards(h, -ux, -uz);                                  // entrance towards the square
        int sx = (rot & 1) == 0 ? h.w() : h.l(), sz = (rot & 1) == 0 ? h.l() : h.w();
        int[] door = turn(h.doorX(), h.doorZ(), rot, h.w(), h.l());
        search:
        for (int path = minPath; path <= minPath + PATH_MAX + 6; path++) {      // as close as it fits
            int dx = s.x() + (int) Math.round(ux * (s.r() + path)), dz = s.z() + (int) Math.round(uz * (s.r() + path));
            int minX = dx - door[0], minZ = dz - door[1];
            if (rectNearDisc(minX, minZ, sx, sz, s.x(), s.z(), s.r() + 1)) continue;   // would reach into the square
            for (int i = 0; i < w.squares.size(); i++) {                         // ... or into another one
                Square o = w.squares.get(i);
                if (i != sq && rectNearDisc(minX, minZ, sx, sz, o.x(), o.z(), o.r() + 2)) continue search;
            }
            if (Math.max(Math.abs(minX - w.cx), Math.abs(minX + sx - 1 - w.cx)) > REACH
                    || Math.max(Math.abs(minZ - w.cz), Math.abs(minZ + sz - 1 - w.cz)) > REACH) continue;   // too far out
            for (Placed o : w.huts) {
                if (minX - HUT_GAP < o.minX() + o.sizeX() && o.minX() < minX + sx + HUT_GAP
                        && minZ - HUT_GAP < o.minZ() + o.sizeZ() && o.minZ() < minZ + sz + HUT_GAP) continue search;
            }
            if (pathBlocked(dx, dz, s.x(), s.z(), s.r(), w.huts)) continue;     // its path would run through a hut
            if (onPath(minX, minZ, sx, sz, w.paths)) continue;                   // ... or it would stand on another's
            int[] range = new int[2];
            int y = medianHeight(w.ground, minX, minZ, sx, sz, false, range);
            if (y == Integer.MIN_VALUE || range[1] - range[0] > MAX_HUT_SLOPE) continue;   // wet or too steep
            if (Math.abs(y - s.y()) > MAX_STEP_TO_PLAZA) continue;               // too far above/below the square
            return new Placed(h.name(), rot, minX, minZ, sx, sz, y, dx, dz, sq);
        }
        return null;
    }

    /** A straight path from r0 to r1 out from (cx, cz) along angle a. */
    static Path ray(int cx, int cz, double a, int r0, int r1, int width) {
        double ux = Math.cos(a), uz = Math.sin(a);
        return new Path(cx + (int) Math.round(ux * r0), cz + (int) Math.round(uz * r0),
                cx + (int) Math.round(ux * r1), cz + (int) Math.round(uz * r1), width);
    }

    /** Distance from (x, z) to the far corner of the hut. */
    static int farCorner(Placed p, int x, int z) {
        int r = 0;
        for (int[] c : new int[][]{{p.minX(), p.minZ()}, {p.minX() + p.sizeX(), p.minZ()}, {p.minX(), p.minZ() + p.sizeZ()},
                {p.minX() + p.sizeX(), p.minZ() + p.sizeZ()}}) {
            r = Math.max(r, (int) Math.ceil(Math.hypot(c[0] - x, c[1] - z)));
        }
        return r;
    }

    /** Ground heights are asked many times while trying places: each column only once. */
    static Ground cached(Ground g) {
        java.util.HashMap<Long, Integer> heights = new java.util.HashMap<>();
        java.util.HashMap<Long, Boolean> wet = new java.util.HashMap<>();
        return new Ground() {
            @Override
            public int height(int x, int z) {
                return heights.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> g.height(x, z));
            }

            @Override
            public boolean wet(int x, int z) {
                return wet.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> g.wet(x, z));
            }
        };
    }

    /** True if the rectangle (a block or two round it included - more for a wide path) covers a piece of a path. */
    static boolean onPath(int minX, int minZ, int sx, int sz, List<Path> paths) {
        for (Path p : paths) {
            int m = Math.max(1, (p.width() + 1) / 2);
            double len = Math.max(1e-6, Math.hypot(p.x1() - p.x0(), p.z1() - p.z0()));
            for (double t = 0.0; t <= len; t += 0.5) {
                double x = p.x0() + (p.x1() - p.x0()) * t / len, z = p.z0() + (p.z1() - p.z0()) * t / len;
                if (x >= minX - m && x < minX + sx + m && z >= minZ - m && z < minZ + sz + m) return true;
            }
        }
        return false;
    }

    /** True if the straight path from the entrance (dx, dz) to the square runs through one of the huts. */
    static boolean pathBlocked(int dx, int dz, int cx, int cz, int plazaR, List<Placed> huts) {
        double len = Math.hypot(cx - dx, cz - dz);
        for (double t = 0.0; t <= Math.max(0.0, len - plazaR); t += 0.5) {
            double x = dx + (cx - dx) * t / len, z = dz + (cz - dz) * t / len;
            for (double side = -0.5; side <= 0.5; side += 1.0) {                 // (the middle of the path)
                double px = x - (cz - dz) / len * side, pz = z + (cx - dx) / len * side;
                if (insideAny(huts, (int) Math.floor(px), (int) Math.floor(pz), 0)) return true;
            }
        }
        return false;
    }

    /** True if the path (its whole width and a block either side) runs through one of the huts. */
    static boolean segmentBlocked(Path p, List<Placed> huts) {
        double len = Math.max(1e-6, Math.hypot(p.x1() - p.x0(), p.z1() - p.z0()));
        double nx = -(p.z1() - p.z0()) / len, nz = (p.x1() - p.x0()) / len, half = p.width() * 0.5 + 1.0;
        for (double t = 0.0; t <= len; t += 0.5) {
            double x = p.x0() + (p.x1() - p.x0()) * t / len, z = p.z0() + (p.z1() - p.z0()) * t / len;
            for (double o = -half; o <= half + 1e-6; o += 0.5) {
                if (insideAny(huts, (int) Math.floor(x + nx * o), (int) Math.floor(z + nz * o), 0)) return true;
            }
        }
        return false;
    }

    static boolean insideAny(List<Placed> huts, int x, int z, int margin) {
        for (Placed p : huts) {
            if (x >= p.minX() - margin && x < p.minX() + p.sizeX() + margin && z >= p.minZ() - margin && z < p.minZ() + p.sizeZ() + margin) {
                return true;
            }
        }
        return false;
    }

    /** True if the rectangle comes closer than r to (cx, cz). */
    static boolean rectNearDisc(int minX, int minZ, int sx, int sz, int cx, int cz, double r) {
        double nx = Math.max(minX, Math.min(cx, minX + sx - 1)), nz = Math.max(minZ, Math.min(cz, minZ + sz - 1));
        return Math.hypot(nx - cx, nz - cz) < r;
    }

    static int medianHeight(Ground g, int minX, int minZ, int sx, int sz, boolean ignoreWet) {
        return medianHeight(g, minX, minZ, sx, sz, ignoreWet, new int[2]);
    }

    /**
     * Middle height of the ground in the rectangle (sampled on the even columns - every 2 blocks, the same columns
     * for every try, so the world's ground is asked less); MIN_VALUE if water is there.
     */
    static int medianHeight(Ground g, int minX, int minZ, int sx, int sz, boolean ignoreWet, int[] range) {
        List<Integer> hs = new ArrayList<>();
        for (int x = minX + (minX & 1); x < minX + sx; x += 2) {
            for (int z = minZ + (minZ & 1); z < minZ + sz; z += 2) {
                if (!ignoreWet && g.wet(x, z)) return Integer.MIN_VALUE;
                hs.add(g.height(x, z));
            }
        }
        if (hs.isEmpty()) hs.add(g.height(minX, minZ));
        Collections.sort(hs);
        range[0] = hs.get(0);
        range[1] = hs.get(hs.size() - 1);
        return hs.get(hs.size() / 2);
    }
}
