package net.icegiant.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * Smooth, terrain-aware snow cover for the Frozen Realm (replaces vanilla "freeze_top_layer" there).
 *
 * <p>Instead of one snow layer everywhere, the snow surface is computed from the smooth terrain
 * height (the same value the terrain generator uses, read back from the noise router):
 * <ul>
 *   <li>the snow surface follows a <b>blurred</b> copy of the terrain, so the 1-block steps of the
 *       ground are filled into ramps of 1..8 snow layers - the snow gets thicker and thinner gradually;</li>
 *   <li><b>steep cliffs stay bare rock</b>, gentle slopes and flat ground get the most snow;</li>
 *   <li><b>hollows and gullies</b> collect extra snow (drifts), <b>ridges</b> are blown almost clean;</li>
 *   <li>more snow higher up, only a thin dusting in the lowland forests;</li>
 *   <li><b>tree wells</b>: bare needle litter (podzol, coarse dirt) and ferns right around the trunks;</li>
 *   <li><b>scree</b>: loose rock (gravel, andesite, cobbles) at the foot of cliffs, fading out downhill.</li>
 * </ul>
 * On the sea: ice floes (IceFloes) and, out on the deep water, icebergs (Icebergs) - built here column by column.
 * Only blocks inside the chunk being decorated are changed. All numbers are TUNE constants.
 */
public final class SnowCover {

    // ---- TUNE ----
    static final double SNOW_START_Y = 75.0, SNOW_FULL_Y = 260.0;   // altitude -> base depth
    static final double LOW_DEPTH = 0.15, HIGH_DEPTH = 1.5;           // base depth in blocks (1 block = 8 layers)
    // ---- TUNE: slope painting (like WorldPainter slope layers; Rubenbuben4's palette) ----
    // Angles are measured on the smooth terrain surface in Minecraft blocks (45 deg = 1 up per 1 across).
    // From steep to flat: each block is used BELOW the given angle down to the next one.
    static final double[] PAINT_BELOW_DEG = {73.0, 65.0, 57.0, 46.0, 44.0, 42.0, 41.0};
    static final String[] PAINT_NAMES = {"polished_basalt", "basalt", "gray_concrete", "basalt", "polished_basalt", "stone", "snow_block"};
    static final double SNOW_BELOW_DEG = 38.0;                        // snow layers only on slopes flatter than this
                                                                      // (38-41 deg: painted with solid snow blocks)
    static final double PAINT_JITTER_DEG = 2.0;                       // light noise on the borders (no hard lines)
    static final double ALPINE_Y = 230.0;                             // above (tree line): no soil, painted + snow
    static final int ROCK_STEP = 3;                                   // a block step this high next to it -> cover the face
    static final int MIN_ROCK_DEPTH = 3, MAX_ROCK_DEPTH = 14;
    static final double HOLLOW_GAIN = 0.11;                           // extra blocks of snow per block of hollow depth
    static final double MAX_DEPTH = 3.5;                              // deepest drift (blocks)
    static final double FOREST_FACTOR = 0.45;                         // less snow on the ground under trees
    // ---- TUNE: tree wells (bare needle litter around the trunks, like under real spruces) ----
    static final double WELL_RADIUS = 1.8;                            // bare ring around a trunk (blocks)
    static final double WELL_JITTER = 0.9;                            // +- irregular edge of the ring
    static final double PODZOL_SHARE = 0.65, COARSE_SHARE = 0.25;     // floor in the well (rest stays grass)
    // ---- TUNE: scree (talus) at the foot of cliffs ----
    static final double SCREE_CLIFF_DEG = 50.0;                       // a slope this steep sheds rock ...
    static final double SCREE_RISE = 3.0;                             // ... if it is this much higher than here
    static final int SCREE_REACH = 3;                                 // how far the scree runs out (grid points = 6 blocks)
    static final double SCREE_FROM = 0.55;                            // strength + noise above this -> scree

