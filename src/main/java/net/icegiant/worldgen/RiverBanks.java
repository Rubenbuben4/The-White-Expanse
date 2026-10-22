package net.icegiant.worldgen;

/**
 * Height rules of the river banks, without Minecraft classes, so they can be checked outside the game
 * (test/RiverBanksTest). RiverFeature builds the banks with them.
 */
public final class RiverBanks {

    private RiverBanks() {
    }

    /** Nothing above this rim column needs holding (no river or lake water next to it). */
    public static final int NO_NEED = Integer.MIN_VALUE;

    /**
     * Top block of a rim column (RiverFeature#bank): normally rimDrop below its natural ground, a gravel step
     * between land and water. Where the river runs cut deep into the land the rim is a ledge 1 over the water
     * (deepCut). It is never cut below the water next to it (need: the highest water of the four neighbours) and,
     * since 2026-10-18, never below its own planned water either: where a river lies level with the land, the rims
     * further from the water were cut 1 below the land and the water - a 1 block deep gravel trench between the
     * river and the land (Rubenbuben4 saw it while flying).
     *
     * @param ground  natural ground of the column
     * @param water   planned water level of the river next to it (RiverNetwork.ChunkData#water)
     * @param rimDrop how far a rim lies below its land
     * @param deepCut true for an open (not tunnelled) rim: at most 1 over the water
     * @param need    water next to it that it must hold, or NO_NEED
     */
    public static int rimTop(int ground, int water, int rimDrop, boolean deepCut, int need) {
        int top = ground - rimDrop;
        if (deepCut) top = Math.min(top, water + 1);
        top = Math.max(top, Math.min(water, ground));      // no trench: not below its water (nor raised over its land)
        if (need != NO_NEED) top = Math.max(top, need);     // holds the water next to it (cut less, or build up)
        return top;
    }
}
