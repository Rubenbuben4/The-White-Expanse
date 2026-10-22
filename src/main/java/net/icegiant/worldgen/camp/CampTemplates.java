package net.icegiant.worldgen.camp;

import java.util.Map;

/**
 * The buildings of the Gnoll camps (Louisdepoui's builds, data/icegiant/structures/gnoll_camp/): template size
 * (x, z) and the cell just outside each one's entrance - found by walking from outside into the building's rooms
 * (the biggest room's widest doorway at ground level). The fish-head hut has a pond in it (water in its ground
 * layer) and is no longer built in the camps since 2026-10-21 (CampPlanner.EXTRA_BIG); its template stays here.
 */
public final class CampTemplates {

    public static final Map<String, CampPlanner.Hut> HUTS = Map.of(
            "chief_hut", new CampPlanner.Hut("chief_hut", 27, 29, 14, 6),
            "shaman_hut", new CampPlanner.Hut("shaman_hut", 35, 37, 27, 17),
            "bar", new CampPlanner.Hut("bar", 26, 15, 13, 5),
            "fishhead_hut", new CampPlanner.Hut("fishhead_hut", 24, 26, 23, 13),
            "stacked_hut", new CampPlanner.Hut("stacked_hut", 29, 27, 10, 20),
            "hut_1", new CampPlanner.Hut("hut_1", 17, 17, 15, 7),
            "hut_2", new CampPlanner.Hut("hut_2", 17, 16, 0, 8),
            "hut_3", new CampPlanner.Hut("hut_3", 18, 17, 8, 15),
            "diagonal_hut", new CampPlanner.Hut("diagonal_hut", 17, 18, 3, 5));

    /** Height of the ground layer inside the template (the little hill each building stands on). */
    public static int groundLevel(String name) {
        return "fishhead_hut".equals(name) ? 1 : 0;
    }

    /**
     * How far the ground layer lies above the world's ground. Every building stands on a hill 1 block high in its
     * template (Louisdepoui's builds); until 2026-10-21 that hill was put level with the ground (it replaced the
     * top block), and the terrain adaptation round the building then cut the ground there 1 block down - a small
     * trench round every hut (Rubenbuben4). Now the hill stands on the ground.
     */
    public static final int RAISE = 1;

    /** World height of the template's bottom for a building whose ground lies at groundY. */
    public static int placeY(String name, int groundY) {
        return groundY + RAISE - groundLevel(name);
    }

    private CampTemplates() {
    }
}
