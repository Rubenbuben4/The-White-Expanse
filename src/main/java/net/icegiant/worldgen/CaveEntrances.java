package net.icegiant.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carver.CarvingContext;

/**
 * Frozen Realm: no cave mouths and ravine openings in the lowland (Rubenbuben4, 2026-10-15) - you can't look
 * through the ground into a cave there any more. Only in the mountains caves may open to the surface - and, since
 * 2026-10-21, on the sea floor: ravines like in vanilla oceans, filled with water (Rubenbuben4: "Schluchten wie in
 * normalem Minecraft", only under the sea). Because our deep sea lies far below vanilla's, the ravines there start
 * as far below as the floor is ({@link #ravineY}, SeaRavineMixin).
 * <p>
 * The cave and ravine carvers still dig everything underground; only the blocks within
 * {@value #SURFACE_SHELL} blocks below the terrain's surface stay solid where the surface lies below
 * {@value #MOUNTAIN_Y}. The surface height comes from the terrain maths (FrozenTerrainMath), so this only acts
 * on our terrain - other dimensions (no NorwayTerrain in their noise router) are untouched.
 */
public final class CaveEntrances {

    // ---- TUNE ----
    static final int SURFACE_SHELL = 8;               // this much solid ground stays over every cave in the lowland
    static final double MOUNTAIN_Y = 110.0;           // from this surface height on (mountains) caves may open
    static final double SEA_OPEN_Y = 60.0;            // sea floor below this (3+ blocks of water): may open too
    static final double VANILLA_SEA_FLOOR_Y = 40.0;   // vanilla's ravine heights (Y 10 - 67) are made for a floor about here
    static final int RAVINE_MIN_Y = -35;              // (ravines lower than this would reach the lava level)

    private CaveEntrances() {
    }

    /** True if the carver must leave this block alone (it would open the cave to the surface outside the mountains). */
    public static boolean keepSolid(CarvingContext context, BlockPos pos) {
        FrozenTerrainMath math = math(context.randomState());
        if (math == null) return false;
        double surface = math.cachedSurfaceY(pos.getX(), pos.getZ());
        if (surface < SEA_OPEN_Y) return false;                             // under the sea: open, like vanilla
        return surface < MOUNTAIN_Y && pos.getY() > surface - SURFACE_SHELL;
    }

    /**
     * Start height of a ravine (vanilla: Y 10 - 67 at random). Under our deep sea it is moved down by as much as the
     * floor lies below vanilla's usual sea floor, so as many ravines cut into the floor as in a vanilla ocean;
     * elsewhere (land, the shallow shelf) unchanged.
     */
    public static int ravineY(RandomState state, int chunkX, int chunkZ, int y) {
        FrozenTerrainMath math = math(state);
        if (math == null) return y;
        double floor = math.cachedSurfaceY(chunkX * 16 + 8, chunkZ * 16 + 8);
        if (floor >= VANILLA_SEA_FLOOR_Y) return y;
        return Math.max(RAVINE_MIN_Y, y + (int) Math.round(floor - VANILLA_SEA_FLOOR_Y));
    }

    static FrozenTerrainMath math(RandomState state) {
        return NorwayTerrain.of(state);
    }
}
