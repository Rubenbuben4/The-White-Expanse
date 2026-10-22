package net.icegiant.item;

/**
 * The plain maths of the Giant Rune Sword's abilities - no Minecraft classes, so it can be checked outside the
 * game (test/SwordGeometryTest). Distances in blocks, angles in degrees. "size" is the user's size factor
 * (width / 0.6: 1 for a player, 2.5 for an Ice Giant); every reach, length and width grows with it.
 */
public final class SwordGeometry {

    // ---- TUNE (Riesen-Runenschwert, 2026-10-17) ----
    public static final double CUT_BLOCKS_PER_SIZE = 8.0;     // ground cut: length 8 x size (giant: 20) ...
    public static final int CUT_MAX_LENGTH = 24;              // ... at most this long
    public static final double CUT_HALF_WIDTH = 1.5;          // ground cut hits up to this far beside its line (x size)
    public static final double HIT_HEIGHT = 3.0;              // both hit up to this high above the ground / feet (x size)
    public static final double HIT_BELOW = 1.0;               // ... and this far below
    public static final double SWEEP_REACH = 6.0;             // wide sweep: reach (x size) ...
    public static final double SWEEP_HALF_ANGLE = 60.0;       // ... in a cone of +-60 degrees
    public static final int GROUND_SEARCH = 3;                // the cut follows the ground up / down this many blocks
    public static final int WALL_HEIGHT = 3;                  // ground this much higher in one step: a wall, the cut ends

    private SwordGeometry() {
    }

    /** Size factor of a creature this wide (a normal player: 1). */
    public static double size(double bbWidth) {
        return Math.max(1.0, bbWidth / 0.6);
    }

    /** How many blocks the ground cut runs. */
    public static int cutLength(double size) {
        return (int) Math.min(CUT_MAX_LENGTH, Math.round(CUT_BLOCKS_PER_SIZE * size));
    }

    /** Fracture blocks on each side of the cut's middle line (player 0: a 1 block crack, giant 1: 3 blocks). */
    public static int cutHalfWidth(double size) {
        return (int) Math.floor((size - 0.5) / 2.0);
    }