    private static final int SEA_LEVEL = 63;                          // only the sea freezes, rivers stay open
    static final double RIVER_SNOW_FADE = 8.0;                        // snow back to normal this far from a river rim
    private static final double CONTINENTS_TO_HEIGHT = 1.0 / 0.00294; // see noise_settings "continents"
    private static final int GRID = 2, MARGIN = 8;                    // sample grid: every 2 blocks, chunk +-8
    private static final int N = (16 + 2 * MARGIN) / GRID + 1;        // 17 points per axis
    private static final int RING = 3;                                // hollow test radius in grid points (6 blocks)

    private SnowCover() {
    }

    /** Called instead of vanilla freeze_top_layer for one chunk; origin = the chunk's min corner. */
    public static boolean apply(WorldGenRegion level, BlockPos origin) {
        ServerLevel server = level.getLevel();
        DensityFunction continents = server.getChunkSource().randomState().router().continents();
        int x0 = origin.getX(), z0 = origin.getZ();

        // 1) smooth terrain height on a coarse grid around the chunk
        double[][] h = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                int x = x0 - MARGIN + i * GRID, z = z0 - MARGIN + j * GRID;
                h[i][j] = continents.compute(new DensityFunction.SinglePointContext(x, 0, z)) * CONTINENTS_TO_HEIGHT + 300.0;
            }
        }
        // 2) blurred height (snow surface base), slope and hollowness
        double[][] blur = new double[N][N], slope = new double[N][N], hollow = new double[N][N], scree = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double sum = 0, wsum = 0;
                for (int a = -2; a <= 2; a++) {
                    for (int b = -2; b <= 2; b++) {
                        double w = Math.exp(-(a * a + b * b) / 2.0);
                        sum += w * at(h, i + a, j + b);
                        wsum += w;
                    }
                }
                blur[i][j] = sum / wsum;
                double gx = (at(h, i + 1, j) - at(h, i - 1, j)) / (2.0 * GRID);
                double gz = (at(h, i, j + 1) - at(h, i, j - 1)) / (2.0 * GRID);
                slope[i][j] = Math.sqrt(gx * gx + gz * gz);
                double ring = 0;
                for (int k = 0; k < 8; k++) {
                    double ang = k * Math.PI / 4.0;
                    ring += at(h, i + (int) Math.round(Math.cos(ang) * RING), j + (int) Math.round(Math.sin(ang) * RING));
                }
                hollow[i][j] = ring / 8.0 - h[i][j];              // > 0: hollow / gully, < 0: ridge
            }
        }
        // 2b) scree: strong right below a higher cliff, fading out over SCREE_REACH grid points
        double cliffSlope = Math.tan(Math.toRadians(SCREE_CLIFF_DEG));
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double best = 0.0;
                for (int a = -SCREE_REACH; a <= SCREE_REACH; a++) {
                    for (int b = -SCREE_REACH; b <= SCREE_REACH; b++) {
                        double dist = Math.sqrt(a * a + b * b);
                        if (dist == 0.0 || dist > SCREE_REACH) continue;
                        int ii = Math.max(0, Math.min(N - 1, i + a)), jj = Math.max(0, Math.min(N - 1, j + b));
                        if (slope[ii][jj] < cliffSlope || h[ii][jj] - h[i][j] < SCREE_RISE) continue;
                        best = Math.max(best, 1.0 - (dist - 1.0) / SCREE_REACH);
                    }
                }
                scree[i][j] = best;
            }
        }

        // icebergs out on the deep sea (only where our terrain is: the floor height comes from its maths)
        FrozenTerrainMath math = NorwayTerrain.of(server.getChunkSource().randomState());
        java.util.List<Icebergs.Berg> bergs = math == null ? java.util.List.of()
                : Icebergs.near(level.getSeed(), x0, z0, x0 + 15, z0 + 15, math::surfaceY);
        // the ice spires of the Kristallspitzen biome (only near a patch: its snowy ring is wider than a cluster reaches)
        java.util.List<CrystalSpires.Cluster> spires = math == null || math.spireBiome(x0 + 8, z0 + 8) < 0.0
                ? java.util.List.of()
                : CrystalSpires.near(level.getSeed(), x0, z0, x0 + 15, z0 + 15, new CrystalSpires.Ground() {
                    @Override
                    public double y(double x, double z) {
                        return math.surfaceY(x, z);
                    }

                    @Override
                    public double core(double x, double z) {
                        return math.spireCore(x, z);
                    }
                });

        RiverNetwork.ChunkData rivers = RiverFeature.chunkData(server, new net.minecraft.world.level.ChunkPos(origin));
        java.util.List<net.icegiant.worldgen.camp.GnollCamps.Camp> camps =
                net.icegiant.worldgen.camp.GnollCamps.campsAt(level, new net.minecraft.world.level.ChunkPos(origin));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                if (!bergs.isEmpty() && iceberg(level, pos, bergs, x, z)) continue;       // (no floes, no snow on it)
                int ri = lx * 16 + lz;
                byte rk = rivers == null ? RiverNetwork.ChunkData.NONE : rivers.kind[ri];
                if (rk != RiverNetwork.ChunkData.NONE && RiverFeature.overTunnel(server, rivers, x, z, ri)) {
                    rk = RiverNetwork.ChunkData.NONE;                                 // over a river tunnel: normal snow
                }
                if (rk == RiverNetwork.ChunkData.CHANNEL || rk == RiverNetwork.ChunkData.LAKE) continue;   // open water
                if (!spires.isEmpty() && crystalSpires(level, pos, spires, x, z, math)) continue;   // (no snow under them)
                // banks: a thin layer (gravel and ice stay bare); next to a river/lake the snow fades to a thin
                // layer, so the bank stays level with the land
                double nearRiver = rk == RiverNetwork.ChunkData.RIM || rk == RiverNetwork.ChunkData.SHORE ? 0.0
                        : rk == RiverNetwork.ChunkData.NEAR ? Math.min(1.0, rivers.edge[ri] / RIVER_SNOW_FADE) : 1.0;
                double gi = (lx + MARGIN) / (double) GRID, gj = (lz + MARGIN) / (double) GRID;
                double surf = sample(blur, gi, gj), s = sample(slope, gi, gj), hol = sample(hollow, gi, gj);
                double sc = rk == RiverNetwork.ChunkData.NONE ? sample(scree, gi, gj) : 0.0;
                boolean inCamp = false;
                for (net.icegiant.worldgen.camp.GnollCamps.Camp c : camps) inCamp |= c.contains(x, z, 2);
                if (inCamp) {
                    // a Gnoll camp: one thin layer, no drifts against the huts; the trodden paths stay bare
                    BlockState topBlock = level.getBlockState(pos.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z));
                    if (topBlock.is(Blocks.DIRT_PATH) || topBlock.is(Blocks.CAMPFIRE)) continue;
                    nearRiver = 0.0;
                    sc = 0.0;
                }
                // (2026-10-24) the terrain here is sea floor: whatever ice stands on it is an iceberg, never land
                boolean sea = math != null && math.cachedSurfaceY(x, z) < SEA_LEVEL - 1;
                coverColumn(level, pos, x, z, surf, s, hol, nearRiver, sc, sea);
            }
        }
        return true;
    }

    private static final ThreadLocal<byte[]> BERG_COLUMN = ThreadLocal.withInitial(() -> new byte[Icebergs.COLUMN_SIZE]);

    /**
     * Builds the icebergs' blocks of this column (in the water and the air only); true if there were any. All icebergs
     * of the column together (Icebergs.column): where they grow into each other they make one block of ice.
     */
    private static boolean iceberg(WorldGenRegion level, BlockPos.MutableBlockPos pos, java.util.List<Icebergs.Berg> bergs,
                                   int x, int z) {
        byte[] col = BERG_COLUMN.get();
        if (!Icebergs.column(bergs, x, z, col)) return false;
        boolean any = false;
        for (int i = 0; i < Icebergs.COLUMN_SIZE; i++) {
            byte m = col[i];
            if (m == Icebergs.NONE) continue;
            BlockState st = level.getBlockState(pos.set(x, Icebergs.COLUMN_MIN_Y + i, z));
            if (!(st.isAir() || st.is(Blocks.WATER) || st.is(Blocks.KELP) || st.is(Blocks.KELP_PLANT)
                    || st.is(Blocks.SEAGRASS) || st.is(Blocks.TALL_SEAGRASS) || st.is(Blocks.ICE)
                    || st.is(Blocks.PACKED_ICE) || st.is(Blocks.SNOW))) continue;       // never into the sea floor
            level.setBlock(pos, switch (m) {
                case Icebergs.BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
                case Icebergs.ICE -> Blocks.ICE.defaultBlockState();
                case Icebergs.SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
                default -> Blocks.PACKED_ICE.defaultBlockState();
            }, 2);
            any = true;
        }
        return any;
    }

    private static final ThreadLocal<byte[]> SPIRE_COLUMN = ThreadLocal.withInitial(() -> new byte[CrystalSpires.COLUMN_SIZE]);

    /** Builds the Kristallspitzen ice spires of this column (into air, snow and plants only); true if there were any. */
    private static boolean crystalSpires(WorldGenRegion level, BlockPos.MutableBlockPos pos,
                                         java.util.List<CrystalSpires.Cluster> spires, int x, int z, FrozenTerrainMath math) {
        double ground = math.cachedSurfaceY(x, z);
        byte[] col = SPIRE_COLUMN.get();
        if (!CrystalSpires.column(spires, x, z, ground, col)) return false;
        int y0 = CrystalSpires.columnMinY(ground);
        boolean any = false;
        for (int i = 0; i < CrystalSpires.COLUMN_SIZE; i++) {
            byte m = col[i];
            if (m == CrystalSpires.NONE) continue;
            BlockState st = level.getBlockState(pos.set(x, y0 + i, z));
            if (!(st.isAir() || st.is(Blocks.SNOW) || (st.canBeReplaced() && st.getFluidState().isEmpty()))) continue;
            level.setBlock(pos, switch (m) {
                case CrystalSpires.BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
                case CrystalSpires.ICE -> Blocks.ICE.defaultBlockState();
                case CrystalSpires.SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
                case CrystalSpires.GLOW_ICE -> net.icegiant.registry.ModBlocks.GLOW_ICE.get().defaultBlockState();
                default -> Blocks.PACKED_ICE.defaultBlockState();
            }, 2);
            any = true;
        }
        return any;
    }

    static final int FLOAT_SCAN = 80;                 // ice this thick (at most) over water counts as floating

    private static boolean isIce(BlockState st) {
        return st.is(Blocks.PACKED_ICE) || st.is(Blocks.BLUE_ICE) || st.is(Blocks.ICE) || st.is(Blocks.SNOW_BLOCK);
    }

    /** True if the ground block at y is ice (or snow on ice) with sea water under it: an iceberg, not land. */
    private static boolean floatingIce(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z, int y) {
        if (level.getBlockState(pos.set(x, y, z)).is(Blocks.SNOW)) y--;                // (a snow layer on top)
        if (!isIce(level.getBlockState(pos.set(x, y, z)))) return false;
        for (int k = 1; k <= FLOAT_SCAN; k++) {
            BlockState st = level.getBlockState(pos.set(x, y - k, z));
            if (isIce(st)) continue;
            return st.is(Blocks.WATER) || st.getFluidState().is(Fluids.WATER);
        }
        return false;
    }

    private static void coverColumn(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z,
                                    double surf, double slope, double hollow, double nearRiver, double scree,
                                    boolean sea) {
        int topAll = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);        // includes leaves
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        pos.set(x, topAll - 1, z);
        BlockState top = level.getBlockState(pos);

        // the sea: hexagonal ice floes in clusters (IceFloes) instead of one closed ice sheet
        if (top.getFluidState().is(Fluids.WATER) && top.getBlock() == Blocks.WATER) {
            if (pos.getY() == SEA_LEVEL - 1) {
                int floe = IceFloes.at(level.getSeed(), x, z);
                if (floe == IceFloes.EDGE) {
                    level.setBlock(pos, Blocks.ICE.defaultBlockState(), 2);
                } else if (floe == IceFloes.INSIDE) {
                    // packed ice, in patches snow-covered (snow layers can't lie on ice, so snow blocks)
                    level.setBlock(pos, (jitter(x, z) > 0.1 ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE).defaultBlockState(), 2);
                    BlockPos below = pos.below();
                    if (level.getBlockState(below).is(Blocks.WATER)) level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), 2);
                }
            }
            return;
        }
        // floating ice (an iceberg that is not one of ours - vanilla's, or one that reached in from a chunk built by an
        // older version): it is no land. Until 2026-10-22 its steep sides were painted like a cliff - the top of the
        // berg became stone, basalt and concrete with snow on it, "mountain peaks of stone" out on the sea.
        if (floatingIce(level, pos, x, z, ground - 1)) return;
        // ... and ice over sea floor is no land either, even when it stands on the floor of a shallow shelf or has an
        // air gap inside (floatingIce then misses it): until 2026-10-24 such bergs got a stone cap with snow on it
        if (sea && isIce(level.getBlockState(pos.set(x, ground - 1, z)))) return;

        // ---- rock instead of soil on cliffs and high up ----
        int lowest = ground;
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
            lowest = Math.min(lowest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz));
        }
        int step = ground - lowest;                                        // exposed side face height
        double deg = Math.toDegrees(Math.atan(slope)) + PAINT_JITTER_DEG * jitter(x, z);
        boolean steep = deg >= SNOW_BELOW_DEG;
        int faceDepth = Math.max(MIN_ROCK_DEPTH, Math.min(MAX_ROCK_DEPTH, step + 2));
        if (steep) {
            paint(level, pos, x, z, ground, faceDepth, paintFor(deg));            // slope painting
        } else if (surf > ALPINE_Y) {
            paint(level, pos, x, z, ground, faceDepth, null);                    // above the tree line: snow on rock
        } else if (step >= ROCK_STEP) {
            paint(level, pos, x, z, ground, faceDepth, Blocks.STONE.defaultBlockState()); // no dirt walls
        } else {
            hideOres(level, pos, x, z, ground, faceDepth);                         // no ores showing on the surface
        }
        if (!steep && scree > 0.0 && scree + 0.35 * jitter(x + 1013, z - 977) > SCREE_FROM) {
            scree(level, pos, x, z, ground);                                       // rock fallen off the cliff above
        }

        double alt = smooth(SNOW_START_Y, SNOW_FULL_Y, surf);
        double depth = LOW_DEPTH + (HIGH_DEPTH - LOW_DEPTH) * alt;
        depth += HOLLOW_GAIN * clamp(hollow, -8.0, 14.0) * (0.5 + 0.5 * alt);
        if (steep) depth = 0.0;                                            // painted rock stays bare
        else if (surf > ALPINE_Y) depth = Math.max(depth, 0.5);           // flatter than 41 deg up high: always snow
        depth = clamp(depth, 0.0, MAX_DEPTH);

        boolean forest = topAll > ground + 1;                              // canopy above the ground
        if (forest) {
            // a light cover on the canopy (like vanilla), less snow on the forest floor
            pos.set(x, topAll, z);
            if (top.is(BlockTags.LEAVES) && level.getBlockState(pos).isAir()) {
                int layers = Math.max(1, Math.min(3, (int) Math.round(depth * 8.0 * 0.5)));
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
            }
            depth *= FOREST_FACTOR;
            // tree wells: right around the trunks the crown keeps the snow off - needle litter and ferns
            if (nearRiver >= 1.0 && !steep && surf <= ALPINE_Y && treeWell(level, pos, x, z, ground)) {
                bareFloor(level, pos, x, z, ground);
                return;
            }
        }

        if (depth < 1.0 / 16.0) return;                                    // cliff: bare rock
        // the snow surface follows the blurred terrain -> block steps become ramps of layers
        // (+0.5: the block surface sits on average half a block above the smooth height)
        double snowTop = surf + 0.5 + depth;
        // ground = first free block. Next to a river (nearRiver < 1) the drifts fade out to one thin
        // layer, so the river's rim stays level with the land (and cut banks are not filled back up)
        double fill = Math.max(snowTop - ground, depth);
        if (nearRiver < 1.0) {
            double thin = Math.min(fill, 1.0 / 8.0);
            fill = thin + (fill - thin) * nearRiver;
        }
        if (fill < 1.0 / 16.0) return;                                     // bare (cliff, ridge)

        pos.set(x, ground - 1, z);
        BlockState below = level.getBlockState(pos);
        if (below.is(Blocks.GRAVEL) || below.is(Blocks.PACKED_ICE) || below.is(Blocks.ICE)) return;   // gravel/ice stay bare
        if (!below.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP) && !below.is(BlockTags.LEAVES)) return;
        if (below.hasProperty(SnowyDirtBlock.SNOWY)) level.setBlock(pos, below.setValue(SnowyDirtBlock.SNOWY, true), 2);

        int full = (int) Math.floor(fill);
        int layers = (int) Math.round((fill - full) * 8.0);
        if (layers == 8) { full++; layers = 0; }
        for (int k = 0; k <= full; k++) {
            pos.set(x, ground + k, z);
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.canBeReplaced()) return;            // trunk, plant stem, ...
            if (k < full) {
                level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
            } else if (layers > 0) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
            } else if (!here.isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);   // grass/fern buried by snow
            }
        }
    }

    /** True if a trunk stands close enough that this spot lies in its snow-free well. */
    private static boolean treeWell(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z, int ground) {
        if (level.getBlockState(pos.set(x, ground - 1, z)).is(BlockTags.LOGS)) return false;   // the trunk itself
        double reach = WELL_RADIUS + WELL_JITTER * jitter(x * 2 + 5101, z * 2 - 3307);
        int r = (int) Math.ceil(reach);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > reach * reach) continue;
                BlockState st = level.getBlockState(pos.set(x + dx, ground, z + dz));
                if (st.is(BlockTags.LOGS) && level.getBlockState(pos.set(x + dx, ground + 2, z + dz)).is(BlockTags.LOGS)) {
                    return true;                                          // an upright trunk (not a fallen log)
                }
            }
        }
        return false;
    }

    /** The floor of a tree well: podzol (needle litter) and coarse dirt instead of snowy grass. */
    private static void bareFloor(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z, int ground) {
        pos.set(x, ground - 1, z);
        BlockState below = level.getBlockState(pos);
        if (!(below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.PODZOL))) return;
        double v = h01(x * 7 + 13, z * 11 - 5);
        BlockState floor = v < PODZOL_SHARE ? Blocks.PODZOL.defaultBlockState()
                : v < PODZOL_SHARE + COARSE_SHARE ? Blocks.COARSE_DIRT.defaultBlockState()
                : Blocks.GRASS_BLOCK.defaultBlockState();
        level.setBlock(pos, floor, 2);
        pos.set(x, ground, z);
        if (level.getBlockState(pos).is(Blocks.SNOW)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    /** Scree: the top of the ground becomes loose rock - gravel, andesite, cobbles and stone. */
    private static void scree(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z, int ground) {
        int depth = 1 + (h01(x * 3 - 7, z * 5 + 1) < 0.5 ? 1 : 0);
        for (int k = 1; k <= depth; k++) {
            pos.set(x, ground - k, z);
            BlockState st = level.getBlockState(pos);
            if (!(st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.STONE) || st.is(Blocks.ANDESITE))) continue;
            double v = h01(x * 13 + k * 101, z * 17 - k * 59) * 0.6 + h01(x >> 1, z >> 1) * 0.4;
            BlockState rock = v < 0.40 ? Blocks.GRAVEL.defaultBlockState()
                    : v < 0.65 ? Blocks.ANDESITE.defaultBlockState()
                    : v < 0.85 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            level.setBlock(pos, rock, 2);
        }
    }

    /** Ores in the top/exposed part of a column become stone (deeper inside they stay). */
    private static void hideOres(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int depth) {
        for (int k = 1; k <= depth; k++) {
            pos.set(x, ground - k, z);
            BlockState st = level.getBlockState(pos);
            if (st.is(net.minecraftforge.common.Tags.Blocks.ORES)) {
                level.setBlock(pos, pos.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    /** Block for a painted slope angle. */
    private static BlockState paintFor(double deg) {
        if (deg >= PAINT_BELOW_DEG[0]) return null;                       // steeper than 73: natural rock mix
        BlockState out = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < PAINT_BELOW_DEG.length; i++) {
            double upper = PAINT_BELOW_DEG[i];
            double lower = i + 1 < PAINT_BELOW_DEG.length ? PAINT_BELOW_DEG[i + 1] : SNOW_BELOW_DEG;
            if (deg < upper && deg >= lower) { out = block(PAINT_NAMES[i]); break; }
        }
        return out;
    }

    private static BlockState block(String name) {
        return switch (name) {
            case "polished_basalt" -> Blocks.POLISHED_BASALT.defaultBlockState();
            case "basalt" -> Blocks.BASALT.defaultBlockState();
            case "gray_concrete" -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case "snow_block" -> Blocks.SNOW_BLOCK.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    /** Smooth value noise -1..1 on ~4-block cells (for the slope-border jitter). */
    private static double jitter(int x, int z) {
        double fx = x / 4.0, fz = z / 4.0;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz);
        double a = h01(ix, iz), b = h01(ix + 1, iz), c = h01(ix, iz + 1), d = h01(ix + 1, iz + 1);
        return ((a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz) * 2.0 - 1.0;
    }

    private static double h01(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h >>> 20) & 0xffff) / 65535.0;
    }

    /**
     * Replaces soil/snow blocks in the top of the column (incl. its exposed side face).
     * material null = natural rock mix (stone/andesite/tuff).
     */
    private static void paint(WorldGenRegion level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int depth,
                              BlockState material) {
        for (int k = 1; k <= depth; k++) {
            int y = ground - k;
            pos.set(x, y, z);
            BlockState st = level.getBlockState(pos);
            if (!(st.is(BlockTags.DIRT) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.GRAVEL) || st.is(Blocks.STONE)
                    || st.is(Blocks.ANDESITE) || st.is(Blocks.PACKED_ICE) || st.is(Blocks.POWDER_SNOW)
                    || st.is(net.minecraftforge.common.Tags.Blocks.ORES))) continue;   // ores on the surface are painted over too
            level.setBlock(pos, material != null ? material : rockAt(x, y, z), 2);
        }
    }

    /** Grey Norwegian rock: mostly stone, patches of andesite and some cobbled/tuff bits. */
    private static BlockState rockAt(int x, int y, int z) {
        long h = (x >> 2) * 73856093L ^ (y >> 2) * 19349663L ^ (z >> 2) * 83492791L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        int r = (int) ((h >>> 16) & 1023);
        if (r < 260) return Blocks.ANDESITE.defaultBlockState();
        if (r < 300) return Blocks.TUFF.defaultBlockState();
        if (r < 320) return Blocks.COBBLESTONE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    private static double at(double[][] g, int i, int j) {
        return g[Math.max(0, Math.min(N - 1, i))][Math.max(0, Math.min(N - 1, j))];
    }

    private static double sample(double[][] g, double i, double j) {
        int i0 = (int) Math.floor(i), j0 = (int) Math.floor(j);
        double fi = i - i0, fj = j - j0;
        double a = at(g, i0, j0) * (1 - fi) + at(g, i0 + 1, j0) * fi;
        double b = at(g, i0, j0 + 1) * (1 - fi) + at(g, i0 + 1, j0 + 1) * fi;
        return a * (1 - fj) + b * fj;
    }

    private static double smooth(double e0, double e1, double v) {
        double t = clamp((v - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