    /**
     * Wide sweep: is a target at (dx, dy, dz) from the swinger's feet (dy = target feet - swinger feet) inside the
     * cone in front of a swinger looking along (lookX, lookZ)?
     */
    public static boolean inCone(double dx, double dz, double dy, double lookX, double lookZ, double size) {
        if (dy < -HIT_BELOW || dy > HIT_HEIGHT * size) return false;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > SWEEP_REACH * size) return false;
        if (dist < 0.5) return true;                                            // standing right at the body
        double look = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (look < 1e-9) return false;
        double cos = (dx * lookX + dz * lookZ) / (dist * look);
        return cos >= Math.cos(Math.toRadians(SWEEP_HALF_ANGLE)) - 1e-12;
    }

    /**
     * Ground cut: is a target {@code along} blocks down the line and {@code lateral} blocks beside it (dy above the
     * ground of the cut) in the stretch the tip has just crossed (prevTip, tip]?
     */
    public static boolean inCutSlice(double along, double lateral, double dy, double prevTip, double tip, double size) {
        return inCutPath(along, along, lateral, dy, prevTip, tip, size);
    }

    /**
     * Ground cut, for a target that moved this tick from {@code alongBefore} to {@code alongNow} down the line: did
     * the tip, running from prevTip to tip, pass it? (It was still ahead of the tip and is now behind it - also when
     * it ran at the cut and jumped past the slice between two ticks.) lateral and dy as in inCutSlice.
     */
    public static boolean inCutPath(double alongBefore, double alongNow, double lateral, double dy,
                                    double prevTip, double tip, double size) {
        return alongBefore > prevTip && alongNow <= tip
                && Math.abs(lateral) <= CUT_HALF_WIDTH * size
                && dy >= -HIT_BELOW && dy <= HIT_HEIGHT * size;
    }

    /** Which side of the line a target is thrown to: +1 or -1. */
    public static int sideSign(double lateral) {
        return lateral >= 0.0 ? 1 : -1;
    }

    /** What one block is for the ground cut. */
    public enum Cell {
        /** Air, plants, snow layers, leaves: the cut passes through. */
        OPEN,
        /** Stops movement (also a crack block standing in for such a block). */
        SOLID,
        /** Water, lava, seagrass: no ground and never cracked. */
        FLUID
    }

    /** The world as the ground cut sees it. */
    @FunctionalInterface
    public interface ColumnView {
        Cell at(int x, int y, int z);
    }

    /** Can one stand on this block: solid, with open space (not water) above? */
    public static boolean standable(ColumnView w, int x, int y, int z) {
        return w.at(x, y, z) == Cell.SOLID && w.at(x, y + 1, z) == Cell.OPEN;
    }

    /**
     * The ground of a column near lastY: the surface one can stand on closest to lastY within +-range (equally far:
     * the higher one). Integer.MIN_VALUE if there is none - an abyss, or water (a river bed is no ground).
     */
    public static int groundY(ColumnView w, int x, int z, int lastY, int range) {
        for (int d = 0; d <= range; d++) {
            if (standable(w, x, lastY + d, z)) return lastY + d;
            if (d > 0 && standable(w, x, lastY - d, z)) return lastY - d;
        }
        return Integer.MIN_VALUE;
    }

    /** The ground under the next step of the cut, near the last ground height (Integer.MIN_VALUE: none). */
    public static int nextGroundY(ColumnView w, int x, int z, int lastY) {
        return groundY(w, x, z, lastY, GROUND_SEARCH);
    }

    /** A wall in front: the WALL_HEIGHT blocks above the last ground height are all solid. The cut ends there. */
    public static boolean wallAhead(ColumnView w, int x, int z, int lastY) {
        for (int y = lastY + 1; y <= lastY + WALL_HEIGHT; y++) {
            if (w.at(x, y, z) != Cell.SOLID) return false;
        }
        return true;
    }

    /** Ground this much higher than the last step: a wall, the cut ends there. */
    public static boolean blockedByWall(int groundY, int lastY) {
        return groundY != Integer.MIN_VALUE && groundY - lastY >= WALL_HEIGHT;
    }

    /**
     * Which blocks of a column the cut cracks: 0 none, 1 the ground block, 2 the thin cover on it (flower, snow
     * layer) and then the ground - top down, so the cover never stands on a crack block by itself and drops.
     * Leaves the column alone if the cover may not crack (tall grass, a door, a sign: it would break off its
     * ground) or something loose stands on the cover (sugar cane, bamboo: it would break off).
     */
    public static int crackLayers(boolean groundCrackable, boolean coverPresent, boolean coverCrackable,
                                  boolean riderNeedsSupport) {
        if (!groundCrackable) return 0;
        if (!coverPresent) return 1;
        if (!coverCrackable || riderNeedsSupport) return 0;
        return 2;
    }

    /**
     * Ticks of cooldown left (0: ready). An entry further away than a whole cooldown comes from another world
     * (game time starts anew there) and counts as ready.
     */
    public static long cooldownLeft(Long until, long now, int cooldownTicks) {
        if (until == null || now >= until || until - now > cooldownTicks) return 0;
        return until - now;
    }

    /**
     * May a sword ability hit this target? Never the swinger itself and never players in creative or spectator
     * mode; an Ice Giant (any non-player swinger) spares other Ice Giants, the gnolls and villagers (those are
     * only grabbed or hit normally, Frost Raids). A player hits everything else.
     */
    public static boolean mayHit(boolean self, boolean targetCreativeOrSpectator, boolean attackerIsPlayer,
                                 boolean targetIsGiant, boolean targetIsGnoll, boolean targetIsVillager) {
        if (self || targetCreativeOrSpectator) return false;
        return attackerIsPlayer || !(targetIsGiant || targetIsGnoll || targetIsVillager);
    }
}
